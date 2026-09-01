@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pansare.sadan.data.MonthlyLedgerEntity
import com.pansare.sadan.data.TenantEntity
import com.pansare.sadan.data.LedgerStatus
import com.pansare.sadan.domain.LedgerEngine
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.TenantStatusChip
import com.pansare.sadan.util.CurrencyUtils
import kotlinx.coroutines.flow.first

@Composable
fun LedgerScreen(vm: AppViewModel, tenantId: Long, navController: NavController) {
    var tenant by remember { mutableStateOf<TenantEntity?>(null) }
    val ledger by vm.observeLedger(tenantId).collectAsState(initial = emptyList())
    var selectedMonth by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(tenantId) {
        tenant = vm.findTenant(tenantId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tenant Ledger") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        val t = tenant ?: return@Scaffold
        
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Ledger for ${t.tenantName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ledger, key = { it.id }) { monthEntry ->
                    LedgerRow(monthEntry) {
                        selectedMonth = monthEntry.month
                    }
                }
            }
        }

        selectedMonth?.let { month ->
            MonthDetailsDialog(vm, tenantId, month) {
                selectedMonth = null
            }
        }
    }
}

@Composable
fun LedgerRow(entry: MonthlyLedgerEntity, onClick: () -> Unit) {
    val status = entry.status
    val color = when (status) {
        LedgerStatus.PAID -> MaterialTheme.colorScheme.primaryContainer
        LedgerStatus.PARTIALLY_PAID -> MaterialTheme.colorScheme.tertiaryContainer
        LedgerStatus.UNPAID -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column {
                Text(entry.month, fontWeight = FontWeight.Bold)
                Text("Rent: ${CurrencyUtils.format(entry.applicableRent)}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(status.name, style = MaterialTheme.typography.bodySmall)
                if (status != LedgerStatus.PAID) {
                    Text("Bal: ${CurrencyUtils.format(entry.balance)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun MonthDetailsDialog(vm: AppViewModel, tenantId: Long, month: String, onDismiss: () -> Unit) {
    var allocations by remember { mutableStateOf<List<com.pansare.sadan.data.PaymentAllocationEntity>>(emptyList()) }
    var payments by remember { mutableStateOf<Map<Long, com.pansare.sadan.data.PaymentEntity>>(emptyMap()) }

    LaunchedEffect(tenantId, month) {
        val ledgerEntry = vm.repo.getDatabase().ledgerDao().observeForTenant(tenantId).first().find { it.month == month }
        if (ledgerEntry != null) {
            allocations = vm.repo.getDatabase().allocationDao().getAll().filter { it.ledgerMonthId == ledgerEntry.id }
            val pIds = allocations.map { it.paymentId }.distinct()
            val loadedPayments = pIds.mapNotNull { vm.repo.getDatabase().paymentDao().find(it) }
            payments = loadedPayments.associateBy { it.id }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details for $month") },
        text = {
            Column {
                if (allocations.isEmpty()) {
                    Text("No payments allocated to this month.")
                } else {
                    allocations.forEach { alloc ->
                        val p = payments[alloc.paymentId]
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Text("Receipt: ${p?.receiptNumber ?: "Unknown"}", fontWeight = FontWeight.Bold)
                                Text("Allocated: ${CurrencyUtils.format(alloc.allocatedAmount)}")
                                Text("Date: ${p?.paymentDate?.let { com.pansare.sadan.util.DateUtils.formatDate(it) } ?: "Unknown"}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
