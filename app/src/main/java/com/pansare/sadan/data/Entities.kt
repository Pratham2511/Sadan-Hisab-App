package com.pansare.sadan.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ──────────────────────────────────────────────────────────────────
// Enums
// ──────────────────────────────────────────────────────────────────

enum class TenantStatus { REGULAR, DEFAULTER, PARTIALLY_PAID, VACATED, OTHER }
enum class PaymentMode { CASH, UPI, BANK_TRANSFER, CHEQUE, OTHER }
enum class LedgerStatus { PAID, UNPAID, PARTIALLY_PAID, NOT_APPLICABLE }

// ──────────────────────────────────────────────────────────────────
// Core Entities
// ──────────────────────────────────────────────────────────────────

@Entity(
    tableName = "rooms",
    indices = [Index(value = ["wing", "roomNumber"], unique = true)]
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wing: String,
    val roomNumber: String,
    val displayRoomNumber: String,
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
    indices = [Index("roomId")]
)
data class TenantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val tenantName: String,
    val mobileNumber: String = "",
    val monthlyRent: Long,
    val occupancyStartMonth: String? = null,
    val status: TenantStatus = TenantStatus.OTHER,
    val active: Boolean = true,
    val remarks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = TenantEntity::class,
        parentColumns = ["id"],
        childColumns = ["tenantId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["receiptNumber"], unique = true),
        Index("tenantId")
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tenantId: Long,
    val receiptNumber: String,
    val paymentDate: Long,
    val paidFromMonth: String,
    val paidToMonth: String,
    val numberOfMonths: Int,
    val amountPaid: Long,
    val paymentMode: PaymentMode,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

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
        Index("tenantId", "status")
    ]
)
data class MonthlyLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tenantId: Long,
    /** Canonical ISO YearMonth: yyyy-MM. */
    val month: String,
    val applicableRent: Long,
    val totalPaid: Long = 0,
    val status: LedgerStatus = LedgerStatus.UNPAID,
    val amountDue: Long = applicableRent,
    val balance: Long = applicableRent,
    val lastPaymentDate: Long? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/** Immutable audit record tying a payment transaction to one ledger month. */
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

@Entity(tableName = "import_validation_issues")
data class ImportValidationIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomDisplay: String,
    val issue: String,
    val sourceValue: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ──────────────────────────────────────────────────────────────────
// Query Result POJOs
// ──────────────────────────────────────────────────────────────────

data class TenantRoomRow(
    val tenantId: Long,
    val tenantName: String,
    val mobileNumber: String,
    val monthlyRent: Long,
    val occupancyStartMonth: String?,
    val manualStatus: TenantStatus,
    val remarks: String,
    val roomId: Long,
    val wing: String,
    val displayRoomNumber: String
)

data class PaymentWithTenantRow(
    @Embedded val payment: PaymentEntity,
    val tenantName: String,
    val displayRoomNumber: String
)

data class DashboardRow(
    val roomId: Long,
    val wing: String,
    val displayRoomNumber: String,
    val tenantId: Long,
    val tenantName: String,
    val monthlyRent: Long,
    val totalOutstanding: Long,
    val unpaidMonths: Int,
    val partiallyPaidMonths: Int
)
