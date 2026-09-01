package com.pansare.sadan.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

@Composable
fun PaymentsScreen(vm: AppViewModel) {
    val payments by vm.payments.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Payment History", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }

        if (payments.isEmpty()) {
            item {
                Text("No payments recorded.")
            }
        }

        items(payments, key = { it.payment.id }) { p ->
            var showConfirmDialog by remember { mutableStateOf(false) }

            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text("Delete Payment?") },
                    text = { Text("Are you sure you want to delete payment ${p.payment.receiptNumber}? Allocations will be reversed.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deletePayment(p.payment.id)
                            showConfirmDialog = false
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
                    }
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${p.displayRoomNumber} · ${p.tenantName}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Amount: ${CurrencyUtils.format(p.payment.amountPaid)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Period: ${DateUtils.formatMonth(p.payment.paidFromMonth)} to ${DateUtils.formatMonth(p.payment.paidToMonth)}", style = MaterialTheme.typography.bodySmall)
                        Text("Receipt: ${p.payment.receiptNumber} | Date: ${DateUtils.formatDate(p.payment.paymentDate)}", style = MaterialTheme.typography.bodySmall)
                        Text("Mode: ${p.payment.paymentMode.name}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { showConfirmDialog = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
