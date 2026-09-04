@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pansare.sadan.data.PaymentMode
import com.pansare.sadan.domain.AllocationPlan
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.DetailRow
import com.pansare.sadan.ui.components.ErrorPanel
import com.pansare.sadan.ui.components.SectionHeader
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

/**
 * Payment entry with a live allocation preview.
 *
 * Nothing is guessed: before saving, the user sees exactly which months the money will
 * settle, what each month owed beforehand, and what will remain outstanding afterwards.
 */
@Composable
fun RecordPaymentScreen(
    vm: AppViewModel,
    tenantId: Long,
    editPaymentId: Long = 0L,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val isEdit = editPaymentId != 0L

    var tenantName by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var from by remember { mutableStateOf(MonthKey.current()) }
    var to by remember { mutableStateOf(MonthKey.current()) }
    var notes by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    var plan by remember { mutableStateOf<AllocationPlan?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var actualTenantId by remember { mutableStateOf(tenantId) }

    // Load context: either a new payment for a tenant, or an existing payment being edited.
    LaunchedEffect(tenantId, editPaymentId) {
        if (isEdit) {
            vm.findPayment(editPaymentId)?.let { p ->
                actualTenantId = p.tenantId
                amount = p.amountPaid.toString()
                receipt = p.receiptNumber
                mode = p.paymentMode
                from = p.paidFromMonth
                to = p.paidToMonth
                notes = p.notes
                dateMillis = p.paymentDate
            }
        } else if (receipt.isBlank()) {
            receipt = vm.nextReceiptNumber()
        }
        vm.findTenant(actualTenantId)?.let { t ->
            tenantName = t.tenantName
            if (!isEdit && from < t.occupancyStartMonth) {
                from = t.occupancyStartMonth
                to = maxOf(t.occupancyStartMonth, to)
            }
            vm.repo.findRoom(t.roomId)?.let { roomNumber = it.displayRoomNumber }
        }
    }

    val amountValue = amount.trim().toLongOrNull()
    val amountError = when {
        amount.isBlank() -> "Enter the amount received."
        amountValue == null -> "Enter a whole number."
        amountValue <= 0 -> "The amount must be greater than zero."
        else -> null
    }
    val periodError = when {
        !MonthKey.isValid(from) || !MonthKey.isValid(to) -> "Use the format yyyy-MM."
        from > to -> "'Paid from' cannot be after 'paid to'."
        else -> null
    }

    // Recompute the preview whenever the inputs change. Read-only; writes nothing.
    LaunchedEffect(actualTenantId, from, to, amountValue, editPaymentId) {
        plan = null
        previewError = null
        if (amountError == null && periodError == null && actualTenantId != 0L && amountValue != null) {
            vm.previewPayment(actualTenantId, from, to, amountValue, editPaymentId)
                .onSuccess { plan = it }
                .onFailure { previewError = it.message }
        }
    }

    val canSave = amountError == null && periodError == null && plan != null && !saving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Payment" else "Record Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "$roomNumber · $tenantName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Payment date ${DateUtils.formatDate(dateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeader("Payment")

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter(Char::isDigit) },
                label = { Text("Amount received") },
                prefix = { Text("₹") },
                isError = amount.isNotEmpty() && amountError != null,
                supportingText = { if (amount.isNotEmpty()) amountError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = receipt,
                onValueChange = { receipt = it },
                label = { Text("Receipt number") },
                supportingText = { Text("Leave blank only for historical records without one.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = modeMenu, onExpandedChange = { modeMenu = it }) {
                OutlinedTextField(
                    value = mode.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                    PaymentMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label) },
                            onClick = { mode = m; modeMenu = false }
                        )
                    }
                }
            }

            SectionHeader("Period this payment covers")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("Paid from") },
                    placeholder = { Text("yyyy-MM") },
                    isError = periodError != null,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("Paid to") },
                    placeholder = { Text("yyyy-MM") },
                    isError = periodError != null,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            periodError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Allocation preview ───────────────────────────────────
            previewError?.let { ErrorPanel(it) }

            plan?.let { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "This payment will be applied to ${p.monthsTouched} month(s)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))

                        p.lines.forEach { line ->
                            val before = p.statesBefore.first { it.id == line.ledgerMonthId }
                            val after = p.statesAfter.first { it.id == line.ledgerMonthId }
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Text(
                                    "${DateUtils.formatMonth(line.month)} — ${CurrencyUtils.format(line.amount)} applied",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Was owing ${CurrencyUtils.format(before.outstanding)} of " +
                                        "${CurrencyUtils.format(before.rentDue)} · " +
                                        "now ${after.status.name.replace('_', ' ').lowercase()}" +
                                        if (after.outstanding > 0) {
                                            ", ${CurrencyUtils.format(after.outstanding)} still owing"
                                        } else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        DetailRow("Total allocated", CurrencyUtils.format(p.allocated), emphasise = true)
                    }
                }
            }

            Button(
                onClick = {
                    saving = true
                    val value = amountValue ?: 0L
                    if (isEdit) {
                        vm.editPayment(editPaymentId, dateMillis, value, mode, from, to, receipt, notes) { ok ->
                            saving = false; if (ok) onDone()
                        }
                    } else {
                        vm.recordPayment(actualTenantId, dateMillis, value, mode, from, to, receipt, notes) { ok ->
                            saving = false; if (ok) onDone()
                        }
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    when {
                        saving -> "Saving…"
                        isEdit -> "Update payment"
                        else -> "Save payment"
                    }
                )
            }
        }
    }
}
