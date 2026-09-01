package com.pansare.sadan.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pansare.sadan.data.*
import com.pansare.sadan.data.backup.BackupManager
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.domain.PaymentAllocator
import com.pansare.sadan.util.AllocationLine
import com.pansare.sadan.util.ReceiptDetails
import com.pansare.sadan.util.ReceiptPdf
import com.pansare.sadan.util.ShareUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * Central ViewModel driving all screens. Exposes reactive state via StateFlow/SharedFlow.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repo = RentRepository(db)
    private val backupManager = BackupManager(repo)

    // ──────────────────────────────────────────────
    // Reactive data streams
    // ──────────────────────────────────────────────

    val tenants: StateFlow<List<TenantRoomRow>> = repo.observeTenants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rooms: StateFlow<List<RoomEntity>> = repo.observeRooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val payments: StateFlow<List<PaymentWithTenantRow>> = repo.observePayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val validationIssues: StateFlow<List<ImportValidationIssueEntity>> = repo.observeValidationIssues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ──────────────────────────────────────────────
    // Messages (snackbar events)
    // ──────────────────────────────────────────────

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    // ──────────────────────────────────────────────
    // As-of date
    // ──────────────────────────────────────────────

    private val _asOfDate = MutableStateFlow(MonthKey.current())
    val asOfDate: StateFlow<String> = _asOfDate.asStateFlow()

    fun setAsOfDate(date: String) {
        _asOfDate.value = date
    }

    // ──────────────────────────────────────────────
    // Dashboard data
    // ──────────────────────────────────────────────

    data class TenantDashboardInfo(
        val tenantId: Long,
        val tenantName: String,
        val displayRoomNumber: String,
        val wing: String,
        val monthlyRent: Long,
        val outstanding: Long,
        val unpaidMonths: Int,
        val partiallyPaidMonths: Int,
        val outstandingSince: String?,
        val lastPaidUpTo: String?,
        val status: TenantStatus
    )

    private val _dashboardData = MutableStateFlow<List<TenantDashboardInfo>>(emptyList())
    val dashboardData: StateFlow<List<TenantDashboardInfo>> = _dashboardData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun refreshDashboard() = viewModelScope.launch {
        _isLoading.value = true
        val asOf = _asOfDate.value
        val tenantList = tenants.value
        val data = tenantList.map { t ->
            val owed = repo.outstanding(t.tenantId, asOf)
            val unpaid = repo.unpaidCount(t.tenantId, asOf)
            val partial = repo.partiallyPaidCount(t.tenantId, asOf)
            val since = repo.outstandingSince(t.tenantId, asOf)
            val lastPaid = repo.lastPaidUpTo(t.tenantId, asOf)
            val status = when {
                owed == 0L -> TenantStatus.REGULAR
                partial > 0 -> TenantStatus.PARTIALLY_PAID
                unpaid > 0 -> TenantStatus.DEFAULTER
                else -> TenantStatus.OTHER
            }
            TenantDashboardInfo(
                tenantId = t.tenantId,
                tenantName = t.tenantName,
                displayRoomNumber = t.displayRoomNumber,
                wing = t.wing,
                monthlyRent = t.monthlyRent,
                outstanding = owed,
                unpaidMonths = unpaid,
                partiallyPaidMonths = partial,
                outstandingSince = since,
                lastPaidUpTo = lastPaid,
                status = status
            )
        }
        _dashboardData.value = data
        _isLoading.value = false
    }

    // ──────────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────────

    init {
        viewModelScope.launch {
            runCatching { repo.seedIfEmpty() }
                .onFailure { _message.emit(it.message ?: "Could not initialize data") }
            // Wait for tenants to load, then refresh dashboard
            tenants.first { it.isNotEmpty() }
            refreshDashboard()
        }
    }

    // ──────────────────────────────────────────────
    // Payment operations
    // ──────────────────────────────────────────────

    fun addPayment(
        tenantId: Long,
        from: String,
        to: String,
        amount: Long,
        receipt: String,
        mode: PaymentMode,
        notes: String,
        paymentDate: Long = System.currentTimeMillis()
    ) = viewModelScope.launch {
        runCatching {
            repo.recordPayment(
                PaymentEntity(
                    tenantId = tenantId,
                    receiptNumber = receipt,
                    paymentDate = paymentDate,
                    paidFromMonth = from,
                    paidToMonth = to,
                    numberOfMonths = 0,
                    amountPaid = amount,
                    paymentMode = mode,
                    notes = notes
                )
            )
        }
            .onSuccess {
                _message.emit("Payment saved and ledger allocated.")
                refreshDashboard()
            }
            .onFailure { _message.emit(it.message ?: "Payment could not be saved.") }
    }

    fun editPayment(payment: PaymentEntity) = viewModelScope.launch {
        runCatching { repo.editPayment(payment) }
            .onSuccess {
                _message.emit("Payment updated and ledger recalculated.")
                refreshDashboard()
            }
            .onFailure { _message.emit(it.message ?: "Payment could not be updated.") }
    }

    fun deletePayment(paymentId: Long) = viewModelScope.launch {
        runCatching { repo.deletePayment(paymentId) }
            .onSuccess {
                _message.emit("Payment deleted and ledger recalculated.")
                refreshDashboard()
            }
            .onFailure { _message.emit(it.message ?: "Payment could not be deleted.") }
    }

    suspend fun findPayment(id: Long): PaymentEntity? = repo.findPayment(id)

    // ──────────────────────────────────────────────
    // Tenant operations
    // ──────────────────────────────────────────────

    fun addTenant(roomId: Long, name: String, rent: Long, occupancy: String?) = viewModelScope.launch {
        runCatching {
            repo.addTenant(
                TenantEntity(
                    roomId = roomId,
                    tenantName = name,
                    monthlyRent = rent,
                    occupancyStartMonth = occupancy
                )
            )
        }
            .onSuccess { _message.emit("Tenant added.") }
            .onFailure { _message.emit(it.message ?: "Tenant could not be added.") }
    }

    fun updateTenant(tenant: TenantEntity) = viewModelScope.launch {
        runCatching { repo.updateTenant(tenant) }
            .onSuccess {
                _message.emit("Tenant updated.")
                refreshDashboard()
            }
            .onFailure { _message.emit(it.message ?: "Tenant could not be updated.") }
    }

    suspend fun findTenant(id: Long): TenantEntity? = repo.findTenant(id)

    // ──────────────────────────────────────────────
    // Outstanding
    // ──────────────────────────────────────────────

    suspend fun outstanding(tenantId: Long, asOf: String = MonthKey.current()) =
        repo.outstanding(tenantId, asOf)

    // ──────────────────────────────────────────────
    // Ledger
    // ──────────────────────────────────────────────

    fun observeLedger(tenantId: Long) = repo.observeLedger(tenantId)

    fun observePaymentsForTenant(tenantId: Long) = repo.observePaymentsForTenant(tenantId)

    // ──────────────────────────────────────────────
    // Receipt generation & sharing
    // ──────────────────────────────────────────────

    fun generateAndShareReceipt(paymentId: Long) = viewModelScope.launch {
        val context = getApplication<Application>()
        runCatching {
            val payment = requireNotNull(repo.findPayment(paymentId)) { "Payment not found." }
            val tenant = requireNotNull(repo.findTenant(payment.tenantId)) { "Tenant not found." }
            val room = requireNotNull(repo.findRoom(tenant.roomId)) { "Room not found." }

            val previousOutstanding = repo.outstanding(tenant.id, payment.paidFromMonth)
            val remainingOutstanding = repo.outstanding(tenant.id, payment.paidToMonth)

            val file = File(context.cacheDir, "receipts/${payment.receiptNumber}.pdf")
            ReceiptPdf.create(
                file,
                payment,
                ReceiptDetails(
                    tenantName = tenant.tenantName,
                    roomNumber = room.displayRoomNumber,
                    previousOutstanding = previousOutstanding + payment.amountPaid,
                    remainingOutstanding = remainingOutstanding
                )
            )
            ShareUtils.sharePdf(context, file, "Receipt ${payment.receiptNumber}")
        }
            .onFailure { _message.emit(it.message ?: "Could not generate receipt.") }
    }

    fun generateReceipt(paymentId: Long, onComplete: (File?) -> Unit) = viewModelScope.launch {
        val context = getApplication<Application>()
        runCatching {
            val payment = requireNotNull(repo.findPayment(paymentId))
            val tenant = requireNotNull(repo.findTenant(payment.tenantId))
            val room = requireNotNull(repo.findRoom(tenant.roomId))

            val previousOutstanding = repo.outstanding(tenant.id, payment.paidFromMonth)
            val remainingOutstanding = repo.outstanding(tenant.id, payment.paidToMonth)

            val file = File(context.cacheDir, "receipts/${payment.receiptNumber}.pdf")
            ReceiptPdf.create(
                file,
                payment,
                ReceiptDetails(
                    tenantName = tenant.tenantName,
                    roomNumber = room.displayRoomNumber,
                    previousOutstanding = previousOutstanding + payment.amountPaid,
                    remainingOutstanding = remainingOutstanding
                )
            )
            file
        }
            .onSuccess { onComplete(it) }
            .onFailure {
                _message.emit(it.message ?: "Could not generate receipt.")
                onComplete(null)
            }
    }

    // ──────────────────────────────────────────────
    // Receipt number
    // ──────────────────────────────────────────────

    suspend fun nextReceiptNumber(): String = repo.nextReceiptNumber()

    // ──────────────────────────────────────────────
    // Reports
    // ──────────────────────────────────────────────

    suspend fun monthlyCollectionReport(month: String) = repo.monthlyCollectionReport(month)
    suspend fun yearlyReport(year: Int) = repo.yearlyReport(year)

    // ──────────────────────────────────────────────
    // Backup & Restore
    // ──────────────────────────────────────────────

    fun exportBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        val context = getApplication<Application>()
        runCatching { backupManager.exportBackup(context, uri, password) }
            .onSuccess { _message.emit("Backup exported successfully.") }
            .onFailure { _message.emit(it.message ?: "Backup failed.") }
    }

    fun restoreBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        val context = getApplication<Application>()
        runCatching { backupManager.restoreBackup(context, uri, password) }
            .onSuccess {
                _message.emit("Backup restored successfully. ${it.rooms.size} rooms, ${it.tenants.size} tenants.")
                refreshDashboard()
            }
            .onFailure { _message.emit(it.message ?: "Restore failed.") }
    }

    // ──────────────────────────────────────────────
    // Settings
    // ──────────────────────────────────────────────

    suspend fun getSetting(key: String): String? = repo.getSetting(key)
    fun setSetting(key: String, value: String) = viewModelScope.launch {
        repo.setSetting(key, value)
    }

    // ──────────────────────────────────────────────
    // Search
    // ──────────────────────────────────────────────

    fun searchTenants(query: String): Flow<List<TenantRoomRow>> = repo.searchTenants(query)
}
