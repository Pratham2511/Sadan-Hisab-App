package com.pansare.sadan.ui.rooms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.TenantStatusChip
import com.pansare.sadan.util.CurrencyUtils

@Composable
fun RoomsScreen(vm: AppViewModel, navController: NavController) {
    val dashboardData by vm.dashboardData.collectAsState()
    var selectedWing by remember { mutableStateOf("A") }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (selectedWing == "A") 0 else 1) {
            Tab(selected = selectedWing == "A", onClick = { selectedWing = "A" }, text = { Text("A Wing") })
            Tab(selected = selectedWing == "B", onClick = { selectedWing = "B" }, text = { Text("B Wing") })
        }

        val wingData = dashboardData.filter { it.wing == selectedWing }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(wingData, key = { it.tenantId }) { t ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("tenant_profile/${t.tenantId}") }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.displayRoomNumber, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(t.tenantName, fontWeight = FontWeight.Medium)
                            Text("Rent: ${CurrencyUtils.format(t.monthlyRent)}", style = MaterialTheme.typography.bodySmall)
                            if (t.outstanding > 0) {
                                Text("Outstanding: ${CurrencyUtils.format(t.outstanding)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TenantStatusChip(t.status)
                    }
                }
            }
        }
    }
}
