@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.tenants

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pansare.sadan.data.TenantEntity
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.util.CurrencyUtils

@Composable
fun TenantProfileScreen(vm: AppViewModel, tenantId: Long, navController: NavController) {
    var tenant by remember { mutableStateOf<TenantEntity?>(null) }
    var outstanding by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(tenantId) {
        tenant = vm.findTenant(tenantId)
        outstanding = vm.outstanding(tenantId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tenant Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("edit_tenant/$tenantId") }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { padding ->
        val t = tenant ?: return@Scaffold Box(Modifier.fillMaxSize()) { CircularProgressIndicator() }
        
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(t.tenantName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Mobile: ${t.mobileNumber.ifEmpty { "N/A" }}")
                    Text("Rent: ${CurrencyUtils.format(t.monthlyRent)}")
                    Text("Occupancy Start: ${t.occupancyStartMonth ?: "Unknown"}")
                    if (t.remarks.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Remarks: ${t.remarks}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Total Outstanding: ${CurrencyUtils.format(outstanding)}",
                        color = if (outstanding > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Button(
                onClick = { navController.navigate("record_payment/${tenantId}") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Record Payment")
            }

            OutlinedButton(
                onClick = { navController.navigate("ledger/${tenantId}") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Ledger")
            }
        }
    }
}
