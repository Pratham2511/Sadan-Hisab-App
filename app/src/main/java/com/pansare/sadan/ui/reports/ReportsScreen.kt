@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.data.RentRepository.MonthlyCollectionReport
import com.pansare.sadan.data.RentRepository.YearlyReport
import java.util.Calendar

@Composable
fun ReportsScreen(vm: AppViewModel) {
    var selectedReport by remember { mutableStateOf<String?>(null) }

    if (selectedReport == null) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Reports", style = MaterialTheme.typography.titleLarge) }
            val reports = listOf(
                "Monthly Collection Report",
                "Yearly Collection Report",
                "Defaulter List"
            )

            reports.forEach { reportName ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedReport = reportName }
                    ) {
                        Text(reportName, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = { selectedReport = null }) {
                    Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(selectedReport!!, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            when (selectedReport) {
                "Monthly Collection Report" -> MonthlyReportView(vm)
                "Yearly Collection Report" -> YearlyReportView(vm)
                "Defaulter List" -> DefaulterListView(vm)
            }
        }
    }
}

@Composable
fun MonthlyReportView(vm: AppViewModel) {
    var month by remember { mutableStateOf(MonthKey.current()) }
    val report by produceState<MonthlyCollectionReport?>(initialValue = null, month) {
        value = vm.monthlyCollectionReport(month)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = month,
            onValueChange = { month = it },
            label = { Text("Month (yyyy-MM)") },
            modifier = Modifier.fillMaxWidth()
        )

        report?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Expected Rent: ${CurrencyUtils.format(r.expectedRent)}")
                    Text("Total Collected: ${CurrencyUtils.format(r.collected)}", color = MaterialTheme.colorScheme.primary)
                    Text("Outstanding Balance: ${CurrencyUtils.format(r.outstanding)}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun YearlyReportView(vm: AppViewModel) {
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    val report by produceState<YearlyReport?>(initialValue = null, year) {
        val y = year.toIntOrNull()
        if (y != null) value = vm.yearlyReport(y)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = year,
            onValueChange = { year = it },
            label = { Text("Year (yyyy)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        report?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total Collected in $year: ${CurrencyUtils.format(r.totalCollected)}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun DefaulterListView(vm: AppViewModel) {
    val dashboardData by vm.dashboardData.collectAsState()
    val defaulters = dashboardData.filter { it.outstanding > 0 }.sortedByDescending { it.outstanding }
    
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Total Defaulters: ${defaulters.size}")
            Text("Total Outstanding: ${CurrencyUtils.format(defaulters.sumOf { it.outstanding })}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        items(defaulters) { d ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${d.displayRoomNumber} - ${d.tenantName}", fontWeight = FontWeight.Bold)
                        Text("Unpaid: ${d.unpaidMonths} months", style = MaterialTheme.typography.bodySmall)
                        if (d.outstandingSince != null) {
                            Text("Since: ${d.outstandingSince}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(CurrencyUtils.format(d.outstanding), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
