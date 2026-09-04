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
import com.pansare.sadan.data.IssueStatus
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
import com.pansare.sadan.util.XlsxImporter
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(RoomFilter.ALL)
    val filter: StateFlow<RoomFilter> = _filter.asStateFlow()

    val payments: StateFlow<List<PaymentWithTenantRow>> = repo.observePayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val issues: StateFlow<List<ImportValidationIssueEntity>> = repo.observeValidationIssues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Single reactive state flow for room cards. Computed safely on Dispatchers.IO
     * whenever database rooms/tenants change or asOf month is updated.
     */
    val rooms: StateFlow<List<RoomCardState>> = combine(
        repo.observeRoomsWithTenants(),
        _asOf
    ) { rows, asOf ->
        withContext(Dispatchers.IO) {
            rows.map { row ->
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Dashboard totals derived directly from rooms and issues. Guarantees 100% metric consistency.
     */
    val dashboard: StateFlow<DashboardState> = combine(rooms, issues) { roomList, issueList ->
        val occupied = roomList.count { it.isOccupied }
        DashboardState(
            totalRooms = RoomInventory.TOTAL_ROOMS,
            occupiedRooms = occupied,
            vacantRooms = RoomInventory.TOTAL_ROOMS - occupied,
            totalTenants = occupied,
            regularTenants = roomList.count { it.isOccupied && it.standing == "Regular" },
            defaulters = roomList.count { it.standing == "Defaulter" },
            partiallyPaidTenants = roomList.count { it.standing == "Partially Paid" },
            unpaidMonths = roomList.sumOf { it.unpaidMonths },
            partialMonths = roomList.sumOf { it.partialMonths },
            totalOutstanding = roomList.sumOf { it.outstanding },
            unresolvedOutstanding = roomList.filter { it.hasUnresolvedHistory }.sumOf { it.outstanding },
            openIssues = issueList.count { it.status == IssueStatus.OPEN },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    /** Rooms after search and filter are applied. Case-insensitive across room, name, mobile. */
    val visibleRooms: StateFlow<List<RoomCardState>> =
        combine(rooms, _query, _filter) { list, q, f ->
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

    val defaulters: StateFlow<List<RoomCardState>> = rooms
        .map { list -> list.filter { it.isOccupied && it.outstanding > 0 }.sortedByDescending { it.outstanding } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { repo.initialiseIfEmpty() }
                .onFailure { emitError(it, "Could not prepare the room inventory.") }
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setFilter(value: RoomFilter) { _filter.value = value }

    fun setAsOf(month: String) {
        if (MonthKey.isValid(month)) {
            _asOf.value = month
        }
    }

    fun refresh() {
        _asOf.value = _asOf.value
    }

    // ──────────────────────────────────────────────
    // Rooms & tenants
    // ──────────────────────────────────────────────

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
                onDone(true)
            }
            .onFailure { emitError(it, "Tenant could not be added."); onDone(false) }
    }

    fun updateTenant(
        tenant: TenantEntity,
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching { repo.updateTenant(tenant) }
            .onSuccess {
                _events.emit(UiEvent.Success("Tenant updated."))
                onDone(true)
            }
            .onFailure { emitError(it, "Could not update the tenant."); onDone(false) }
    }

    fun changeRent(
        tenantId: Long,
        newRent: Long,
        effectiveFrom: String,
        note: String = "",
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching { repo.changeRent(tenantId, newRent, effectiveFrom, note) }
            .onSuccess {
                _events.emit(UiEvent.Success("Rent updated."))
                onDone(true)
            }
            .onFailure { emitError(it, "Could not change the rent."); onDone(false) }
    }

    fun moveOutTenant(
        tenantId: Long,
        endMonth: String,
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching { repo.moveOutTenant(tenantId, endMonth) }
            .onSuccess {
                _events.emit(UiEvent.Success("Tenancy ended."))
                onDone(true)
            }
            .onFailure { emitError(it, "Could not process move-out."); onDone(false) }
    }

    // ──────────────────────────────────────────────
    // Payments
    // ──────────────────────────────────────────────

    suspend fun previewPayment(
        tenantId: Long,
        from: String,
        to: String,
        amount: Long,
        excludePaymentId: Long = 0L
    ): Result<AllocationPlan> = runCatching {
        repo.previewPayment(tenantId, from, to, amount, excludePaymentId)
    }

    fun recordPayment(
        tenantId: Long,
        paymentDate: Long,
        amount: Long,
        mode: PaymentMode,
        from: String,
        to: String,
        receiptNumber: String,
        notes: String = "",
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching {
            repo.recordPayment(tenantId, paymentDate, amount, mode, from, to, receiptNumber, notes)
        }
            .onSuccess {
                _events.emit(UiEvent.Success("Payment recorded."))
                onDone(true)
            }
            .onFailure { emitError(it, "Payment could not be saved."); onDone(false) }
    }

    fun editPayment(
        paymentId: Long,
        paymentDate: Long,
        amount: Long,
        mode: PaymentMode,
        from: String,
        to: String,
        receiptNumber: String,
        notes: String = "",
        onDone: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching {
            repo.editPayment(paymentId, paymentDate, amount, mode, from, to, receiptNumber, notes)
        }
            .onSuccess {
                _events.emit(UiEvent.Success("Payment updated."))
                onDone(true)
            }
            .onFailure { emitError(it, "Payment edit failed."); onDone(false) }
    }

    fun deletePayment(paymentId: Long, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        runCatching { repo.deletePayment(paymentId) }
            .onSuccess {
                _events.emit(UiEvent.Success("Payment deleted and ledger reversed."))
                onDone(true)
            }
            .onFailure { emitError(it, "Could not delete the payment."); onDone(false) }
    }

    suspend fun nextReceiptNumber(): String = repo.nextReceiptNumber()

    // ──────────────────────────────────────────────
    // Receipts
    // ──────────────────────────────────────────────

    private suspend fun buildReceipt(paymentId: Long): Pair<File, String> {
        val payment = repo.findPayment(paymentId) ?: error("Payment not found")
        val tenant = repo.findTenant(payment.tenantId) ?: error("Tenant not found")
        val room = repo.findRoom(tenant.roomId) ?: error("Room not found")
        val allocs = repo.allocationsForPayment(paymentId)
        val ledger = repo.observeLedger(tenant.id).map { list -> list.associateBy { it.id } }
        val summary = repo.summaryFor(tenant.id)

        val propName = repo.getSetting(RentRepository.KEY_PROPERTY_NAME)
            ?: RentRepository.DEFAULT_PROPERTY_NAME
        val propAddr = repo.getSetting(RentRepository.KEY_PROPERTY_ADDRESS)
            ?: RentRepository.DEFAULT_PROPERTY_ADDRESS

        val lines = allocs.map { a ->
            val lm = repo.database().ledgerDao().findById(a.ledgerMonthId)
            val monthStr = lm?.month ?: ""
            ReceiptLine(
                month = MonthKey.displayName(monthStr),
                rentDue = lm?.rentDue ?: 0L,
                allocated = a.allocatedAmount
            )
        }

        val destFile = java.io.File(getApplication<Application>().cacheDir, "receipt_${payment.receiptNumber.replace('/', '_')}.pdf")
        val file = ReceiptPdf.create(
            destFile,
            ReceiptData(
                propertyName = propName,
                propertyAddress = propAddr,
                receiptNumber = payment.receiptNumber,
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
     * Reads and validates a CSV or XLSX without writing anything, so the user always sees what
     * would happen before it happens.
     */
    fun dryRunImport(uri: Uri, sheetName: String? = null, onDone: (ImportResult?) -> Unit) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val cr = getApplication<Application>().contentResolver
                val name = uri.lastPathSegment?.lowercase() ?: ""
                val mime = cr.getType(uri)?.lowercase() ?: ""

                val isXlsx = name.endsWith(".xlsx") || name.endsWith(".xls") ||
                    mime.contains("spreadsheet") || mime.contains("excel") || mime.contains("openxmlformats")

                val rows = if (isXlsx) {
                    val stream = cr.openInputStream(uri) ?: error("Could not open file.")
                    val sheetData = XlsxImporter.parseSheet(stream, sheetName)
                    XlsxImporter.parseRowsFromMatrix(sheetData.rows)
                } else {
                    val text = cr.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open CSV file.")
                    CsvImport.parse(text)
                }
                repo.validateImport(rows)
            }
        }
            .onSuccess { onDone(it) }
            .onFailure { emitError(it, "The file could not be read."); onDone(null) }
    }

    /**
     * Lists all available sheet names in an XLSX file.
     */
    fun listXlsxSheets(uri: Uri, onDone: (List<String>) -> Unit) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: error("Could not open file.")
                XlsxImporter.listSheets(stream)
            }
        }
            .onSuccess { onDone(it) }
            .onFailure { onDone(emptyList()) }
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
