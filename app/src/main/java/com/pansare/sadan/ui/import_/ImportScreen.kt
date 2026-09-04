@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.import_

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pansare.sadan.domain.ImportIssue
import com.pansare.sadan.domain.ImportResult
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.ConfirmDialog
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.ui.components.SectionHeader
import com.pansare.sadan.ui.components.UnresolvedNotice
import com.pansare.sadan.util.CurrencyUtils

/**
 * Two-stage CSV import: pick a file, read the dry-run verdict, then decide.
 *
 * Nothing is written until the user presses Import, and every input row is accounted for as
 * imported, needs-review or rejected — there are no silent skips.
 */
@Composable
fun ImportScreen(vm: AppViewModel, onBack: () -> Unit, onViewIssues: () -> Unit) {
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        result = null
        fileName = uri.lastPathSegment?.substringAfterLast('/')
        vm.dryRunImport(uri) { outcome ->
            result = outcome
            busy = false
        }
    }

    if (confirming) {
        val current = result
        ConfirmDialog(
            title = "Import ${current?.importedCount ?: 0} payments?",
            message = "Only the valid rows are imported. Rows needing review and rejected " +
                "rows are recorded as issues instead, so nothing is lost. " +
                "If any row fails while saving, the whole import is rolled back.",
            confirmLabel = "Import",
            onConfirm = {
                confirming = false
                if (current != null) {
                    busy = true
                    vm.commitImport(current) { ok ->
                        busy = false
                        if (ok) result = null
                    }
                }
            },
            onDismiss = { confirming = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import payments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val current = result
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Choose an Excel (.xlsx) or CSV file",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Supports Excel workbooks with multiple sheets (e.g. 'B Wing') and CSV files. " +
                                "Recognised columns: Room/Roman, Tenant Name, Rent, Receipt no. & Date, Unpaid Rent/Months. " +
                                "The file is validated first and nothing is saved until you confirm.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                picker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.UploadFile, contentDescription = null)
                            Spacer(Modifier.height(0.dp))
                            Text("  Select file")
                        }
                        fileName?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("Selected: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (busy) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            }

            if (current != null) {
                item {
                    val total = current.importedCount + current.reviewCount + current.rejectedCount
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Check before importing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            CountRow("Rows read", total)
                            CountRow("Ready to import", current.importedCount)
                            CountRow("Need review", current.reviewCount)
                            CountRow("Rejected", current.rejectedCount)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Every row is accounted for: " +
                                    "${current.importedCount} + ${current.reviewCount} + " +
                                    "${current.rejectedCount} = $total.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (current.valid.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Total value of importable rows: " +
                                        CurrencyUtils.format(current.valid.sumOf { it.amount }),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { confirming = true },
                                    enabled = !busy && current.importedCount > 0,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text("Import ${current.importedCount} rows") }
                                OutlinedButton(
                                    onClick = { result = null; fileName = null },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text("Discard") }
                            }
                            if (current.importedCount == 0) {
                                Spacer(Modifier.height(8.dp))
                                UnresolvedNotice(
                                    "No row in this file can be imported as it stands. " +
                                        "Fix the reasons listed below and try again."
                                )
                            }
                        }
                    }
                }

                if (current.review.isNotEmpty()) {
                    item { SectionHeader("Needs review (${current.review.size})") }
                    items(current.review, key = { "r${it.rowNumber}${it.kind}" }) {
                        IssueRow(it, "This row is not imported until it is corrected.")
                    }
                }

                if (current.rejected.isNotEmpty()) {
                    item { SectionHeader("Rejected (${current.rejected.size})") }
                    items(current.rejected, key = { "x${it.rowNumber}${it.kind}" }) {
                        IssueRow(it, "This row cannot be imported.")
                    }
                }

                if (current.review.isNotEmpty() || current.rejected.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = onViewIssues,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Open the issues list") }
                    }
                }
            } else if (!busy) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.UploadFile,
                        title = "No file checked yet",
                        message = "Select a CSV file to see exactly what would be imported."
                    )
                }
            }
        }
    }
}

@Composable
private fun CountRow(label: String, value: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun IssueRow(issue: ImportIssue, consequence: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Row ${issue.rowNumber}" +
                    if (issue.reference.isNotBlank()) " — ${issue.reference}" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(issue.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "$consequence Reason code: ${issue.kind.name}.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
