package com.pansare.sadan.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.pansare.sadan.data.TenantStatus
import com.pansare.sadan.domain.ImportIssue
import com.pansare.sadan.domain.ImportResult
import com.pansare.sadan.domain.IssueKind
import kotlinx.coroutines.launch

/**
 * Adds database-dependent checks after file parsing. In particular, a legacy payment cannot
 * be marked "ready" when its room is still vacant; that used to fail only after Confirm.
 */
fun AppViewModel.dryRunImportWithTenantCheck(
    uri: Uri,
    sheetName: String? = null,
    onDone: (ImportResult?) -> Unit
) {
    dryRunImport(uri, sheetName) { parsed ->
        if (parsed == null) {
            onDone(null)
            return@dryRunImport
        }
        viewModelScope.launch {
            val rooms = repo.getAllRooms().associateBy { it.id }
            val occupiedDisplays = repo.getAllTenants()
                .asSequence()
                .filter { it.status == TenantStatus.ACTIVE }
                .mapNotNull { rooms[it.roomId]?.displayRoomNumber?.uppercase() }
                .toSet()

            val stillValid = mutableListOf<com.pansare.sadan.domain.ValidatedPaymentRow>()
            val tenantIssues = mutableListOf<ImportIssue>()
            parsed.valid.forEach { row ->
                if (row.roomDisplay.uppercase() in occupiedDisplays) {
                    stillValid += row
                } else {
                    tenantIssues += ImportIssue(
                        kind = IssueKind.MISSING_REQUIRED_FIELD,
                        message = "Room ${row.roomDisplay} has no active tenant. Add or select the current tenant before importing this payment.",
                        rowNumber = row.rowNumber,
                        reference = if (row.receiptNumber.isBlank()) row.roomDisplay else "${row.roomDisplay} · receipt ${row.receiptNumber}"
                    )
                }
            }

            onDone(
                parsed.copy(
                    valid = stillValid,
                    review = parsed.review + tenantIssues
                )
            )
        }
    }
}
