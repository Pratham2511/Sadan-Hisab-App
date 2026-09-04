@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.ledger

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.data.LedgerStatus
import com.pansare.sadan.domain.RentCertainty
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.ui.components.LedgerStatusPill
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

/**
 * Month-by-month ledger. Each month shows what was due, what was paid, the resulting
 * status and — where money was received — the receipts that settled it, so any figure
 * can be traced back to a real transaction.
 */
@Composable
fun LedgerScreen(
    vm: AppViewModel,
    tenantId: Long,
    onBack: () -> Unit
) {
    val rows by vm.repo.observeLedger(tenantId).collectAsStateWithLifecycle(emptyList())
    val allocations by vm.repo.observeAllocationDetails(tenantId).collectAsStateWithLifecycle(emptyList())
    val tenant by vm.repo.observeTenant(tenantId).collectAsStateWithLifecycle(null)

    val byMonth = allocations.groupBy { it.ledgerMonthId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ledger")
                        tenant?.let {
                            Text(
                                it.tenantName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { pad ->
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(
                    title = "No ledger months yet",
                    message = "The ledger is created from the tenant's occupancy start month. " +
                        "Check the occupancy start date on the tenant's profile.",
                    icon = Icons.Outlined.ReceiptLong
                )
            }
            return@Scaffold
        }

        val totalDue = rows.sumOf { it.rentDue }
        val totalPaid = rows.sumOf { it.totalPaid }
        val totalOutstanding = rows.sumOf { it.balance }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Total due", style = MaterialTheme.typography.labelMedium)
                            Text(CurrencyUtils.format(totalDue), fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Total paid", style = MaterialTheme.typography.labelMedium)
                            Text(CurrencyUtils.format(totalPaid), fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Outstanding", style = MaterialTheme.typography.labelMedium)
                            Text(
                                CurrencyUtils.format(totalOutstanding),
                                fontWeight = FontWeight.Bold,
                                color = if (totalOutstanding > 0) {
                                    MaterialTheme.colorScheme.error
                                } else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            items(rows, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                DateUtils.formatMonth(row.month),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            LedgerStatusPill(row.status)
                        }
                        Spacer(Modifier.height(6.dp))

                        if (row.certainty == RentCertainty.UNRESOLVED) {
                            Text(
                                "Rent for this month is unknown. No amount has been assumed, " +
                                    "so this month is excluded from firm totals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Text(
                                "Due ${CurrencyUtils.format(row.rentDue)} · " +
                                    "Paid ${CurrencyUtils.format(row.totalPaid)}" +
                                    if (row.balance > 0) {
                                        " · Outstanding ${CurrencyUtils.format(row.balance)}"
                                    } else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (row.balance > 0) {
                                    MaterialTheme.colorScheme.error
                                } else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val settled = byMonth[row.id].orEmpty()
                        if (settled.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            settled.forEach { a ->
                                Text(
                                    "• ${CurrencyUtils.format(a.allocatedAmount)} on " +
                                        "${DateUtils.formatDate(a.paymentDate)}" +
                                        if (a.receiptNumber.isNotBlank()) " · receipt ${a.receiptNumber}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
