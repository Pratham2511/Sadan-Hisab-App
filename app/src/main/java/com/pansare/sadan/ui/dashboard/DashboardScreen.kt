package com.pansare.sadan.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.MetricCard
import com.pansare.sadan.util.CurrencyUtils

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val dashboardData by vm.dashboardData.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val asOfDate by vm.asOfDate.collectAsState()

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val totalRooms = dashboardData.size
    val aWingCount = dashboardData.count { it.wing == "A" }
    val bWingCount = dashboardData.count { it.wing == "B" }
    val totalOutstanding = dashboardData.sumOf { it.outstanding }
    val totalUnpaidMonths = dashboardData.sumOf { it.unpaidMonths }
    val totalPartialMonths = dashboardData.sumOf { it.partiallyPaidMonths }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Overview as of $asOfDate", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Total Outstanding", CurrencyUtils.format(totalOutstanding), Modifier.weight(1f), MaterialTheme.colorScheme.error)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Unpaid Months", totalUnpaidMonths.toString(), Modifier.weight(1f))
                MetricCard("Partially Paid", totalPartialMonths.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text("Properties", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Total Rooms", totalRooms.toString(), Modifier.weight(1f))
                MetricCard("A Wing", aWingCount.toString(), Modifier.weight(1f))
                MetricCard("B Wing", bWingCount.toString(), Modifier.weight(1f))
            }
        }
    }
}
