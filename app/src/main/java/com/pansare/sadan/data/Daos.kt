package com.pansare.sadan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.Update
import com.pansare.sadan.domain.RentCertainty
import kotlinx.coroutines.flow.Flow

/** Enum <-> String converters. Stored as names so backups stay readable and stable. */
class Converters {
    @TypeConverter fun tenantStatusToString(v: TenantStatus): String = v.name
    @TypeConverter fun stringToTenantStatus(v: String): TenantStatus = TenantStatus.valueOf(v)

    @TypeConverter fun paymentModeToString(v: PaymentMode): String = v.name
    @TypeConverter fun stringToPaymentMode(v: String): PaymentMode = PaymentMode.valueOf(v)

    @TypeConverter fun ledgerStatusToString(v: LedgerStatus): String = v.name
    @TypeConverter fun stringToLedgerStatus(v: String): LedgerStatus = LedgerStatus.valueOf(v)

    @TypeConverter fun certaintyToString(v: RentCertainty): String = v.name
    @TypeConverter fun stringToCertainty(v: String): RentCertainty = RentCertainty.valueOf(v)

    @TypeConverter fun issueStatusToString(v: IssueStatus): String = v.name
    @TypeConverter fun stringToIssueStatus(v: String): IssueStatus = IssueStatus.valueOf(v)
}

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY sortKey")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun count(): Int

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun find(id: Long): RoomEntity?

    @Query("SELECT * FROM rooms WHERE displayRoomNumber = :display")
    suspend fun findByDisplay(display: String): RoomEntity?

    @Query("SELECT * FROM rooms ORDER BY sortKey")
    suspend fun getAll(): List<RoomEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(room: RoomEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rooms: List<RoomEntity>)

    @Update
    suspend fun update(room: RoomEntity)

    /**
     * Every room with its current active tenant, if any. LEFT JOIN keeps vacant rooms
     * in the list — the room inventory is independent of tenancy.
     */
    @Query("""
        SELECT r.id AS roomId, r.wing, r.displayRoomNumber, r.sortKey,
               t.id AS tenantId, t.tenantName, t.mobileNumber, t.monthlyRent,
               t.occupancyStartMonth, t.status AS tenantStatus
        FROM rooms r
        LEFT JOIN tenants t ON t.roomId = r.id AND t.status = 'ACTIVE'
        ORDER BY r.sortKey
    """)
    fun observeRoomsWithTenants(): Flow<List<RoomWithTenantRow>>

    @Query("""
        SELECT r.id AS roomId, r.wing, r.displayRoomNumber, r.sortKey,
               t.id AS tenantId, t.tenantName, t.mobileNumber, t.monthlyRent,
               t.occupancyStartMonth, t.status AS tenantStatus
        FROM rooms r
        LEFT JOIN tenants t ON t.roomId = r.id AND t.status = 'ACTIVE'
        ORDER BY r.sortKey
    """)
    suspend fun roomsWithTenants(): List<RoomWithTenantRow>
}

@Dao
interface TenantDao {
    @Query("SELECT * FROM tenants WHERE id = :id")
    suspend fun find(id: Long): TenantEntity?

    @Query("SELECT * FROM tenants WHERE id = :id")
    fun observe(id: Long): Flow<TenantEntity?>

    @Query("SELECT * FROM tenants WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1")
    suspend fun findActiveByRoom(roomId: Long): TenantEntity?

    @Query("SELECT COUNT(*) FROM tenants")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM tenants WHERE status = 'ACTIVE'")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM tenants WHERE status = 'ACTIVE'")
    suspend fun getAllActive(): List<TenantEntity>

    @Query("SELECT * FROM tenants")
    suspend fun getAll(): List<TenantEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tenant: TenantEntity): Long

    @Update
    suspend fun update(tenant: TenantEntity)

    @Delete
    suspend fun delete(tenant: TenantEntity)
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

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT COUNT(*) FROM payments")
    suspend fun count(): Int

    @Query("SELECT fingerprint FROM payments")
    suspend fun allFingerprints(): List<String>

    @Query("SELECT COUNT(*) FROM payments WHERE fingerprint = :fingerprint AND id != :excludeId")
    suspend fun fingerprintExists(fingerprint: String, excludeId: Long = 0): Int

    @Query("SELECT COUNT(*) FROM payments WHERE receiptNumber = :receipt AND receiptNumber != '' AND id != :excludeId")
    suspend fun receiptExists(receipt: String, excludeId: Long = 0): Int

    @Query("""
        SELECT MAX(CAST(SUBSTR(receiptNumber, LENGTH(:prefix) + 1) AS INTEGER))
        FROM payments WHERE receiptNumber LIKE :prefix || '%'
    """)
    suspend fun maxReceiptSequence(prefix: String): Int?

    /** Collections in a date window, for the collection reports. */
    @Query("SELECT COALESCE(SUM(amountPaid), 0) FROM payments WHERE paymentDate BETWEEN :from AND :to")
    suspend fun collectedBetween(from: Long, to: Long): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM monthly_ledger WHERE tenantId = :tenantId ORDER BY month")
    suspend fun forTenant(tenantId: Long): List<MonthlyLedgerEntity>

    @Query("SELECT * FROM monthly_ledger WHERE tenantId = :tenantId ORDER BY month")
    fun observeForTenant(tenantId: Long): Flow<List<MonthlyLedgerEntity>>

    @Query("SELECT * FROM monthly_ledger WHERE tenantId = :tenantId AND month BETWEEN :from AND :to ORDER BY month")
    suspend fun range(tenantId: Long, from: String, to: String): List<MonthlyLedgerEntity>

    @Query("SELECT * FROM monthly_ledger WHERE tenantId = :tenantId AND month = :month")
    suspend fun find(tenantId: Long, month: String): MonthlyLedgerEntity?

    @Query("SELECT * FROM monthly_ledger WHERE id = :id")
    suspend fun findById(id: Long): MonthlyLedgerEntity?

    @Query("SELECT * FROM monthly_ledger")
    suspend fun getAll(): List<MonthlyLedgerEntity>

    @Query("SELECT MAX(month) FROM monthly_ledger WHERE tenantId = :tenantId")
    suspend fun latestMonth(tenantId: Long): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<MonthlyLedgerEntity>)

    @Update
    suspend fun updateAll(rows: List<MonthlyLedgerEntity>)

    @Query("DELETE FROM monthly_ledger WHERE tenantId = :tenantId AND month > :lastMonth AND totalPaid = 0")
    suspend fun trimUnpaidAfter(tenantId: Long, lastMonth: String)

    // ── Aggregates used by the dashboard and reports ──────────────────

    @Query("SELECT COALESCE(SUM(balance), 0) FROM monthly_ledger WHERE month <= :asOf")
    fun observeTotalOutstanding(asOf: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(balance), 0) FROM monthly_ledger WHERE tenantId = :tenantId AND month <= :asOf")
    suspend fun outstandingFor(tenantId: Long, asOf: String): Long

    @Query("SELECT COUNT(*) FROM monthly_ledger WHERE month <= :asOf AND status = 'UNPAID'")
    fun observeUnpaidMonthCount(asOf: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM monthly_ledger WHERE month <= :asOf AND status = 'PARTIALLY_PAID'")
    fun observePartialMonthCount(asOf: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(rentDue), 0) FROM monthly_ledger WHERE month = :month AND certainty = 'KNOWN'")
    suspend fun expectedForMonth(month: String): Long

    @Query("SELECT COALESCE(SUM(totalPaid), 0) FROM monthly_ledger WHERE month = :month")
    suspend fun collectedForMonth(month: String): Long

    @Query("SELECT COUNT(*) FROM monthly_ledger WHERE month = :month AND status = :status")
    suspend fun countByStatusForMonth(month: String, status: LedgerStatus): Int

    @Query("SELECT COALESCE(SUM(rentDue), 0) FROM monthly_ledger WHERE month BETWEEN :from AND :to AND certainty = 'KNOWN'")
    suspend fun expectedForRange(from: String, to: String): Long

    @Query("SELECT COALESCE(SUM(totalPaid), 0) FROM monthly_ledger WHERE month BETWEEN :from AND :to")
    suspend fun collectedForRange(from: String, to: String): Long

    @Query("SELECT COALESCE(SUM(balance), 0) FROM monthly_ledger WHERE certainty != 'KNOWN' AND month <= :asOf")
    suspend fun unresolvedOutstanding(asOf: String): Long
}

@Dao
interface AllocationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<PaymentAllocationEntity>)

    @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun byPayment(paymentId: Long): List<PaymentAllocationEntity>

    @Query("""
        SELECT * FROM payment_allocations
        WHERE ledgerMonthId IN (SELECT id FROM monthly_ledger WHERE tenantId = :tenantId)
    """)
    suspend fun byTenant(tenantId: Long): List<PaymentAllocationEntity>

    @Query("SELECT * FROM payment_allocations")
    suspend fun getAll(): List<PaymentAllocationEntity>

    @Query("DELETE FROM payment_allocations WHERE paymentId = :paymentId")
    suspend fun deleteByPayment(paymentId: Long)

    /** Traces each ledger month back to the payments that settled it. */
    @Query("""
        SELECT a.id AS allocationId, a.paymentId, a.ledgerMonthId, a.allocatedAmount,
               l.month, p.receiptNumber, p.paymentDate, p.paymentMode
        FROM payment_allocations a
        JOIN monthly_ledger l ON l.id = a.ledgerMonthId
        JOIN payments p ON p.id = a.paymentId
        WHERE l.tenantId = :tenantId
        ORDER BY l.month, p.paymentDate
    """)
    fun observeDetailsForTenant(tenantId: Long): Flow<List<AllocationDetailRow>>
}

@Dao
interface RentChangeDao {
    @Query("SELECT * FROM rent_changes WHERE tenantId = :tenantId ORDER BY effectiveFromMonth")
    suspend fun forTenant(tenantId: Long): List<RentChangeEntity>

    @Query("SELECT * FROM rent_changes WHERE tenantId = :tenantId ORDER BY effectiveFromMonth")
    fun observeForTenant(tenantId: Long): Flow<List<RentChangeEntity>>

    @Query("SELECT * FROM rent_changes")
    suspend fun getAll(): List<RentChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(change: RentChangeEntity): Long

    @Query("DELETE FROM rent_changes WHERE tenantId = :tenantId AND effectiveFromMonth = :month")
    suspend fun deleteAt(tenantId: Long, month: String)
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

    @Insert
    suspend fun insert(row: ImportValidationIssueEntity): Long

    @Query("SELECT * FROM import_validation_issues ORDER BY status, createdAt DESC")
    fun observeAll(): Flow<List<ImportValidationIssueEntity>>

    @Query("SELECT COUNT(*) FROM import_validation_issues WHERE status = 'OPEN'")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM import_validation_issues")
    suspend fun getAll(): List<ImportValidationIssueEntity>

    @Query("UPDATE import_validation_issues SET status = 'RESOLVED' WHERE id = :id")
    suspend fun resolve(id: Long)

    @Query("DELETE FROM import_validation_issues WHERE id = :id")
    suspend fun delete(id: Long)
}
