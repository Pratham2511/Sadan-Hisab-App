@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.tenants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.data.TenantEntity
import com.pansare.sadan.data.TenantStatus
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.ConfirmDialog
import com.pansare.sadan.ui.components.LoadingState
import com.pansare.sadan.ui.components.SectionHeader
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

@Composable
fun EditTenantScreen(
    vm: AppViewModel,
    tenantId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var loaded by remember { mutableStateOf<TenantEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(TenantStatus.ACTIVE) }
    var statusMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Rent change is a separate, dated operation — editing the tenant never rewrites history.
    var newRent by remember { mutableStateOf("") }
    var rentFrom by remember { mutableStateOf(MonthKey.current()) }
    var showRentConfirm by remember { mutableStateOf(false) }
    var showMoveOut by remember { mutableStateOf(false) }
    var moveOutMonth by remember { mutableStateOf(MonthKey.current()) }

    val rentChanges by vm.repo.observeRentChanges(tenantId).collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(tenantId) {
        vm.repo.findTenant(tenantId)?.let {
            loaded = it
            name = it.tenantName
            mobile = it.mobileNumber
            remarks = it.remarks
            status = it.status
        }
    }

    val tenant = loaded
    if (tenant == null) {
        LoadingState(label = "Loading tenant")
        return
    }

    val nameError = if (name.isBlank()) "Tenant name is required." else null
    val mobileError = when {
        mobile.isBlank() -> null
        !mobile.trim().matches(Regex("^[0-9+][0-9 -]{6,19}$")) -> "Enter a valid mobile number."
        else -> null
    }
    val rentValue = newRent.trim().toLongOrNull()
    val rentFromValid = MonthKey.isValid(rentFrom)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Tenant") },
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
            SectionHeader("Details")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tenant name") },
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("Mobile number") },
                isError = mobileError != null,
                supportingText = { mobileError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(expanded = statusMenu, onExpandedChange = { statusMenu = it }) {
                OutlinedTextField(
                    value = status.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Status") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    TenantStatus.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(s.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
                            },
                            onClick = { status = s; statusMenu = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    saving = true
                    vm.updateTenant(
                        tenant.copy(
                            tenantName = name.trim(),
                            mobileNumber = mobile.trim(),
                            remarks = remarks.trim(),
                            status = status
                        )
                    ) { ok -> saving = false; if (ok) onDone() }
                },
                enabled = nameError == null && mobileError == null && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) { Text(if (saving) "Saving…" else "Save changes") }

            SectionHeader("Rent history")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Current rent ${CurrencyUtils.format(tenant.monthlyRent)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    rentChanges.forEach { change ->
                        Text(
                            "From ${DateUtils.formatMonth(change.effectiveFromMonth)}: " +
                                CurrencyUtils.format(change.monthlyRent) +
                                if (change.note.isNotBlank()) " (${change.note})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Text(
                "Changing the rent applies from the month you choose onward. Months already " +
                    "settled keep the rent that applied at the time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newRent,
                    onValueChange = { newRent = it.filter(Char::isDigit) },
                    label = { Text("New rent") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = rentFrom,
                    onValueChange = { rentFrom = it },
                    label = { Text("From month") },
                    placeholder = { Text("yyyy-MM") },
                    isError = !rentFromValid,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = { showRentConfirm = true },
                enabled = rentValue != null && rentValue >= 0 && rentFromValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Record rent change") }

            SectionHeader("End tenancy")
            OutlinedTextField(
                value = moveOutMonth,
                onValueChange = { moveOutMonth = it },
                label = { Text("Final month of tenancy") },
                placeholder = { Text("yyyy-MM") },
                isError = !MonthKey.isValid(moveOutMonth),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = { showMoveOut = true },
                enabled = MonthKey.isValid(moveOutMonth),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Mark as moved out") }
        }
    }

    if (showRentConfirm && rentValue != null) {
        ConfirmDialog(
            title = "Record rent change?",
            message = "From ${DateUtils.formatMonth(rentFrom)} the rent becomes " +
                "${CurrencyUtils.format(rentValue)}. Earlier months keep their own rate.",
            confirmLabel = "Record",
            onConfirm = {
                vm.changeRent(tenantId, rentValue, rentFrom, "Rent revised") { ok ->
                    if (ok) { newRent = "" }
                }
            },
            onDismiss = { showRentConfirm = false }
        )
    }

    if (showMoveOut) {
        ConfirmDialog(
            title = "End this tenancy?",
            message = "The room becomes vacant after ${DateUtils.formatMonth(moveOutMonth)}. " +
                "The ledger and all payments are kept, and unpaid future months are removed.",
            confirmLabel = "End tenancy",
            destructive = true,
            onConfirm = { vm.moveOutTenant(tenantId, moveOutMonth) { ok -> if (ok) onDone() } },
            onDismiss = { showMoveOut = false }
        )
    }
}
