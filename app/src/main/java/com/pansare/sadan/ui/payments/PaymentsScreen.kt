@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.payments

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.data.PaymentMode
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.ConfirmDialog
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

@Composable
fun PaymentsScreen(
    vm: AppViewModel,
    onEditPayment: (Long) -> Unit,
    onAddPayment: () -> Unit
) {
    val payments by vm.payments.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf<PaymentMode?>(null) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    val filtered = remember(payments, search, modeFilter) {
        val term = search.trim().lowercase()
        payments.filter { p ->
            (modeFilter == null || p.payment.paymentMode == modeFilter) &&
                (term.isBlank() ||
                    p.tenantName.lowercase().contains(term) ||
                    p.displayRoomNumber.lowercase().contains(term) ||
                    p.payment.receiptNumber.lowercase().contains(term) ||
                    p.payment.amountPaid.toString().contains(term))
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            label = { Text("Search room, tenant, receipt or amount") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = modeFilter == null,
                onClick = { modeFilter = null },
                label = { Text("All modes") }
            )
            PaymentMode.entries.forEach { m ->
                FilterChip(
                    selected = modeFilter == m,
                    onClick = { modeFilter = if (modeFilter == m) null else m },
                    label = { Text(m.label) }
                )
            }
        }

        if (filtered.isEmpty()) {
            EmptyState(
                title = if (payments.isEmpty()) "No payments recorded yet" else "No matching payments",
                message = if (payments.isEmpty()) {
                    "When you receive rent, record it here and the ledger updates automatically."
                } else "Try a different search term or clear the filters.",
                icon = Icons.Outlined.Payments,
                actionLabel = if (payments.isEmpty()) "Record a payment" else "Clear filters",
                onAction = {
                    if (payments.isEmpty()) onAddPayment() else { search = ""; modeFilter = null }
                }
            )
            return
        }

        val total = filtered.sumOf { it.payment.amountPaid }
        Text(
            "${filtered.size} payment(s) · ${CurrencyUtils.format(total)} total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.payment.id }) { row ->
                val p = row.payment
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${row.displayRoomNumber} · ${row.tenantName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${DateUtils.formatDate(p.paymentDate)} · ${p.paymentMode.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                CurrencyUtils.format(p.amountPaid),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Covers ${DateUtils.formatMonth(p.paidFromMonth)} to " +
                                DateUtils.formatMonth(p.paidToMonth) +
                                if (p.receiptNumber.isNotBlank()) " · Receipt ${p.receiptNumber}" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (p.notes.isNotBlank()) {
                            Text(
                                p.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onEditPayment(p.id) }) { Text("Edit") }
                            TextButton(onClick = { vm.shareReceipt(p.id) }) { Text("Share receipt") }
                            TextButton(
                                onClick = { pendingDelete = p.id }
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        val row = payments.firstOrNull { it.payment.id == id }
        ConfirmDialog(
            title = "Delete this payment?",
            message = "The allocations will be reversed and the affected months will return to " +
                "their previous balances. This cannot be undone." +
                (row?.let { "\n\n${CurrencyUtils.format(it.payment.amountPaid)} from ${it.tenantName}." } ?: ""),
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deletePayment(id) },
            onDismiss = { pendingDelete = null }
        )
    }
}
