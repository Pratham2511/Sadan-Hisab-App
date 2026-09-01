package com.pansare.sadan.data

import androidx.room.withTransaction
import com.pansare.sadan.domain.LedgerEngine
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.domain.PaymentAllocator
import com.pansare.sadan.domain.PlannedAllocation
import kotlinx.coroutines.flow.Flow

/**
 * Central repository that coordinates all data operations.
 * All payment operations are transactional — partial updates never persist.
 */
class RentRepository(private val db: AppDatabase) {

    private val rooms = db.roomDao()
    private val tenants = db.tenantDao()
    private val payments = db.paymentDao()
    private val ledger = db.ledgerDao()
    private val allocations = db.allocationDao()
    private val rents = db.rentChangeDao()
    private val settings = db.settingsDao()
    private val validation = db.validationDao()

    // ──────────────────────────────────────────────
    // Observe (Flow-based reactive queries)
    // ──────────────────────────────────────────────

    fun observeTenants(): Flow<List<TenantRoomRow>> = tenants.observeRows()
    fun observeRooms(): Flow<List<RoomEntity>> = rooms.observeAll()
    fun observePayments(): Flow<List<PaymentWithTenantRow>> = payments.observeHistory()
    fun observePaymentsForTenant(tenantId: Long): Flow<List<PaymentWithTenantRow>> =
        payments.observeForTenant(tenantId)
    fun observeLedger(tenantId: Long): Flow<List<MonthlyLedgerEntity>> =
        ledger.observeForTenant(tenantId)
    fun observeValidationIssues(): Flow<List<ImportValidationIssueEntity>> =
        validation.observeAll()
    fun observeSettings(): Flow<List<SettingEntity>> = settings.observeAll()
    fun searchTenants(query: String): Flow<List<TenantRoomRow>> = tenants.search(query)

    // ──────────────────────────────────────────────
    // Seed
    // ──────────────────────────────────────────────

    suspend fun seedIfEmpty() = db.withTransaction {
        if (tenants.count() == 0) SeedData.insert(db)
    }

    // ──────────────────────────────────────────────
    // Room CRUD
    // ──────────────────────────────────────────────

    suspend fun addRoom(room: RoomEntity): Long = rooms.insert(room)
    suspend fun findRoom(id: Long): RoomEntity? = rooms.find(id)

    // ──────────────────────────────────────────────
    // Tenant CRUD
    // ──────────────────────────────────────────────

    suspend fun addTenant(tenant: TenantEntity): Long = db.withTransaction {
        require(tenant.tenantName.isNotBlank()) { "Tenant name is required." }
        require(tenant.monthlyRent >= 0) { "Rent cannot be negative." }
        requireNotNull(rooms.find(tenant.roomId)) { "Room does not exist." }
        tenants.insert(tenant)
    }

    suspend fun findTenant(id: Long): TenantEntity? = tenants.find(id)

    suspend fun updateTenant(tenant: TenantEntity) = db.withTransaction {
        require(tenant.tenantName.isNotBlank()) { "Tenant name is required." }
        require(tenant.monthlyRent >= 0) { "Rent cannot be negative." }
        tenants.update(tenant.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTenantRent(tenantId: Long, newRent: Long, effectiveFrom: String, reason: String = "") =
        db.withTransaction {
            val tenant = requireNotNull(tenants.find(tenantId)) { "Tenant does not exist." }
            tenants.update(tenant.copy(monthlyRent = newRent, updatedAt = System.currentTimeMillis()))
            rents.insert(
                RentChangeEntity(
                    tenantId = tenantId,
                    effectiveFromMonth = effectiveFrom,
                    monthlyRent = newRent,
                    note = reason
                )
            )
        }

    // ──────────────────────────────────────────────
    // Ledger management
    // ──────────────────────────────────────────────

    suspend fun ensureLedgerThrough(tenantId: Long, through: String) {
        val tenant = requireNotNull(tenants.find(tenantId)) { "Tenant does not exist." }
        val start = tenant.occupancyStartMonth
            ?: throw IllegalArgumentException("Set a verified occupancy start month before recording payment.")
        if (start > through) return

        val rows = MonthKey.betweenInclusive(start, through).mapNotNull { month ->
            if (ledger.find(tenantId, month) == null) {
                val rent = rents.applicable(tenantId, month)?.monthlyRent ?: tenant.monthlyRent
                MonthlyLedgerEntity(
                    tenantId = tenantId,
                    month = month,
                    applicableRent = rent,
                    amountDue = rent,
                    balance = rent
                )
            } else null
        }
        if (rows.isNotEmpty()) ledger.insertAll(rows)
    }

    // ──────────────────────────────────────────────
    // Payment CRUD
    // ──────────────────────────────────────────────

    suspend fun recordPayment(draft: PaymentEntity): Long = db.withTransaction {
        require(draft.receiptNumber.isNotBlank()) { "Receipt number is required." }
        require(draft.amountPaid > 0) { "Payment amount cannot be zero." }
        require(payments.receiptExists(draft.receiptNumber) == 0) { "Receipt number already exists." }

        val range = MonthKey.betweenInclusive(draft.paidFromMonth, draft.paidToMonth)
        ensureLedgerThrough(draft.tenantId, draft.paidToMonth)

        val selected = ledger.range(draft.tenantId, draft.paidFromMonth, draft.paidToMonth)
        require(selected.size == range.size) { "The selected period is outside verified occupancy." }

        val planned = PaymentAllocator.plan(selected, draft.amountPaid)
        val id = payments.insert(
            draft.copy(numberOfMonths = range.size, updatedAt = System.currentTimeMillis())
        )

        allocations.insertAll(
            planned.map {
                PaymentAllocationEntity(
                    paymentId = id,
                    ledgerMonthId = it.ledgerId,
                    allocatedAmount = it.amount
                )
            }
        )

        recalculate(selected)
        id
    }

    suspend fun editPayment(updated: PaymentEntity) = db.withTransaction {
        require(updated.receiptNumber.isNotBlank()) { "Receipt number is required." }
        require(updated.amountPaid > 0) { "Payment amount cannot be zero." }
        require(payments.receiptExists(updated.receiptNumber, updated.id) == 0) {
            "Receipt number already exists."
        }

        val old = requireNotNull(payments.find(updated.id)) { "Payment no longer exists." }
        val previous = allocations.byPayment(old.id)
        val previousRows = previous.mapNotNull { allocation -> ledgerById(allocation.ledgerMonthId) }

        // Reverse old allocations
        allocations.deleteByPayment(old.id)

        // Apply new allocations
        ensureLedgerThrough(updated.tenantId, updated.paidToMonth)
        val range = MonthKey.betweenInclusive(updated.paidFromMonth, updated.paidToMonth)
        val selected = ledger.range(updated.tenantId, updated.paidFromMonth, updated.paidToMonth)
        require(selected.size == range.size) { "The selected period is outside verified occupancy." }

        // Recalculate previous rows first (to reset their balances before re-planning)
        recalculate(previousRows)

        // Now re-fetch selected rows (they may have changed if they overlap with previousRows)
        val refreshedSelected = ledger.range(updated.tenantId, updated.paidFromMonth, updated.paidToMonth)

        val planned = PaymentAllocator.plan(refreshedSelected, updated.amountPaid)
        payments.update(
            updated.copy(numberOfMonths = range.size, updatedAt = System.currentTimeMillis())
        )
        allocations.insertAll(
            planned.map {
                PaymentAllocationEntity(
                    paymentId = updated.id,
                    ledgerMonthId = it.ledgerId,
                    allocatedAmount = it.amount
                )
            }
        )

        recalculate((previousRows + refreshedSelected).distinctBy { it.id })
    }

    suspend fun deletePayment(paymentId: Long) = db.withTransaction {
        val payment = requireNotNull(payments.find(paymentId)) { "Payment no longer exists." }
        val rows = allocations.byPayment(paymentId).mapNotNull { ledgerById(it.ledgerMonthId) }
        allocations.deleteByPayment(paymentId)
        payments.delete(payment)
        recalculate(rows)
    }

    suspend fun findPayment(id: Long): PaymentEntity? = payments.find(id)

    // ──────────────────────────────────────────────
    // Outstanding & Dashboard
    // ──────────────────────────────────────────────

    suspend fun outstanding(tenantId: Long, asOf: String = MonthKey.current()): Long =
        ledger.outstanding(tenantId, MonthKey.format(MonthKey.parse(asOf)))

    suspend fun unpaidCount(tenantId: Long, asOf: String = MonthKey.current()): Int =
        ledger.unpaidCount(tenantId, asOf)

    suspend fun partiallyPaidCount(tenantId: Long, asOf: String = MonthKey.current()): Int =
        ledger.partiallyPaidCount(tenantId, asOf)

    suspend fun outstandingSince(tenantId: Long, asOf: String = MonthKey.current()): String? =
        ledger.outstandingSince(tenantId, asOf)

    suspend fun lastPaidUpTo(tenantId: Long, asOf: String = MonthKey.current()): String? =
        ledger.lastPaidUpTo(tenantId, asOf)

    suspend fun unpaidOrPartialCount(tenantId: Long, asOf: String = MonthKey.current()): Int =
        ledger.unpaidOrPartialCount(tenantId, asOf)

    // ──────────────────────────────────────────────
    // Receipt number generation
    // ──────────────────────────────────────────────

    suspend fun nextReceiptNumber(): String {
        val prefix = settings.get("receipt_prefix") ?: "PS"
        val year = java.time.Year.now().value
        val prefixStr = "$prefix-$year-"
        val maxSeq = payments.maxReceiptSequence(prefixStr) ?: 0
        return "$prefixStr${String.format("%04d", maxSeq + 1)}"
    }

    // ──────────────────────────────────────────────
    // Reports
    // ──────────────────────────────────────────────

    data class MonthlyCollectionReport(
        val month: String,
        val expectedRent: Long,
        val collected: Long,
        val outstanding: Long,
        val paidCount: Int,
        val partiallyPaidCount: Int,
        val unpaidCount: Int
    )

    suspend fun monthlyCollectionReport(month: String): MonthlyCollectionReport {
        val expected = ledger.expectedRentForMonth(month)
        val collected = ledger.collectedForMonth(month)
        return MonthlyCollectionReport(
            month = month,
            expectedRent = expected,
            collected = collected,
            outstanding = expected - collected,
            paidCount = ledger.countByStatusForMonth(month, LedgerStatus.PAID),
            partiallyPaidCount = ledger.countByStatusForMonth(month, LedgerStatus.PARTIALLY_PAID),
            unpaidCount = ledger.countByStatusForMonth(month, LedgerStatus.UNPAID)
        )
    }

    data class YearlyReport(
        val year: Int,
        val totalExpected: Long,
        val totalCollected: Long,
        val outstanding: Long,
        val collectionPercentage: Double
    )

    suspend fun yearlyReport(year: Int): YearlyReport {
        val from = "$year-01"
        val to = "$year-12"
        val expected = ledger.expectedRentForRange(from, to)
        val collected = ledger.collectedForRange(from, to)
        return YearlyReport(
            year = year,
            totalExpected = expected,
            totalCollected = collected,
            outstanding = expected - collected,
            collectionPercentage = if (expected > 0) (collected.toDouble() / expected * 100) else 0.0
        )
    }

    // ──────────────────────────────────────────────
    // Settings
    // ──────────────────────────────────────────────

    suspend fun getSetting(key: String): String? = settings.get(key)

    suspend fun setSetting(key: String, value: String) =
        settings.upsert(SettingEntity(key, value))

    // ──────────────────────────────────────────────
    // Backup data access
    // ──────────────────────────────────────────────

    suspend fun getAllRooms(): List<RoomEntity> = rooms.getAll()
    suspend fun getAllTenants(): List<TenantEntity> = tenants.getAll()
    suspend fun getAllPayments(): List<PaymentEntity> = payments.getAll()
    suspend fun getAllLedger(): List<MonthlyLedgerEntity> = ledger.getAll()
    suspend fun getAllAllocations(): List<PaymentAllocationEntity> = allocations.getAll()
    suspend fun getAllRentChanges(): List<RentChangeEntity> = rents.getAll()
    suspend fun getAllSettings(): List<SettingEntity> = settings.getAll()
    suspend fun getAllValidationIssues(): List<ImportValidationIssueEntity> = validation.getAll()

    fun getDatabase(): AppDatabase = db

    // ──────────────────────────────────────────────
    // Rent changes
    // ──────────────────────────────────────────────

    suspend fun getRentChanges(tenantId: Long): List<RentChangeEntity> = rents.forTenant(tenantId)

    // ──────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────

    private suspend fun ledgerById(id: Long): MonthlyLedgerEntity? = ledger.findById(id)

    private suspend fun recalculate(rows: List<MonthlyLedgerEntity>) {
        if (rows.isEmpty()) return
        ledger.updateAll(
            rows.map { row ->
                val paid = allocations.totalForLedger(row.id)
                val balance = row.amountDue - paid
                require(balance >= 0) { "Payment exceeds the remaining balance." }
                row.copy(
                    totalPaid = paid,
                    balance = balance,
                    status = when {
                        paid == 0L -> LedgerStatus.UNPAID
                        balance == 0L -> LedgerStatus.PAID
                        else -> LedgerStatus.PARTIALLY_PAID
                    },
                    lastPaymentDate = allocations.lastPaymentDate(row.id),
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
    }
}
