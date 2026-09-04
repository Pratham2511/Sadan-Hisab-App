@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.tenants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.SectionHeader

/**
 * Creates a tenancy without needing any import. Validation is inline and per-field,
 * and the save button stays disabled until the form is genuinely valid.
 */
@Composable
fun AddTenantScreen(
    vm: AppViewModel,
    presetRoomId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val vacant = remember(rooms) { rooms.filter { !it.isOccupied } }

    var roomId by remember { mutableStateOf(presetRoomId) }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var rent by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(MonthKey.current()) }
    var remarks by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var roomMenu by remember { mutableStateOf(false) }

    LaunchedEffect(presetRoomId, vacant) {
        if (roomId == 0L && presetRoomId != 0L) roomId = presetRoomId
    }

    val selectedRoom = rooms.firstOrNull { it.roomId == roomId }

    val roomError = if (roomId == 0L) "Choose a room." else null
    val nameError = when {
        name.isBlank() -> "Tenant name is required."
        name.trim().length < 2 -> "Enter the full name."
        else -> null
    }
    val rentValue = rent.trim().toLongOrNull()
    val rentError = when {
        rent.isBlank() -> "Monthly rent is required."
        rentValue == null -> "Enter rent as a whole number."
        rentValue < 0 -> "Rent cannot be negative."
        else -> null
    }
    val mobileError = when {
        mobile.isBlank() -> null
        !mobile.trim().matches(Regex("^[0-9+][0-9 -]{6,19}$")) -> "Enter a valid mobile number."
        else -> null
    }
    val startError = when {
        !MonthKey.isValid(start) -> "Use the format yyyy-MM, e.g. ${MonthKey.current()}."
        start > MonthKey.current() -> "The start month cannot be in the future."
        else -> null
    }

    val valid = listOf(roomError, nameError, rentError, mobileError, startError).all { it == null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Tenant") },
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
            SectionHeader("Room")

            ExposedDropdownMenuBox(
                expanded = roomMenu,
                onExpandedChange = { roomMenu = it }
            ) {
                OutlinedTextField(
                    value = selectedRoom?.displayRoomNumber ?: "Select a vacant room",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Room") },
                    isError = roomError != null,
                    supportingText = { roomError?.let { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                    if (vacant.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Every room is currently occupied") },
                            onClick = { roomMenu = false }
                        )
                    }
                    vacant.forEach { room ->
                        DropdownMenuItem(
                            text = { Text(room.displayRoomNumber) },
                            onClick = { roomId = room.roomId; roomMenu = false }
                        )
                    }
                }
            }

            SectionHeader("Tenant details")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tenant name") },
                isError = name.isNotEmpty() && nameError != null,
                supportingText = { if (name.isNotEmpty()) nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("Mobile number (optional)") },
                isError = mobileError != null,
                supportingText = { mobileError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = rent,
                onValueChange = { rent = it.filter(Char::isDigit) },
                label = { Text("Monthly rent") },
                prefix = { Text("₹") },
                isError = rent.isNotEmpty() && rentError != null,
                supportingText = { if (rent.isNotEmpty()) rentError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = start,
                onValueChange = { start = it },
                label = { Text("Occupancy start month") },
                placeholder = { Text("yyyy-MM") },
                isError = startError != null,
                supportingText = {
                    Text(
                        startError
                            ?: "The ledger will be created from this month onward."
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "Rent recorded here becomes the opening rate. If the rent changes later, " +
                    "record a rent change so past months keep their own correct rate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    saving = true
                    vm.addTenant(
                        roomId = roomId,
                        name = name,
                        mobile = mobile,
                        rent = rentValue ?: 0L,
                        occupancyStart = start,
                        remarks = remarks
                    ) { ok -> saving = false; if (ok) onDone() }
                },
                enabled = valid && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(if (saving) "Saving…" else "Save tenant")
            }
        }
    }
}
