package com.pansare.sadan.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pansare.sadan.data.AppDatabase
import com.pansare.sadan.data.ImportValidationIssueEntity
import com.pansare.sadan.data.MonthlyLedgerEntity
import com.pansare.sadan.data.PaymentEntity
import com.pansare.sadan.data.PaymentMode
import com.pansare.sadan.data.PaymentWithTenantRow
import com.pansare.sadan.data.RentRepository
import com.pansare.sadan.data.RoomInventory
import com.pansare.sadan.data.RoomWithTenantRow
import com.pansare.sadan.data.TenantEntity
import com.pansare.sadan.data.backup.BackupManager
import com.pansare.sadan.domain.AllocationPlan
import com.pansare.sadan.domain.DefaulterSummary
import com.pansare.sadan.domain.ImportResult
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.util.CsvImport
import com.pansare.sadan.util.ReceiptData
import com.pansare.sadan.util.ReceiptLine
import com.pansare.sadan.util.ReceiptPdf
import com.pansare.sadan.util.ShareUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** A user-visible outcome. Never a fake success — failures carry the real reason. */
sealed interface UiEvent {
    data class Success(val message: String) : UiEvent
    data class Error(val message: String) : UiEvent
}

/** Filters offered on the Rooms screen. */
enum class RoomFilter(val label: String) {
    ALL("All"),
    OCCUPIED("Occupied"),
    VACANT("Vacant"),
    REGULAR("Regular"),
    DEFAULTER("Defaulter"),
    PARTIAL("Partially Paid")
}

/** A room row enriched with its derived financial state. */
data class RoomCardState(
    val roomId: Long,
    val wing: String,
    val displayRoomNumber: String,
    val tenantId: Long?,
    val tenantName: String?,
    val mobileNumber: String?,
    val monthlyRent: Long?,
    val outstanding: Long,
    val unpaidMonths: Int,
    val partialMonths: Int,
    val hasUnresolvedHistory: Boolean
) {
    val isOccupied: Boolean get() = tenantId != null

    /** Payment standing, derived from the ledger — never a stored flag. */
    val standing: String
        get() = when {
            !isOccupied -> "Vacant"
            outstanding <= 0L -> "Regular"
            partialMonths > 0 && unpaidMonths == 0 -> "Partially Paid"
            else -> "Defaulter"
        }
}

data class DashboardState(
    val totalRooms: Int = RoomInventory.TOTAL_ROOMS,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = RoomInventory.TOTAL_ROOMS,
    val totalTenants: Int = 0,
    val regularTenants: Int = 0,
    val defaulters: Int = 0,
    val partiallyPaidTenants: Int = 0,
    val unpaidMonths: Int = 0,
    val partialMonths: Int = 0,
    val totalOutstanding: Long = 0,
    val unresolvedOutstanding: Long = 0,
    val openIssues: Int = 0,
    val isLoading: Boolean = true
) {
    val isFreshInstall: Boolean get() = totalTenants == 0
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repo = RentRepository(db)
    private val backupManager = BackupManager(repo)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _asOf = MutableStateFlow(MonthKey.current())
    val asOf: StateFlow<String> = _asOf.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomCardState>>(emptyList())
    val rooms: StateFlow<List<RoomCardState>> = _rooms.asStateFlow()

    private val _dashboard = MutableStateFlow(DashboardState())
    val dashboard: StateFlow<DashboardState> = _dashboard.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(RoomFilter.ALL)
    val filter: StateFlow<RoomFilter> = _filter.asStateFlow()

    val payments: StateFlow<List<PaymentWithTenantRow>> = repo.observePayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val issues: StateFlow<List<ImportValidationIssueEntity>> = repo.observeValidationIssues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Rooms after search and filter are applied. Case-insensitive across room, name, mobile. */
    val visibleRooms: StateFlow<List<RoomCardState>> =
        combine(_rooms, _query, _filter) { list, q, f ->
            val term = q.trim().lowercase()
            list.asSequence()
                .filter { room ->
                    term.isBlank() ||
                        room.displayRoomNumber.lowercase().contains(term) ||
                        room.tenantName?.lowercase()?.contains(term) == true ||
                        room.mobileNumber?.lowercase()?.contains(term) == true
                }
                .filter { room ->
                    when (f) {
                        RoomFilter.ALL -> true
                        RoomFilter.OCCUPIED -> room.isOccupied
                        RoomFilter.VACANT -> !room.isOccupied
                        RoomFilter.REGULAR -> room.isOccupied && room.standing == "Regular"
                        RoomFilter.DEFAULTER -> room.standing == "Defaulter"
                        RoomFilter.PARTIAL -> room.standing == "Partially Paid"
                    }
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaulters: StateFlow<List<RoomCardState>> = _rooms
        .map { list -> list.filter { it.isOccupied && it.outstanding > 0 }.sortedByDescending { it.outstanding } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { repo.initialiseIfEmpty() }
                .onFailure { emitError(it, "Could not prepare the room inventory.") }
            refresh()
        }
        // Any change to rooms, tenants or payments re-derives the screens.
        viewModelScope.launch {
            combine(repo.observeRoomsWithTenants(), repo.observePayments()) { r, _ -> r }
                .collect { rowsChanged(it) }
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setFilter(value: RoomFilter) { _filter.value = value }

    fun setAsOf(month: String) {
        if (MonthKey.isValid(month)) {
            _asOf.value = month
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        runCatching { rowsChanged(null) }
            .onFailure { emitError(it, "Could not refresh.") }
    }

    /** Recomputes every room's derived state and the dashboard totals. */
    private suspend fun rowsChanged(rows: List<RoomWithTenantRow>?) {
        val asOf = _asOf.value
        val source = rows ?: repo.roomsWithTenants()
        val cards = source.map { row ->
            val summary: DefaulterSummary? = row.tenantId?.let { repo.summaryFor(it, asOf) }
            RoomCardState(
                roomId = row.roomId,
                wing = row.wing,
                displayRoomNumber = row.displayRoomNumber,
                tenantId = row.tenantId,
                tenantName = row.tenantName,
                mobileNumber = row.mobileNumber,
                monthlyRent = row.monthlyRent,
                outstanding = summary?.totalOutstanding ?: 0L,
                unpaidMonths = summary?.unpaidMonths ?: 0,
                partialMonths = summary?.partialMonths ?: 0,
                hasUnresolvedHistory = summary?.hasUnresolvedHistory ?: false
            )
        }
        _rooms.value = cards

        val occupied = cards.count { it.isOccupied }
        _dashboard.value = DashboardState(
            totalRooms = cards.size,
            occupiedRooms = occupied,
            vacantRooms = cards.size - occupied,
            totalTenants = occupied,
            regularTenants = cards.count { it.isOccupied && it.standing == "Regular" },
            defaulters = cards.count { it.standing == "Defaulter" },
            partiallyPaidTenants = cards.count { it.standing == "Partially Paid" },
            unpaidMonths = cards.sumOf { it.unpaidMonths },
            partialMonths = cards.sumOf { it.partialMonths },
            totalOutstanding = cards.sumOf { it.outstanding },
            unresolvedOutstanding = cards.filter { it.hasUnresolvedHistory }.sumOf { it.outstanding },
            openIssues = issues.value.size,
            isLoading = false
        )
    }

    // ── Tenants ───────────────────────────────────────────────────────

    fun addTenant(
        roomId: Long,
        name: String,
        mobile: String,
        rent: Long,
        occupancyStart: String,
        remarks: String,
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching { repo.addTenant(roomId, name, mobile, rent, occupancyStart, remarks) }
            .onSuccess {
                _events.emit(UiEvent.Success("$name added."))
                refresh(); onDone(true)
            }
            .onFailure { emitError(it, "Tenant could not be added."); onDone(false) }
    }

    fun updateTenant(tenant: TenantEntity, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        runCatching { repo.updateTenant(tenant) }
            .onSuccess { _events.emit(UiEvent.Success("Tenant updated.")); refresh(); onDone(true) }
            .onFailure { emitError(it, "Tenant could not be updated."); onDone(false) }
    }

    fun changeRent(tenantId: Long, newRent: Long, from: String, note: String, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            runCatching { repo.changeRent(tenantId, newRent, from, note) }
                .onSuccess { _events.emit(UiEvent.Success("Rent updated from ${MonthKey.displayName(from)}.")); refresh(); onDone(true) }
                .onFailure { emitError(it, "Rent could not be updated."); onDone(false) }
        }

    fun moveOut(tenantId: Long, endMonth: String, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        runCatching { repo.moveOutTenant(tenantId, endMonth) }
            .onSuccess { _events.emit(UiEvent.Success("Tenancy closed. The room is now vacant.")); refresh(); onDone(true) }
            .onFailure { emitError(it, "Could not close the tenancy."); onDone(false) }
    }

    suspend fun findTenant(id: Long): TenantEntity? = repo.findTenant(id)
    suspend fun summaryFor(id: Long): DefaulterSummary = repo.summaryFor(id, _asOf.value)
    fun observeTenant(id: Long): Flow<TenantEntity?> = repo.observeTenant(id)
    fun observeLedger(id: Long): Flow<List<MonthlyLedgerEntity>> = repo.observeLedger(id)
    fun observeAllocationDetails(id: Long) = repo.observeAllocationDetails(id)
    fun observePaymentsForTenant(id: Long) = repo.observePaymentsForTenant(id)
    fun observeRentChanges(id: Long) = repo.observeRentChanges(id)

    // ── Payments ──────────────────────────────────────────────────────

    /** Non-destructive preview so the user sees the allocation before saving. */
    suspend fun previewPayment(
        tenantId: Long, from: String, to: String, amount: Long, excludePaymentId: Long = 0
    ): Result<AllocationPlan> = runCatching {
        repo.previewPayment(tenantId, from, to, amount, excludePaymentId)
    }

    fun recordPayment(
        tenantId: Long, date: Long, amount: Long, mode: PaymentMode,
        from: String, to: String, receipt: String, notes: String,
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching { repo.recordPayment(tenantId, date, amount, mode, from, to, receipt, notes) }
            .onSuccess { _events.emit(UiEvent.Success("Payment recorded and allocated.")); refresh(); onDone(true) }
            .onFailure { emitError(it, "Payment could not be saved."); onDone(false) }
    }

    fun editPayment(
        paymentId: Long, date: Long, amount: Long, mode: PaymentMode,
        from: String, to: String, receipt: String, notes: String,
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching { repo.editPayment(paymentId, date, amount, mode, from, to, receipt, notes) }
            .onSuccess { _events.emit(UiEvent.Success("Payment updated; ledger recalculated.")); refresh(); onDone(true) }
            .onFailure { emitError(it, "Payment could not be updated."); onDone(false) }
    }

    fun deletePayment(paymentId: Long) = viewModelScope.launch {
        runCatching { repo.deletePayment(paymentId) }
            .onSuccess { _events.emit(UiEvent.Success("Payment deleted; ledger recalculated.")); refresh() }
            .onFailure { emitError(it, "Payment could not be deleted.") }
    }

    suspend fun findPayment(id: Long): PaymentEntity? = repo.findPayment(id)
    suspend fun nextReceiptNumber(): String = repo.nextReceiptNumber()

    // ── Receipts ──────────────────────────────────────────────────────

    private suspend fun buildReceipt(paymentId: Long): Pair<File, String> {
        val payment = repo.findPayment(paymentId) ?: error("Payment not found.")
        val tenant = repo.findTenant(payment.tenantId) ?: error("Tenant not found.")
        val room = repo.findRoom(tenant.roomId) ?: error("Room not found.")
        val allocs = repo.allocationsForPayment(paymentId)
        val summary = repo.summaryFor(tenant.id, _asOf.value)

        val monthById = repo.getAllLedger().filter { it.tenantId == tenant.id }.associateBy { it.id }
        val lines = allocs.mapNotNull { a ->
            monthById[a.ledgerMonthId]?.let { ReceiptLine(it.month, it.rentDue, a.allocatedAmount) }
        }.sortedBy { it.month }

        val context = getApplication<Application>()
        val file = File(context.cacheDir, "receipts/${payment.receiptNumber.ifBlank { "receipt-$paymentId" }}.pdf")
        ReceiptPdf.create(
            file,
            ReceiptData(
                propertyName = repo.getSetting(RentRepository.KEY_PROPERTY_NAME)
                    ?: RentRepository.DEFAULT_PROPERTY_NAME,
                propertyAddress = repo.getSetting(RentRepository.KEY_PROPERTY_ADDRESS)
                    ?: RentRepository.DEFAULT_PROPERTY_ADDRESS,
                receiptNumber = payment.receiptNumber.ifBlank { "—" },
                paymentDate = payment.paymentDate,
                roomNumber = room.displayRoomNumber,
                tenantName = tenant.tenantName,
                paidFromMonth = payment.paidFromMonth,
                paidToMonth = payment.paidToMonth,
                monthsCovered = lines.size,
                amount = payment.amountPaid,
                paymentMode = payment.paymentMode.label,
                remainingOutstanding = summary.totalOutstanding,
                allocations = lines,
                notes = payment.notes
            )
        )
        return file to payment.receiptNumber
    }

    fun shareReceipt(paymentId: Long) = viewModelScope.launch {
        runCatching {
            val (file, receipt) = buildReceipt(paymentId)
            ShareUtils.sharePdf(getApplication(), file, "Rent Receipt $receipt")
        }.onFailure { emitError(it, "Could not generate the receipt.") }
    }

    fun generateReceipt(paymentId: Long, onDone: (File?) -> Unit) = viewModelScope.launch {
        runCatching { buildReceipt(paymentId).first }
            .onSuccess { _events.emit(UiEvent.Success("Receipt saved.")); onDone(it) }
            .onFailure { emitError(it, "Could not generate the receipt."); onDone(null) }
    }

    // ── Reports ───────────────────────────────────────────────────────

    suspend fun monthlyReport(month: String) = runCatching { ReportBuilder(repo).monthly(month) }
    suspend fun yearlyReport(year: Int) = runCatching { ReportBuilder(repo).yearly(year) }
    suspend fun tenantHistory(tenantId: Long) = runCatching { ReportBuilder(repo).tenantHistory(tenantId) }

    // ── Backup / restore ──────────────────────────────────────────────

    fun exportBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        runCatching { backupManager.export(getApplication(), uri, password) }
            .onSuccess { _events.emit(UiEvent.Success("Encrypted backup saved.")) }
            .onFailure { emitError(it, "Backup failed.") }
            .also { password.fill('\u0000') }
    }

    fun restoreBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        runCatching { backupManager.restore(getApplication(), uri, password) }
            .onSuccess {
                _events.emit(UiEvent.Success("Restored ${it.tenants.size} tenants and ${it.payments.size} payments."))
                refresh()
            }
            .onFailure { emitError(it, "Restore failed. Your existing data is unchanged.") }
            .also { password.fill('\u0000') }
    }

    // ── Settings ──────────────────────────────────────────────────────

    suspend fun getSetting(key: String): String? = repo.getSetting(key)

    fun setSetting(key: String, value: String) = viewModelScope.launch {
        runCatching { repo.setSetting(key, value) }
            .onSuccess { _events.emit(UiEvent.Success("Saved.")) }
            .onFailure { emitError(it, "Could not save the setting.") }
    }

    // ──────────────────────────────────────────────
    // Import
    // ──────────────────────────────────────────────

    /**
     * Reads and validates a CSV without writing anything, so the user always sees what
     * would happen before it happens.
     */
    fun dryRunImport(uri: Uri, onDone: (ImportResult?) -> Unit) = viewModelScope.launch {
        runCatching {
            val text = getApplication<Application>().contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalArgumentException("The selected file could not be opened.")
            repo.validateImport(CsvImport.parse(text))
        }
            .onSuccess { onDone(it) }
            .onFailure { emitError(it, "The file could not be read as a CSV."); onDone(null) }
    }

    /**
     * Commits a previously validated result. The repository wraps this in a single
     * transaction, so a failure part-way through imports nothing at all.
     */
    fun commitImport(result: ImportResult, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        runCatching { repo.commitImport(result) }
            .onSuccess {
                _events.emit(
                    UiEvent.Success(
                        "Imported ${it.importedCount} payments. " +
                            "${it.reviewCount} need review, ${it.rejectedCount} rejected."
                    )
                )
                refresh()
                onDone(true)
            }
            .onFailure {
                emitError(it, "Import failed and nothing was saved.")
                onDone(false)
            }
    }

    fun resolveIssue(id: Long) = viewModelScope.launch {
        runCatching { repo.resolveIssue(id) }
            .onFailure { emitError(it, "Could not update the issue.") }
    }

    /** Surfaces the real reason. Never logs tenant names, amounts or contact details. */
    private suspend fun emitError(t: Throwable, fallback: String) {
        _events.emit(UiEvent.Error(t.message?.takeIf { it.isNotBlank() } ?: fallback))
    }
}
