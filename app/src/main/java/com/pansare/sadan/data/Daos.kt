package com.pansare.sadan.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY wing, roomNumber")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun find(id: Long): RoomEntity?

    @Query("SELECT * FROM rooms WHERE wing = :wing ORDER BY roomNumber")
    fun observeByWing(wing: String): Flow<List<RoomEntity>>

    @Insert
    suspend fun insert(room: RoomEntity): Long

    @Update
    suspend fun update(room: RoomEntity)

    @Query("SELECT * FROM rooms")
    suspend fun getAll(): List<RoomEntity>
}

@Dao
interface TenantDao {
    @Query("""
        SELECT t.id tenantId, t.tenantName, t.mobileNumber, t.monthlyRent,
               t.occupancyStartMonth, t.status manualStatus, t.remarks,
               r.id roomId, r.wing, r.displayRoomNumber
        FROM tenants t JOIN rooms r ON r.id = t.roomId
        WHERE t.active = 1
        ORDER BY r.wing, r.roomNumber
    """)
    fun observeRows(): Flow<List<TenantRoomRow>>

    @Query("SELECT * FROM tenants WHERE id = :id")
    suspend fun find(id: Long): TenantEntity?

    @Query("SELECT * FROM tenants WHERE roomId = :roomId AND active = 1")
    suspend fun findByRoom(roomId: Long): TenantEntity?

    @Insert
    suspend fun insert(tenant: TenantEntity): Long

    @Update
    suspend fun update(tenant: TenantEntity)

    @Query("SELECT COUNT(*) FROM tenants")
    suspend fun count(): Int

    @Query("SELECT * FROM tenants WHERE active = 1")
    suspend fun getAll(): List<TenantEntity>

    @Query("""
        SELECT t.id tenantId, t.tenantName, t.mobileNumber, t.monthlyRent,
               t.occupancyStartMonth, t.status manualStatus, t.remarks,
               r.id roomId, r.wing, r.displayRoomNumber
        FROM tenants t JOIN rooms r ON r.id = t.roomId
        WHERE t.active = 1
        AND (t.tenantName LIKE '%' || :query || '%'
             OR r.displayRoomNumber LIKE '%' || :query || '%'
             OR t.mobileNumber LIKE '%' || :query || '%')
        ORDER BY r.wing, r.roomNumber
    """)
    fun search(query: String): Flow<List<TenantRoomRow>>
}

@Dao
interface PaymentDao {
    @Query("""
        SELECT p.*, t.tenantName, r.displayRoomNumber
        FROM payments p
        JOIN tenants t ON t.id = p.tenantId
        JOIN rooms r ON r.id = t.roomId
        ORDER BY p.paymentDate DESC, p.id DESC
    """)
    fun observeHistory(): Flow<List<PaymentWithTenantRow>>

    @Query("""
        SELECT p.*, t.tenantName, r.displayRoomNumber
        FROM payments p
        JOIN tenants t ON t.id = p.tenantId
        JOIN rooms r ON r.id = t.roomId
        WHERE p.tenantId = :tenantId
        ORDER BY p.paymentDate DESC, p.id DESC
    """)
    fun observeForTenant(tenantId: Long): Flow<List<PaymentWithTenantRow>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun find(id: Long): PaymentEntity?

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT MAX(CAST(SUBSTR(receiptNumber, LENGTH(:prefix) + 1) AS INTEGER)) FROM payments WHERE receiptNumber LIKE :prefix || '%'")
    suspend fun maxReceiptSequence(prefix: String): Int?

    @Query("SELECT COUNT(*) FROM payments WHERE receiptNumber = :receipt AND id != :excludeId")
    suspend fun receiptExists(receipt: String, excludeId: Long = 0): Int
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM monthly_ledger WHERE tenantId = :tenantId AND month BETWEEN :from AND :to ORDER BY month")
    suspend fun range(tenantId: Long, from: String, to: String): List<MonthlyLedgerEntity>

    @Query("SELECT * FROM monthly_ledger WHERE tenantId = :tenantId ORDER BY month")
    fun observeForTenant(tenantId: Long): Flow<List<MonthlyLedgerEntity>>

    @Query("SELECT * FROM monthly_ledger WHERE tenantId = :tenantId AND month = :month")
    suspend fun find(tenantId: Long, month: String): MonthlyLedgerEntity?

    @Query("SELECT * FROM monthly_ledger WHERE id = :id")
    suspend fun findById(id: Long): MonthlyLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<MonthlyLedgerEntity>)

    @Update
    suspend fun updateAll(rows: List<MonthlyLedgerEntity>)

    @Query("SELECT COALESCE(SUM(balance), 0) FROM monthly_ledger WHERE tenantId = :tenantId AND month <= :asOf")
    suspend fun outstanding(tenantId: Long, asOf: String): Long

    @Query("SELECT COUNT(*) FROM monthly_ledger WHERE tenantId = :tenantId AND month <= :asOf AND status = 'UNPAID'")
    suspend fun unpaidCount(tenantId: Long, asOf: String): Int

    @Query("SELECT COUNT(*) FROM monthly_ledger WHERE tenantId = :tenantId AND month <= :asOf AND status = 'PARTIALLY_PAID'")
    suspend fun partiallyPaidCount(tenantId: Long, asOf: String): Int

    @Query("SELECT COUNT(*) FROM monthly_ledger WHERE tenantId = :tenantId AND month <= :asOf AND balance > 0")
    suspend fun unpaidOrPartialCount(tenantId: Long, asOf: String): Int

    @Query("SELECT MIN(month) FROM monthly_ledger WHERE tenantId = :tenantId AND balance > 0 AND month <= :asOf")
    suspend fun outstandingSince(tenantId: Long, asOf: String): String?

    @Query("SELECT MAX(month) FROM monthly_ledger WHERE tenantId = :tenantId AND balance = 0 AND month <= :asOf")
    suspend fun lastPaidUpTo(tenantId: Long, asOf: String): String?

    @Query("SELECT * FROM monthly_ledger")
    suspend fun getAll(): List<MonthlyLedgerEntity>

    // Monthly collection report
    @Query("""
        SELECT COALESCE(SUM(applicableRent), 0) FROM monthly_ledger
        WHERE month = :month
    """)
    suspend fun expectedRentForMonth(month: String): Long

    @Query("""
        SELECT COALESCE(SUM(totalPaid), 0) FROM monthly_ledger
        WHERE month = :month
    """)
    suspend fun collectedForMonth(month: String): Long

    @Query("""
        SELECT COUNT(*) FROM monthly_ledger
        WHERE month = :month AND status = :status
    """)
    suspend fun countByStatusForMonth(month: String, status: LedgerStatus): Int

    // Yearly report
    @Query("""
        SELECT COALESCE(SUM(applicableRent), 0) FROM monthly_ledger
        WHERE month BETWEEN :fromMonth AND :toMonth
    """)
    suspend fun expectedRentForRange(fromMonth: String, toMonth: String): Long

    @Query("""
        SELECT COALESCE(SUM(totalPaid), 0) FROM monthly_ledger
        WHERE month BETWEEN :fromMonth AND :toMonth
    """)
    suspend fun collectedForRange(fromMonth: String, toMonth: String): Long
}

@Dao
interface AllocationDao {
    @Insert
    suspend fun insertAll(rows: List<PaymentAllocationEntity>)

    @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun byPayment(paymentId: Long): List<PaymentAllocationEntity>

    @Query("SELECT COALESCE(SUM(allocatedAmount), 0) FROM payment_allocations WHERE ledgerMonthId = :ledgerId")
    suspend fun totalForLedger(ledgerId: Long): Long

    @Query("SELECT MAX(p.paymentDate) FROM payments p JOIN payment_allocations a ON a.paymentId = p.id WHERE a.ledgerMonthId = :ledgerId")
    suspend fun lastPaymentDate(ledgerId: Long): Long?

    @Query("DELETE FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun deleteByPayment(paymentId: Long)

    @Query("SELECT * FROM payment_allocations")
    suspend fun getAll(): List<PaymentAllocationEntity>
}

@Dao
interface RentChangeDao {
    @Query("SELECT * FROM rent_changes WHERE tenantId = :tenantId AND effectiveFromMonth <= :month ORDER BY effectiveFromMonth DESC LIMIT 1")
    suspend fun applicable(tenantId: Long, month: String): RentChangeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(change: RentChangeEntity): Long

    @Query("SELECT * FROM rent_changes WHERE tenantId = :tenantId ORDER BY effectiveFromMonth")
    suspend fun forTenant(tenantId: Long): List<RentChangeEntity>

    @Query("SELECT * FROM rent_changes")
    suspend fun getAll(): List<RentChangeEntity>
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingEntity)

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<SettingEntity>

    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<SettingEntity>>
}

@Dao
interface ValidationDao {
    @Insert
    suspend fun insertAll(rows: List<ImportValidationIssueEntity>)

    @Query("SELECT * FROM import_validation_issues ORDER BY roomDisplay")
    fun observeAll(): Flow<List<ImportValidationIssueEntity>>

    @Query("SELECT * FROM import_validation_issues ORDER BY roomDisplay")
    suspend fun getAll(): List<ImportValidationIssueEntity>
}
