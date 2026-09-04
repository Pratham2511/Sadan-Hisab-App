@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.ReportBuilder
import com.pansare.sadan.ui.components.DetailRow
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.ui.components.ErrorPanel
import com.pansare.sadan.ui.components.SectionHeader
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils
import java.time.LocalDate

private enum class Report(val title: String, val description: String) {
    MONTHLY("Monthly Collection", "Expected, collected and outstanding for one month"),
    YEARLY("Yearly Collection", "Twelve month totals and collection rate"),
    DEFAULTERS("Defaulter List", "Everyone with an outstanding balance"),
    OUTSTANDING("Outstanding Report", "Outstanding grouped by wing"),
    TENANT("Tenant Payment History", "Month-by-month history for one tenant"),
    STATUS("Status Summary", "How many rooms sit in each state")
}

@Composable
fun ReportsScreen(vm: AppViewModel, onOpenTenant: (Long) -> Unit) {
    var selected by remember { mutableStateOf<Report?>(null) }

    val current = selected
    if (current == null) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item { Text("Reports", style = MaterialTheme.typography.titleLarge) }
            items(Report.entries) { report ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { selected = report }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(report.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            report.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selected = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to report list")
            }
            Text(current.title, style = MaterialTheme.typography.titleMedium)
        }

        when (current) {
            Report.MONTHLY -> MonthlyReport(vm)
            Report.YEARLY -> YearlyReport(vm)
            Report.DEFAULTERS -> DefaulterReport(vm, onOpenTenant)
            Report.OUTSTANDING -> OutstandingReport(vm)
            Report.TENANT -> TenantHistoryReport(vm)
            Report.STATUS -> StatusSummary(vm)
        }
    }
}

@Composable
private fun MonthlyReport(vm: AppViewModel) {
    var month by remember { mutableStateOf(MonthKey.current()) }
    val result by produceState<Result<ReportBuilder.MonthlyCollection>?>(null, month) {
        value = if (MonthKey.isValid(month)) vm.monthlyReport(month) else null
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = month,
            onValueChange = { month = it },
            label = { Text("Month") },
            placeholder = { Text("yyyy-MM") },
            isError = !MonthKey.isValid(month),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        result?.fold(
            onSuccess = { r ->
                if (!r.hasData) {
                    EmptyState(
                        title = "Not enough data",
                        message = "No rent was due or collected in ${DateUtils.formatMonth(r.month)}.",
                        icon = Icons.Outlined.Assessment
                    )
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            DetailRow("Expected", CurrencyUtils.format(r.expected))
                            DetailRow("Collected", CurrencyUtils.format(r.collected), emphasise = true)
                            DetailRow("Outstanding", CurrencyUtils.format(r.outstanding))
                            DetailRow("Collection rate", "${r.collectionRate}%")
                            Spacer(Modifier.height(8.dp))
                            DetailRow("Months paid", r.paidCount.toString())
                            DetailRow("Partly paid", r.partialCount.toString())
                            DetailRow("Unpaid", r.unpaidCount.toString())
                        }
                    }
                }
            },
            onFailure = { ErrorPanel(it.message ?: "Could not build this report.") }
        )
    }
}

@Composable
private fun YearlyReport(vm: AppViewModel) {
    var year by remember { mutableStateOf(LocalDate.now().year.toString()) }
    val parsed = year.toIntOrNull()
    val result by produceState<Result<ReportBuilder.YearlyCollection>?>(null, year) {
        value = parsed?.let { vm.yearlyReport(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = year,
            onValueChange = { year = it.filter(Char::isDigit).take(4) },
            label = { Text("Year") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        result?.fold(
            onSuccess = { r ->
                if (!r.hasData) {
                    EmptyState(
                        title = "Not enough data",
                        message = "Nothing was due or collected in ${r.year}.",
                        icon = Icons.Outlined.Assessment
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    DetailRow("Expected", CurrencyUtils.format(r.expected))
                                    DetailRow("Collected", CurrencyUtils.format(r.collected), emphasise = true)
                                    DetailRow("Outstanding", CurrencyUtils.format(r.outstanding))
                                    DetailRow("Collection rate", "${r.collectionRate}%")
                                }
                            }
                        }
                        items(r.months.filter { it.hasData }) { m ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp)) {
                                    Text(
                                        DateUtils.formatMonth(m.month),
                                        Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text("${CurrencyUtils.format(m.collected)} of ${CurrencyUtils.format(m.expected)}")
                                }
                            }
                        }
                    }
                }
            },
            onFailure = { ErrorPanel(it.message ?: "Could not build this report.") }
        )
    }
}

@Composable
private fun DefaulterReport(vm: AppViewModel, onOpenTenant: (Long) -> Unit) {
    val defaulters by vm.defaulters.collectAsStateWithLifecycle()

    if (defaulters.isEmpty()) {
        EmptyState(
            title = "No outstanding tenants",
            message = "Every occupied room is fully settled.",
            icon = Icons.Outlined.Assessment
        )
        return
    }

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("${defaulters.size} tenant(s) owing", fontWeight = FontWeight.SemiBold)
                    Text(
                        CurrencyUtils.format(defaulters.sumOf { it.outstanding }),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        items(defaulters, key = { it.roomId }) { d ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { d.tenantId?.let(onOpenTenant) }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row {
                        Text(
                            "${d.displayRoomNumber} · ${d.tenantName}",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            CurrencyUtils.format(d.outstanding),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${d.unpaidMonths} unpaid, ${d.partialMonths} partly paid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OutstandingReport(vm: AppViewModel) {
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val owing = rooms.filter { it.outstanding > 0 }

    if (owing.isEmpty()) {
        EmptyState(
            title = "Nothing outstanding",
            message = "There is no outstanding rent to report.",
            icon = Icons.Outlined.Assessment
        )
        return
    }

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        listOf("A", "B").forEach { wing ->
            val wingRooms = owing.filter { it.wing == wing }
            if (wingRooms.isNotEmpty()) {
                item {
                    SectionHeader(
                        "$wing Wing — ${CurrencyUtils.format(wingRooms.sumOf { it.outstanding })}"
                    )
                }
                items(wingRooms, key = { it.roomId }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp)) {
                            Text(
                                "${r.displayRoomNumber} · ${r.tenantName}",
                                Modifier.weight(1f)
                            )
                            Text(
                                CurrencyUtils.format(r.outstanding),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TenantHistoryReport(vm: AppViewModel) {
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val occupied = rooms.filter { it.isOccupied }
    var tenantId by remember { mutableStateOf(0L) }

    if (occupied.isEmpty()) {
        EmptyState(
            title = "No tenants yet",
            message = "Add a tenant before running a payment history report.",
            icon = Icons.Outlined.Assessment
        )
        return
    }

    val history by produceState<Result<ReportBuilder.TenantHistory>?>(null, tenantId) {
        value = if (tenantId != 0L) vm.tenantHistory(tenantId) else null
    }

    LazyColumn(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (tenantId == 0L) {
            item { SectionHeader("Choose a tenant") }
            items(occupied, key = { it.roomId }) { r ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { tenantId = r.tenantId ?: 0L }) {
                    Text(
                        "${r.displayRoomNumber} · ${r.tenantName}",
                        Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            history?.fold(
                onSuccess = { h ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "${h.roomNumber} · ${h.tenantName}",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                DetailRow("Total due", CurrencyUtils.format(h.totalDue))
                                DetailRow("Total paid", CurrencyUtils.format(h.totalPaid))
                                DetailRow("Outstanding", CurrencyUtils.format(h.totalOutstanding), emphasise = true)
                            }
                        }
                    }
                    items(h.rows) { row ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp)) {
                                Text(DateUtils.formatMonth(row.month), Modifier.weight(1f))
                                Text(
                                    "${CurrencyUtils.format(row.paid)} / ${CurrencyUtils.format(row.rentDue)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                onFailure = { item { ErrorPanel(it.message ?: "Could not build this report.") } }
            )
        }
    }
}

@Composable
private fun StatusSummary(vm: AppViewModel) {
    val state by vm.dashboard.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("Total rooms", state.totalRooms.toString())
                DetailRow("Occupied", state.occupiedRooms.toString())
                DetailRow("Vacant", state.vacantRooms.toString())
                Spacer(Modifier.height(8.dp))
                DetailRow("Regular tenants", state.regularTenants.toString())
                DetailRow("Partly paid", state.partiallyPaidTenants.toString())
                DetailRow("Defaulters", state.defaulters.toString())
                Spacer(Modifier.height(8.dp))
                DetailRow("Unpaid months", state.unpaidMonths.toString())
                DetailRow("Partly paid months", state.partialMonths.toString())
                DetailRow("Total outstanding", CurrencyUtils.format(state.totalOutstanding), emphasise = true)
            }
        }
    }
}
