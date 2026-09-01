@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pansare.sadan.data.PaymentMode
import com.pansare.sadan.data.TenantEntity
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.util.CurrencyUtils
import kotlinx.coroutines.launch

@Composable
fun RecordPaymentScreen(vm: AppViewModel, tenantId: Long, navController: NavController) {
    var tenant by remember { mutableStateOf<TenantEntity?>(null) }
    var receiptNumber by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var fromMonth by remember { mutableStateOf("") }
    var toMonth by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var notes by remember { mutableStateOf("") }
    
    var showConfirmDialog by remember { mutableStateOf(false) }
    var outstanding by remember { mutableLongStateOf(0L) }
    
    val scope = rememberCoroutineScope()

    LaunchedEffect(tenantId) {
        tenant = vm.findTenant(tenantId)
        receiptNumber = vm.nextReceiptNumber()
        outstanding = vm.outstanding(tenantId)
        
        // Auto-fill from/to based on first unpaid month
        val firstUnpaid = vm.repo.outstandingSince(tenantId)
        if (firstUnpaid != null) {
            fromMonth = firstUnpaid
            toMonth = firstUnpaid
        }
    }

    val amount = amountText.toLongOrNull() ?: 0L
    val isOverpayment = outstanding > 0 && amount > outstanding

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Payment") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        val t = tenant ?: return@Scaffold
        
        if (showConfirmDialog) {
            val selectedLedger by produceState(
                initialValue = emptyList<com.pansare.sadan.data.MonthlyLedgerEntity>(),
                fromMonth, toMonth
            ) {
                value = vm.repo.getDatabase().ledgerDao().range(t.id, fromMonth, toMonth)
            }
            val allocations = remember(selectedLedger, amount) {
                runCatching { com.pansare.sadan.domain.PaymentAllocator.plan(selectedLedger, amount) }.getOrNull()
            }

            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Confirm Payment") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You are about to record a payment of ${CurrencyUtils.format(amount)}.")
                        if (allocations != null) {
                            Text("Allocation Preview:", fontWeight = FontWeight.Bold)
                            allocations.forEach { alloc ->
                                val month = selectedLedger.find { it.id == alloc.ledgerId }?.month ?: ""
                                Text("- $month: ${CurrencyUtils.format(alloc.amount)}", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("Invalid allocation. Please check amounts.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.addPayment(
                                tenantId = t.id,
                                from = fromMonth,
                                to = toMonth,
                                amount = amount,
                                receipt = receiptNumber,
                                mode = paymentMode,
                                notes = notes
                            )
                            showConfirmDialog = false
                            navController.popBackStack()
                        },
                        enabled = allocations != null
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }

        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Tenant: ${t.tenantName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Total Outstanding: ${CurrencyUtils.format(outstanding)}", color = MaterialTheme.colorScheme.error)

            OutlinedTextField(
                value = receiptNumber,
                onValueChange = { receiptNumber = it },
                label = { Text("Receipt Number") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = fromMonth,
                    onValueChange = { fromMonth = it },
                    label = { Text("From (yyyy-MM)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = toMonth,
                    onValueChange = { toMonth = it },
                    label = { Text("To (yyyy-MM)") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount Paid (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                isError = isOverpayment,
                supportingText = {
                    if (isOverpayment) Text("Amount exceeds total outstanding.")
                }
            )

            Text("Payment Mode", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = paymentMode == mode,
                        onClick = { paymentMode = mode },
                        label = { Text(mode.name) }
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = amount > 0 && !isOverpayment && fromMonth.isNotBlank() && toMonth.isNotBlank()
            ) {
                Text("Review and Save")
            }
        }
    }
}
