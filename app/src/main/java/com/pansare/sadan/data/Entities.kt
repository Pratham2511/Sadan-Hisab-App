package com.pansare.sadan.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pansare.sadan.domain.RentCertainty

// ──────────────────────────────────────────────────────────────────
// Enums
// ──────────────────────────────────────────────────────────────────

/**
 * Lifecycle of a tenancy. Payment behaviour (defaulter / partially paid) is NOT stored
 * here — it is always derived from the ledger. See LedgerEngine.summarise.
 */
enum class TenantStatus { ACTIVE, MOVED_OUT, INACTIVE }

enum class PaymentMode { CASH, UPI, BANK_TRANSFER, CHEQUE, OTHER;

    val label: String
        get() = when (this) {
            CASH -> "Cash"
            UPI -> "UPI"
            BANK_TRANSFER -> "Bank Transfer"
            CHEQUE -> "Cheque"
            OTHER -> "Other"
        }
}

/** Persisted mirror of the derived month status, kept for fast querying only. */
enum class LedgerStatus { UNPAID, PARTIALLY_PAID, PAID }

/** Lifecycle of a validation/reconciliation issue raised during import. */
enum class IssueStatus { OPEN, RESOLVED }

// ──────────────────────────────────────────────────────────────────
// Core Entities
// ──────────────────────────────────────────────────────────────────

/**
 * A physical room. Exists independently of any tenant and is never deleted when a
 * tenancy ends, so room identity stays stable across the property's lifetime.
 */
@Entity(
    tableName = "rooms",
    indices = [
        Index(value = ["displayRoomNumber"], unique = true),
        Index(value = ["wing"]),
        Index(value = ["sortKey"])
    ]
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wing: String,
    val roomNumber: String,
    val displayRoomNumber: String,
    /** Deterministic ordering key, e.g. "A-027-(A)". Never rely on row order. */
    val sortKey: String,
    val active: Boolean = true,
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tenants",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("roomId"), Index("status"), Index("tenantName"), Index("mobileNumber")]
)
data class TenantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val tenantName: String,
    val mobileNumber: String = "",
    /** Current rent. Historical months use RentChangeEntity, never this value. */
    val monthlyRent: Long,
    /** Canonical yyyy-MM. Required to build a ledger. */
    val occupancyStartMonth: String,
    /** Set when the tenancy ends; the ledger stops accruing after this month. */
    val occupancyEndMonth: String? = null,
    val status: TenantStatus = TenantStatus.ACTIVE,
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() = status == TenantStatus.ACTIVE
}

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = TenantEntity::class,
        parentColumns = ["id"],
        childColumns = ["tenantId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("tenantId"),
        Index("paymentDate"),
        Index("receiptNumber"),
        Index(value = ["fingerprint"], unique = true)
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tenantId: Long,
    /** May be blank for historical records that never had one. */
    val receiptNumber: String = "",
    val paymentDate: Long,
    val amountPaid: Long,
    val paymentMode: PaymentMode,
    /** Period the user intended this payment to settle. */
    val paidFromMonth: String,
    val paidToMonth: String,
    /**
     * Composite duplicate-detection key. Unique index, so an identical payment can never
     * be inserted twice even if two code paths race.
     */
    val fingerprint: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One tenant-month. The rent recorded here is the rent that applied to THAT month,
 * resolved from RentChangeEntity at the time the row was created.
 *
 * totalPaid / balance / status are a cached projection of the allocations. They are
 * always recomputed by the repository inside the same transaction that changes
 * allocations, so they can never drift.
 */
@Entity(
    tableName = "monthly_ledger",
    foreignKeys = [ForeignKey(
        entity = TenantEntity::class,
        parentColumns = ["id"],
        childColumns = ["tenantId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["tenantId", "month"], unique = true),
        Index(value = ["tenantId", "status"]),
        Index("month")
    ]
)
data class MonthlyLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tenantId: Long,
    /** Canonical yyyy-MM. */
    val month: String,
    /** Rent applicable to this specific month. */
    val rentDue: Long,
    /** How much we trust rentDue. UNRESOLVED months are excluded from firm totals. */
    val certainty: RentCertainty = RentCertainty.KNOWN,
    val totalPaid: Long = 0,
    val balance: Long = rentDue,
    val status: LedgerStatus = LedgerStatus.UNPAID,
    val lastPaymentDate: Long? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/** Immutable audit record tying one payment to one ledger month. */
@Entity(
    tableName = "payment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["paymentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MonthlyLedgerEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledgerMonthId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["paymentId", "ledgerMonthId"], unique = true),
        Index("ledgerMonthId"),
        Index("paymentId")
    ]
)
data class PaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val ledgerMonthId: Long,
    val allocatedAmount: Long,
    val createdAt: Long = System.currentTimeMillis()
)

/** A dated rent rate. Determines rentDue for every month from effectiveFromMonth onward. */
@Entity(
    tableName = "rent_changes",
    foreignKeys = [ForeignKey(
        entity = TenantEntity::class,
        parentColumns = ["id"],
        childColumns = ["tenantId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["tenantId", "effectiveFromMonth"], unique = true)]
)
data class RentChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tenantId: Long,
    val effectiveFromMonth: String,
    val monthlyRent: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A validation or reconciliation problem the user must see. Import never hides a bad row;
 * it records it here and reports the counts.
 */
@Entity(
    tableName = "import_validation_issues",
    indices = [Index("status"), Index("tenantId")]
)
data class ImportValidationIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tenantId: Long? = null,
    val reference: String,
    /** Matches com.pansare.sadan.domain.IssueKind. */
    val kind: String,
    val message: String,
    val sourceValue: String = "",
    val status: IssueStatus = IssueStatus.OPEN,
    val createdAt: Long = System.currentTimeMillis()
)

// ──────────────────────────────────────────────────────────────────
// Query Result Projections
// ──────────────────────────────────────────────────────────────────

/** A room together with its current tenant, if any. Vacant rooms have null tenant fields. */
data class RoomWithTenantRow(
    val roomId: Long,
    val wing: String,
    val displayRoomNumber: String,
    val sortKey: String,
    val tenantId: Long?,
    val tenantName: String?,
    val mobileNumber: String?,
    val monthlyRent: Long?,
    val occupancyStartMonth: String?,
    val tenantStatus: TenantStatus?
) {
    val isOccupied: Boolean get() = tenantId != null
}

data class PaymentWithTenantRow(
    @Embedded val payment: PaymentEntity,
    val tenantName: String,
    val displayRoomNumber: String
)

/** One allocation joined to its month, for tracing a payment back from the ledger. */
data class AllocationDetailRow(
    val allocationId: Long,
    val paymentId: Long,
    val ledgerMonthId: Long,
    val allocatedAmount: Long,
    val month: String,
    val receiptNumber: String,
    val paymentDate: Long,
    val paymentMode: PaymentMode
)
