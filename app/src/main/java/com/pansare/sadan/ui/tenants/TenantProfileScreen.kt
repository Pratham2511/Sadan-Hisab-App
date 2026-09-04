@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.tenants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.domain.DefaulterSummary
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.DetailRow
import com.pansare.sadan.ui.components.LoadingState
import com.pansare.sadan.ui.components.SectionHeader
import com.pansare.sadan.ui.components.StandingPill
import com.pansare.sadan.ui.components.UnresolvedNotice
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

@Composable
fun TenantProfileScreen(
    vm: AppViewModel,
    tenantId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewLedger: () -> Unit
) {
    val tenant by vm.observeTenant(tenantId).collectAsStateWithLifecycle(null)
    val payments by vm.observePaymentsForTenant(tenantId).collectAsStateWithLifecycle(emptyList())
    var summary by remember { mutableStateOf<DefaulterSummary?>(null) }
    var roomNumber by remember { mutableStateOf("") }

    // Recompute whenever the payment list changes, so the profile never shows stale figures.
    LaunchedEffect(tenantId, payments) {
        summary = vm.summaryFor(tenantId)
        vm.findTenant(tenantId)?.let { t ->
            vm.repo.findRoom(t.roomId)?.let { roomNumber = it.displayRoomNumber }
        }
    }

    val t = tenant
    val s = summary
    if (t == null || s == null) {
        LoadingState(label = "Loading tenant")
        return
    }

    val standing = when {
        s.totalOutstanding <= 0L -> "Regular"
        s.partialMonths > 0 && s.unpaidMonths == 0 -> "Partially Paid"
        else -> "Defaulter"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tenant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit tenant")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.tenantName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Room $roomNumber",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        StandingPill(standing)
                    }
                    Spacer(Modifier.height(10.dp))
                    DetailRow("Mobile", t.mobileNumber.ifBlank { "Not recorded" })
                    DetailRow("Monthly rent", CurrencyUtils.format(t.monthlyRent))
                    DetailRow("Occupancy start", DateUtils.formatMonth(t.occupancyStartMonth))
                    DetailRow("Status", t.status.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() })
                    if (t.remarks.isNotBlank()) DetailRow("Remarks", t.remarks)
                }
            }

            if (s.hasUnresolvedHistory) {
                UnresolvedNotice(
                    "Some historical months have no known rent. Those months are shown separately " +
                        "and are not included in the firm outstanding figure."
                )
            }

            SectionHeader("Financial summary")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (s.totalOutstanding > 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total outstanding", style = MaterialTheme.typography.labelLarge)
                    Text(
                        CurrencyUtils.format(s.totalOutstanding),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    DetailRow("Unpaid months", s.unpaidMonths.toString())
                    DetailRow("Partly paid months", s.partialMonths.toString())
                    DetailRow("Outstanding since", s.outstandingSince?.let { DateUtils.formatMonth(it) } ?: "—")
                    DetailRow("Last month fully paid", s.lastPaidUpTo?.let { DateUtils.formatMonth(it) } ?: "—")
                    if (s.unresolvedOutstanding > 0) {
                        DetailRow(
                            "Of which unresolved",
                            CurrencyUtils.format(s.unresolvedOutstanding)
                        )
                    }
                }
            }

            if (s.unpaidPeriods.isNotEmpty()) {
                SectionHeader("Unpaid periods")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        // Gaps are listed as separate runs, never merged into one long span.
                        s.unpaidPeriods.forEach { p ->
                            Text(
                                if (p.fromMonth == p.toMonth) {
                                    "${DateUtils.formatMonth(p.fromMonth)} — ${CurrencyUtils.format(p.amount)}"
                                } else {
                                    "${DateUtils.formatMonth(p.fromMonth)} to ${DateUtils.formatMonth(p.toMonth)} " +
                                        "(${p.months} months) — ${CurrencyUtils.format(p.amount)}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            SectionHeader("Actions")
            Button(
                onClick = onRecordPayment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) { Text("Record payment") }

            OutlinedButton(
                onClick = onViewLedger,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) { Text("View ledger (${s.unpaidMonths + s.partialMonths} months owing)") }

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) { Text("Edit tenant") }

            if (payments.isNotEmpty()) {
                SectionHeader("Recent payments")
                payments.take(5).forEach { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    CurrencyUtils.format(p.payment.amountPaid),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${DateUtils.formatDate(p.payment.paymentDate)} · ${p.payment.paymentMode.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { vm.shareReceipt(p.payment.id) }) {
                                Text("Receipt")
                            }
                        }
                    }
                }
            }
        }
    }
}
