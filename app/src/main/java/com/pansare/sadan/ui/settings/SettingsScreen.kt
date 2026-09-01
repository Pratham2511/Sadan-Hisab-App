package com.pansare.sadan.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pansare.sadan.ui.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.titleLarge) }
        
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Backup & Restore", fontWeight = FontWeight.SemiBold)
                    Text("Export or import encrypted backup to device storage.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { /* TODO */ }) { Text("Export") }
                        OutlinedButton(onClick = { /* TODO */ }) { Text("Import") }
                    }
                }
            }
        }
        
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("About", fontWeight = FontWeight.SemiBold)
                    Text("Pansare Sadan Rent Management", style = MaterialTheme.typography.bodyMedium)
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
