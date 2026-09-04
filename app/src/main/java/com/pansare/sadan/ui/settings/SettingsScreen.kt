@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.data.RentRepository
import com.pansare.sadan.data.backup.BackupManager
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.ConfirmDialog
import com.pansare.sadan.ui.components.SectionHeader

/**
 * Settings. Every control here does something real — there are no placeholder toggles.
 * Backup and restore use the Storage Access Framework, so no filesystem path is hard-coded.
 */
@Composable
fun SettingsScreen(vm: AppViewModel, onViewIssues: () -> Unit, onImport: () -> Unit) {
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()

    var propertyName by remember { mutableStateOf("") }
    var propertyAddress by remember { mutableStateOf("") }
    var receiptPrefix by remember { mutableStateOf("") }

    var passwordDialog by remember { mutableStateOf<PasswordPurpose?>(null) }
    var pendingExport by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(Unit) {
        propertyName = vm.getSetting(RentRepository.KEY_PROPERTY_NAME)
            ?: RentRepository.DEFAULT_PROPERTY_NAME
        propertyAddress = vm.getSetting(RentRepository.KEY_PROPERTY_ADDRESS)
            ?: RentRepository.DEFAULT_PROPERTY_ADDRESS
        receiptPrefix = vm.getSetting(RentRepository.KEY_RECEIPT_PREFIX)
            ?: RentRepository.DEFAULT_RECEIPT_PREFIX
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) { pendingExport = uri; passwordDialog = PasswordPurpose.EXPORT } }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) confirmRestore = uri }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.titleLarge) }

        item { SectionHeader("Property") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = propertyName,
                        onValueChange = { propertyName = it },
                        label = { Text("Property name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = propertyAddress,
                        onValueChange = { propertyAddress = it },
                        label = { Text("Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = receiptPrefix,
                        onValueChange = { receiptPrefix = it.uppercase().take(6) },
                        label = { Text("Receipt prefix") },
                        supportingText = { Text("Receipts are numbered $receiptPrefix-YEAR-0001.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            vm.setSetting(RentRepository.KEY_PROPERTY_NAME, propertyName.trim())
                            vm.setSetting(RentRepository.KEY_PROPERTY_ADDRESS, propertyAddress.trim())
                            vm.setSetting(RentRepository.KEY_RECEIPT_PREFIX, receiptPrefix.trim())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) { Text("Save property details") }
                }
            }
        }

        item { SectionHeader("Backup and restore") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Encrypted backup", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Backups are encrypted with AES-GCM using a key derived from your password. " +
                            "The password is never stored — if you lose it the backup cannot be opened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                createBackup.launch(
                                    "sadan-backup-${System.currentTimeMillis()}.${BackupManager.FILE_EXTENSION}"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) { Text("Export") }
                        OutlinedButton(
                            onClick = { openBackup.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) { Text("Restore") }
                    }
                }
            }
        }

        item { SectionHeader("Data") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Current data", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${dashboard.totalRooms} rooms · ${dashboard.totalTenants} tenants · " +
                            "${dashboard.openIssues} open issue(s)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onViewIssues,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) { Text("Review data issues") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) { Text("Import payments from CSV") }
                }
            }
        }

        item { SectionHeader("About") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sadan", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Rent management for $propertyName, $propertyAddress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "All data stays on this device. The app works fully offline and " +
                            "never sends your records anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Restore is destructive, so confirm before even asking for the password.
    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = "Restore from backup?",
            message = "This replaces all current data with the contents of the backup. " +
                "If the backup cannot be read, your existing data is left untouched.",
            confirmLabel = "Continue",
            destructive = true,
            onConfirm = { pendingRestore = uri; passwordDialog = PasswordPurpose.RESTORE },
            onDismiss = { confirmRestore = null }
        )
    }

    passwordDialog?.let { purpose ->
        PasswordDialog(
            purpose = purpose,
            onConfirm = { password ->
                when (purpose) {
                    PasswordPurpose.EXPORT -> pendingExport?.let { vm.exportBackup(it, password) }
                    PasswordPurpose.RESTORE -> pendingRestore?.let { vm.restoreBackup(it, password) }
                }
                passwordDialog = null
                pendingExport = null
                pendingRestore = null
                confirmRestore = null
            },
            onDismiss = {
                passwordDialog = null
                pendingExport = null
                pendingRestore = null
                confirmRestore = null
            }
        )
    }
}

private enum class PasswordPurpose { EXPORT, RESTORE }

@Composable
private fun PasswordDialog(
    purpose: PasswordPurpose,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val exporting = purpose == PasswordPurpose.EXPORT

    val tooShort = password.length < BackupManager.MIN_PASSWORD
    val mismatch = exporting && confirm.isNotEmpty() && password != confirm
    val valid = !tooShort && (!exporting || password == confirm)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exporting) "Set a backup password" else "Enter the backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (exporting) {
                        "Choose a password of at least ${BackupManager.MIN_PASSWORD} characters. " +
                            "It cannot be recovered if forgotten."
                    } else "Enter the password used when this backup was created.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    isError = password.isNotEmpty() && tooShort,
                    supportingText = {
                        if (password.isNotEmpty() && tooShort) {
                            Text("At least ${BackupManager.MIN_PASSWORD} characters.")
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (exporting) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm password") },
                        isError = mismatch,
                        supportingText = { if (mismatch) Text("The passwords do not match.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password.toCharArray()) },
                enabled = valid
            ) { Text(if (exporting) "Export" else "Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
