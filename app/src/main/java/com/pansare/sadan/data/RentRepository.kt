package com.pansare.sadan.data

import androidx.room.withTransaction
import com.pansare.sadan.domain.AccountingException
import com.pansare.sadan.domain.Allocation
import com.pansare.sadan.domain.AllocationPlan
import com.pansare.sadan.domain.DefaulterSummary
import com.pansare.sadan.domain.ImportEngine
import com.pansare.sadan.domain.ImportResult
import com.pansare.sadan.domain.IssueKind
import com.pansare.sadan.domain.LedgerEngine
import com.pansare.sadan.domain.LedgerMonth
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.domain.RawPaymentRow
import com.pansare.sadan.domain.RentCertainty
import com.pansare.sadan.domain.RentPeriod
import com.pansare.sadan.domain.RentResolver
import kotlinx.coroutines.flow.Flow

/**
 * Coordinates every data operation and is the only place that writes accounting data.
 *
 * Rules held here:
 *  - All multi-step accounting work runs inside a single Room transaction.
 *  - The ledger projection (totalPaid / balance / status) is recomputed from allocations
 *    after every change, so stale balances are structurally impossible.
 *  - Invariants are verified before a transaction is allowed to commit.
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
    // Observation
    // ──────────────────────────────────────────────

    fun observeRoomsWithTenants(): Flow<List<RoomWithTenantRow>> = rooms.observeRoomsWithTenants()
    suspend fun roomsWithTenants(): List<RoomWithTenantRow> = rooms.roomsWithTenants()
    fun observeRooms(): Flow<List<RoomEntity>> = rooms.observeAll()
    fun observePayments(): Flow<List<PaymentWithTenantRow>> = payments.observeHistory()
    fun observePaymentsForTenant(id: Long): Flow<List<PaymentWithTenantRow>> = payments.observeForTenant(id)
    fun observeLedger(tenantId: Long): Flow<List<MonthlyLedgerEntity>> = ledger.observeForTenant(tenantId)
    fun observeAllocationDetails(tenantId: Long): Flow<List<AllocationDetailRow>> =
        allocations.observeDetailsForTenant(tenantId)
    fun observeTenant(id: Long): Flow<TenantEntity?> = tenants.observe(id)
    fun observeRentChanges(tenantId: Long): Flow<List<RentChangeEntity>> = rents.observeForTenant(tenantId)
    fun observeValidationIssues(): Flow<List<ImportValidationIssueEntity>> = validation.observeAll()
    fun observeOpenIssueCount(): Flow<Int> = validation.observeOpenCount()
    fun observeSettings(): Flow<List<SettingEntity>> = settings.observeAll()

    // ──────────────────────────────────────────────
    // First launch
    // ──────────────────────────────────────────────

    /**
     * Creates the 48-room inventory and nothing else.
     * No tenants, no payments, no ledger rows, no sample data of any kind.
     */
    suspend fun initialiseIfEmpty() = db.withTransaction {
        if (rooms.count() == 0) {
            rooms.insertAll(RoomInventory.buildRooms())
        }
    }

    suspend fun roomCount(): Int = rooms.count()
    suspend fun tenantCount(): Int = tenants.activeCount()
    suspend fun paymentCount(): Int = payments.count()

    // ──────────────────────────────────────────────
    // Rooms & tenants
    // ──────────────────────────────────────────────

    suspend fun findRoom(id: Long): RoomEntity? = rooms.find(id)
    suspend fun findRoomByDisplay(display: String): RoomEntity? = rooms.findByDisplay(display)
    suspend fun findTenant(id: Long): TenantEntity? = tenants.find(id)
    suspend fun allRooms(): List<RoomEntity> = rooms.getAll()

    /**
     * Creates a tenancy and builds its ledger from the occupancy start month to the
     * current month, charging each month the rent in force for that month.
     */
    suspend fun addTenant(
        roomId: Long,
        name: String,
        mobile: String,
        monthlyRent: Long,
        occupancyStartMonth: String,
        remarks: String = ""
    ): Long = db.withTransaction {
        val cleanName = name.trim()
        if (cleanName.isBlank()) throw AccountingException("Tenant name is required.")
        if (monthlyRent < 0) throw AccountingException("Monthly rent cannot be negative.")
        if (!MonthKey.isValid(occupancyStartMonth)) {
            throw AccountingException("Occupancy start month must be a valid month.")
        }
        if (occupancyStartMonth > MonthKey.current()) {
            throw AccountingException("Occupancy start month cannot be in the future.")
        }
        val cleanMobile = mobile.trim()
        if (cleanMobile.isNotBlank() && !cleanMobile.matches(Regex("^[0-9+][0-9 -]{6,19}$"))) {
            throw AccountingException("Enter a valid mobile number, or leave it blank.")
        }
        rooms.find(roomId) ?: throw AccountingException("That room does not exist.")
        tenants.findActiveByRoom(roomId)?.let {
            throw AccountingException("Room already has an active tenant (${it.tenantName}).")
        }

        val tenantId = tenants.insert(
            TenantEntity(
                roomId = roomId,
                tenantName = cleanName,
                mobileNumber = cleanMobile,
                monthlyRent = monthlyRent,
                occupancyStartMonth = occupancyStartMonth,
                remarks = remarks.trim()
            )
        )

        // The opening rent rate. Later changes are added as further RentChange rows.
        rents.insert(
            RentChangeEntity(
                tenantId = tenantId,
                effectiveFromMonth = occupancyStartMonth,
                monthlyRent = monthlyRent,
                note = "Opening rent"
            )
        )

        buildLedgerThrough(tenantId, MonthKey.current())
        tenantId
    }

    suspend fun updateTenant(tenant: TenantEntity) = db.withTransaction {
        if (tenant.tenantName.isBlank()) throw AccountingException("Tenant name is required.")
        if (tenant.monthlyRent < 0) throw AccountingException("Monthly rent cannot be negative.")
        if (!MonthKey.isValid(tenant.occupancyStartMonth)) {
            throw AccountingException("Occupancy start month must be a valid month.")
        }
        tenants.update(tenant.copy(updatedAt = System.currentTimeMillis()))
        // Rent history may have shifted the applicable rate; re-resolve unpaid months.
        refreshLedgerRents(tenant.id)
        recalculateTenant(tenant.id)
    }

    /**
     * Records a rent change from a given month and re-resolves affected ledger months.
     * Months that are already settled keep their historical rent — we never rewrite the past.
     */
    suspend fun changeRent(
        tenantId: Long,
        newRent: Long,
        effectiveFrom: String,
        note: String = ""
    ) = db.withTransaction {
        if (newRent < 0) throw AccountingException("Rent cannot be negative.")
        if (!MonthKey.isValid(effectiveFrom)) throw AccountingException("Invalid effective month.")
        val tenant = tenants.find(tenantId) ?: throw AccountingException("Tenant not found.")

        rents.insert(
            RentChangeEntity(
                tenantId = tenantId,
                effectiveFromMonth = effectiveFrom,
                monthlyRent = newRent,
                note = note
            )
        )
        if (effectiveFrom <= MonthKey.current()) {
            tenants.update(tenant.copy(monthlyRent = newRent, updatedAt = System.currentTimeMillis()))
        }
        refreshLedgerRents(tenantId)
        recalculateTenant(tenantId)
    }

    /** Ends a tenancy. The room becomes vacant but keeps existing; the ledger is preserved. */
    suspend fun moveOutTenant(tenantId: Long, endMonth: String) = db.withTransaction {
        val tenant = tenants.find(tenantId) ?: throw AccountingException("Tenant not found.")
        if (!MonthKey.isValid(endMonth)) throw AccountingException("Invalid end month.")
        if (endMonth < tenant.occupancyStartMonth) {
            throw AccountingException("End month cannot precede the occupancy start month.")
        }
        tenants.update(
            tenant.copy(
                status = TenantStatus.MOVED_OUT,
                occupancyEndMonth = endMonth,
                updatedAt = System.currentTimeMillis()
            )
        )
        // Drop future months that were never paid; keep everything with money against it.
        ledger.trimUnpaidAfter(tenantId, endMonth)
    }

    // ──────────────────────────────────────────────
    // Ledger construction
    // ──────────────────────────────────────────────

    /**
     * Ensures a ledger row exists for every month from occupancy start to [through],
     * each charged the rent applicable to that month. Months before the earliest known
     * rate are created as UNRESOLVED rather than being given an invented rent.
     */
    suspend fun buildLedgerThrough(tenantId: Long, through: String) {
        val tenant = tenants.find(tenantId) ?: throw AccountingException("Tenant not found.")
        val end = minOf(through, tenant.occupancyEndMonth ?: through)
        val start = tenant.occupancyStartMonth
        if (start > end) return

        val periods = rents.forTenant(tenantId).map { RentPeriod(it.effectiveFromMonth, it.monthlyRent) }
        val existing = ledger.forTenant(tenantId).map { it.month }.toSet()

        val newRows = MonthKey.betweenInclusive(start, end)
            .filter { it !in existing }
            .map { month ->
                val resolved = RentResolver.resolve(periods, month)
                MonthlyLedgerEntity(
                    tenantId = tenantId,
                    month = month,
                    rentDue = resolved.amount,
                    certainty = resolved.certainty,
                    balance = resolved.amount,
                    status = if (resolved.amount == 0L) LedgerStatus.PAID else LedgerStatus.UNPAID,
                    notes = if (resolved.certainty == RentCertainty.UNRESOLVED) {
                        "Rent for this month is unknown — no rate was assumed. Add a rent record to resolve."
                    } else ""
                )
            }

        if (newRows.isNotEmpty()) ledger.insertAll(newRows)

        newRows.filter { it.certainty == RentCertainty.UNRESOLVED }.forEach { row ->
            validation.insert(
                ImportValidationIssueEntity(
                    tenantId = tenantId,
                    reference = row.month,
                    kind = IssueKind.UNKNOWN_HISTORICAL_RENT.name,
                    message = "No rent rate is known for ${MonthKey.displayName(row.month)}. " +
                        "This month is excluded from firm totals until a rate is supplied.",
                    sourceValue = ""
                )
            )
        }
    }

    /** Re-resolves rentDue for months that carry no payment yet, after a rent history edit. */
    private suspend fun refreshLedgerRents(tenantId: Long) {
        val periods = rents.forTenant(tenantId).map { RentPeriod(it.effectiveFromMonth, it.monthlyRent) }
        if (periods.isEmpty()) return
        val rows = ledger.forTenant(tenantId)
        val paidIds = allocations.byTenant(tenantId).map { it.ledgerMonthId }.toSet()

        val updated = rows.filter { it.id !in paidIds }.mapNotNull { row ->
            val resolved = RentResolver.resolve(periods, row.month)
            if (resolved.amount != row.rentDue || resolved.certainty != row.certainty) {
                row.copy(rentDue = resolved.amount, certainty = resolved.certainty)
            } else null
        }
        if (updated.isNotEmpty()) ledger.updateAll(updated)
    }

    // ──────────────────────────────────────────────
    // Payments
    // ──────────────────────────────────────────────

    /**
     * Builds an allocation preview without writing anything, so the user can see exactly
     * where the money will go before committing.
     */
    suspend fun previewPayment(
        tenantId: Long,
        from: String,
        to: String,
        amount: Long,
        excludePaymentId: Long = 0
    ): AllocationPlan {
        validatePeriod(from, to)
        val months = ledgerMonthsFor(tenantId, from, to)
        if (months.isEmpty()) {
            throw AccountingException("The selected period is outside this tenant's occupancy.")
        }
        val allocs = allocationsFor(tenantId)
        return if (excludePaymentId != 0L) {
            LedgerEngine.planForEdit(months, allocs, excludePaymentId, amount)
        } else {
            LedgerEngine.plan(LedgerEngine.computeStates(months, allocs), amount)
        }
    }

    /** Records a payment and its allocations atomically, then recalculates the ledger. */
    suspend fun recordPayment(
        tenantId: Long,
        paymentDate: Long,
        amount: Long,
        mode: PaymentMode,
        from: String,
        to: String,
        receiptNumber: String,
        notes: String = ""
    ): Long = db.withTransaction {
        if (amount <= 0L) throw AccountingException("Payment amount must be greater than zero.")
        validatePeriod(from, to)
        val tenant = tenants.find(tenantId) ?: throw AccountingException("Tenant not found.")
        val room = rooms.find(tenant.roomId) ?: throw AccountingException("Room not found.")

        buildLedgerThrough(tenantId, maxOf(to, MonthKey.current()))

        val receipt = receiptNumber.trim()
        if (receipt.isNotBlank() && payments.receiptExists(receipt) > 0) {
            throw AccountingException("Receipt number $receipt has already been used.")
        }

        val fingerprint = ImportEngine.fingerprint(
            room.displayRoomNumber, paymentDate, amount, receipt, mode.name, from, to
        )
        if (payments.fingerprintExists(fingerprint) > 0) {
            throw AccountingException(
                "An identical payment is already recorded for this tenant, date, amount and period."
            )
        }

        // Plan first: if this would overpay, nothing has been written yet.
        val plan = LedgerEngine.plan(
            LedgerEngine.computeStates(ledgerMonthsFor(tenantId, from, to), allocationsFor(tenantId)),
            amount
        )

        val paymentId = payments.insert(
            PaymentEntity(
                tenantId = tenantId,
                receiptNumber = receipt,
                paymentDate = paymentDate,
                amountPaid = amount,
                paymentMode = mode,
                paidFromMonth = from,
                paidToMonth = to,
                fingerprint = fingerprint,
                notes = notes.trim()
            )
        )

        allocations.insertAll(
            plan.lines.map {
                PaymentAllocationEntity(
                    paymentId = paymentId,
                    ledgerMonthId = it.ledgerMonthId,
                    allocatedAmount = it.amount
                )
            }
        )

        recalculateTenant(tenantId)
        verifyTenantInvariants(tenantId)
        paymentId
    }

    /**
     * Edits a payment by fully reversing its allocations, re-planning against the resulting
     * ledger, and re-applying — all in one transaction. No stale allocation can survive.
     */
    suspend fun editPayment(
        paymentId: Long,
        paymentDate: Long,
        amount: Long,
        mode: PaymentMode,
        from: String,
        to: String,
        receiptNumber: String,
        notes: String = ""
    ) = db.withTransaction {
        if (amount <= 0L) throw AccountingException("Payment amount must be greater than zero.")
        validatePeriod(from, to)
        val existing = payments.find(paymentId) ?: throw AccountingException("Payment no longer exists.")
        val tenantId = existing.tenantId
        val tenant = tenants.find(tenantId) ?: throw AccountingException("Tenant not found.")
        val room = rooms.find(tenant.roomId) ?: throw AccountingException("Room not found.")

        val receipt = receiptNumber.trim()
        if (receipt.isNotBlank() && payments.receiptExists(receipt, paymentId) > 0) {
            throw AccountingException("Receipt number $receipt has already been used.")
        }

        buildLedgerThrough(tenantId, maxOf(to, MonthKey.current()))

        // Step 1: reverse the old allocations.
        allocations.deleteByPayment(paymentId)

        // Step 2: re-plan against the ledger as it now stands.
        val plan = LedgerEngine.plan(
            LedgerEngine.computeStates(ledgerMonthsFor(tenantId, from, to), allocationsFor(tenantId)),
            amount
        )

        val fingerprint = ImportEngine.fingerprint(
            room.displayRoomNumber, paymentDate, amount, receipt, mode.name, from, to
        )
        if (payments.fingerprintExists(fingerprint, paymentId) > 0) {
            throw AccountingException("Another payment with identical details already exists.")
        }

        // Step 3: apply.
        payments.update(
            existing.copy(
                receiptNumber = receipt,
                paymentDate = paymentDate,
                amountPaid = amount,
                paymentMode = mode,
                paidFromMonth = from,
                paidToMonth = to,
                fingerprint = fingerprint,
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        allocations.insertAll(
            plan.lines.map {
                PaymentAllocationEntity(
                    paymentId = paymentId,
                    ledgerMonthId = it.ledgerMonthId,
                    allocatedAmount = it.amount
                )
            }
        )

        // Step 4: recalculate every month for the tenant, not just the edited period.
        recalculateTenant(tenantId)
        verifyTenantInvariants(tenantId)
    }

    /** Deletes a payment, reverses its allocations and recalculates the ledger. */
    suspend fun deletePayment(paymentId: Long) = db.withTransaction {
        val payment = payments.find(paymentId) ?: throw AccountingException("Payment no longer exists.")
        val tenantId = payment.tenantId
        allocations.deleteByPayment(paymentId)
        payments.delete(payment)
        recalculateTenant(tenantId)
        verifyTenantInvariants(tenantId)
    }

    suspend fun findPayment(id: Long): PaymentEntity? = payments.find(id)

    suspend fun allocationsForPayment(id: Long): List<PaymentAllocationEntity> = allocations.byPayment(id)

    // ──────────────────────────────────────────────
    // Recalculation & invariants
    // ──────────────────────────────────────────────

    /**
     * Rebuilds the cached ledger projection from the allocations. Deterministic and
     * idempotent: running it twice yields identical rows.
     */
    suspend fun recalculateTenant(tenantId: Long) {
        val rows = ledger.forTenant(tenantId)
        if (rows.isEmpty()) return
        val allocs = allocations.byTenant(tenantId)
        val states = LedgerEngine.computeStates(rows.map { it.toDomain() }, allocs.map { it.toDomain() })
            .associateBy { it.id }

        val lastPaidDates = mutableMapOf<Long, Long>()
        if (allocs.isNotEmpty()) {
            val byId = payments.getAll().associateBy { it.id }
            allocs.forEach { a ->
                val date = byId[a.paymentId]?.paymentDate ?: return@forEach
                lastPaidDates[a.ledgerMonthId] = maxOf(lastPaidDates[a.ledgerMonthId] ?: 0L, date)
            }
        }

        val updated = rows.mapNotNull { row ->
            val state = states[row.id] ?: return@mapNotNull null
            val status = when {
                state.paid <= 0L && state.rentDue == 0L -> LedgerStatus.PAID
                state.paid <= 0L -> LedgerStatus.UNPAID
                state.paid >= state.rentDue -> LedgerStatus.PAID
                else -> LedgerStatus.PARTIALLY_PAID
            }
            val candidate = row.copy(
                totalPaid = state.paid,
                balance = state.outstanding,
                status = status,
                lastPaymentDate = lastPaidDates[row.id],
                updatedAt = System.currentTimeMillis()
            )
            if (candidate.totalPaid == row.totalPaid &&
                candidate.balance == row.balance &&
                candidate.status == row.status &&
                candidate.lastPaymentDate == row.lastPaymentDate
            ) null else candidate
        }
        if (updated.isNotEmpty()) ledger.updateAll(updated)
    }

    /** Aborts the surrounding transaction if the tenant's books are inconsistent. */
    private suspend fun verifyTenantInvariants(tenantId: Long) {
        val rows = ledger.forTenant(tenantId).map { it.toDomain() }
        val allocs = allocations.byTenant(tenantId)
        val amounts = allocs.map { it.paymentId }.distinct()
            .mapNotNull { id -> payments.find(id)?.let { id to it.amountPaid } }
            .toMap()
        LedgerEngine.verifyInvariants(rows, allocs.map { it.toDomain() }, amounts)
    }

    // ──────────────────────────────────────────────
    // Derived reads
    // ──────────────────────────────────────────────

    suspend fun summaryFor(tenantId: Long, asOf: String = MonthKey.current()): DefaulterSummary {
        val rows = ledger.forTenant(tenantId).map { it.toDomain() }
        val allocs = allocations.byTenant(tenantId).map { it.toDomain() }
        return LedgerEngine.summarise(LedgerEngine.computeStates(rows, allocs), asOf)
    }

    suspend fun outstanding(tenantId: Long, asOf: String = MonthKey.current()): Long =
        ledger.outstandingFor(tenantId, asOf)

    private suspend fun ledgerMonthsFor(tenantId: Long, from: String, to: String): List<LedgerMonth> =
        ledger.range(tenantId, from, to).map { it.toDomain() }

    private suspend fun allocationsFor(tenantId: Long): List<Allocation> =
        allocations.byTenant(tenantId).map { it.toDomain() }

    private fun validatePeriod(from: String, to: String) {
        if (!MonthKey.isValid(from) || !MonthKey.isValid(to)) {
            throw AccountingException("Payment period must use valid months.")
        }
        if (from > to) throw AccountingException("The 'paid from' month cannot be after the 'paid to' month.")
    }

    // ──────────────────────────────────────────────
    // Receipt numbering
    // ──────────────────────────────────────────────

    suspend fun nextReceiptNumber(): String {
        val prefix = settings.get(KEY_RECEIPT_PREFIX) ?: DEFAULT_RECEIPT_PREFIX
        val year = java.time.Year.now().value
        val full = "$prefix-$year-"
        val next = (payments.maxReceiptSequence(full) ?: 0) + 1
        return "%s%04d".format(full, next)
    }

    // ──────────────────────────────────────────────
    // Import
    // ──────────────────────────────────────────────

    suspend fun validateImport(rows: List<RawPaymentRow>): ImportResult {
        val roomNames = rooms.getAll().map { it.displayRoomNumber }.toSet()
        return ImportEngine.validate(
            rows = rows,
            knownRooms = roomNames,
            existingFingerprints = payments.allFingerprints().toSet(),
            existingReceipts = payments.allReceiptNumbers().toSet()
        )
    }

    /**
     * Commits a validated import inside one transaction. If any row fails at commit time
     * the whole import rolls back — the database is never left half-imported. Review and
     * rejected rows are persisted as visible issues rather than discarded.
     */
    suspend fun commitImport(result: ImportResult): ImportResult = db.withTransaction {
        result.valid.forEach { row ->
            val room = rooms.findByDisplay(row.roomDisplay)
                ?: throw AccountingException("Room ${row.roomDisplay} disappeared during import.")
            val tenant = tenants.findActiveByRoom(room.id)
                ?: throw AccountingException(
                    "Room ${row.roomDisplay} has no active tenant, so its payment cannot be imported."
                )

            recordPayment(
                tenantId = tenant.id,
                paymentDate = row.paymentDateMillis,
                amount = row.amount,
                mode = runCatching { PaymentMode.valueOf(row.paymentMode.uppercase()) }
                    .getOrDefault(PaymentMode.OTHER),
                from = row.paidFromMonth,
                to = row.paidToMonth,
                receiptNumber = row.receiptNumber,
                notes = "Imported"
            )
        }

        val issues = (result.review + result.rejected).map {
            ImportValidationIssueEntity(
                reference = it.reference.ifBlank { "row ${it.rowNumber}" },
                kind = it.kind.name,
                message = it.message,
                sourceValue = "row ${it.rowNumber}"
            )
        }
        if (issues.isNotEmpty()) validation.insertAll(issues)

        result
    }

    suspend fun addValidationIssue(issue: ImportValidationIssueEntity) = validation.insert(issue)
    suspend fun resolveIssue(id: Long) = validation.resolve(id)

    // ──────────────────────────────────────────────
    // Settings
    // ──────────────────────────────────────────────

    suspend fun getSetting(key: String): String? = settings.get(key)
    suspend fun setSetting(key: String, value: String) = settings.upsert(SettingEntity(key, value))

    // ──────────────────────────────────────────────
    // Backup access
    // ──────────────────────────────────────────────

    suspend fun getAllRooms(): List<RoomEntity> = rooms.getAll()
    suspend fun getAllTenants(): List<TenantEntity> = tenants.getAll()
    suspend fun getAllPayments(): List<PaymentEntity> = payments.getAll()
    suspend fun getAllLedger(): List<MonthlyLedgerEntity> = ledger.getAll()
    suspend fun getAllAllocations(): List<PaymentAllocationEntity> = allocations.getAll()
    suspend fun getAllRentChanges(): List<RentChangeEntity> = rents.getAll()
    suspend fun getAllSettings(): List<SettingEntity> = settings.getAll()
    suspend fun getAllValidationIssues(): List<ImportValidationIssueEntity> = validation.getAll()
    fun database(): AppDatabase = db

    companion object {
        const val KEY_RECEIPT_PREFIX = "receipt_prefix"
        const val DEFAULT_RECEIPT_PREFIX = "PS"
        const val KEY_PROPERTY_NAME = "property_name"
        const val KEY_PROPERTY_ADDRESS = "property_address"
        const val DEFAULT_PROPERTY_NAME = "PANSARE SADAN"
        const val DEFAULT_PROPERTY_ADDRESS = "Sakinaka, Mohili Village"
    }
}

// ── Entity -> domain mappers ─────────────────────────────────────────

internal fun MonthlyLedgerEntity.toDomain() = LedgerMonth(
    id = id,
    month = month,
    rentDue = rentDue,
    certainty = certainty
)

internal fun PaymentAllocationEntity.toDomain() = Allocation(
    paymentId = paymentId,
    ledgerMonthId = ledgerMonthId,
    amount = allocatedAmount
)
