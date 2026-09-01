package com.pansare.sadan.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pansare.sadan.data.LedgerStatus
import com.pansare.sadan.data.TenantStatus
import com.pansare.sadan.ui.theme.*

@Composable
fun StatusChip(status: LedgerStatus, modifier: Modifier = Modifier) {
    val (color, label) = when (status) {
        LedgerStatus.PAID -> StatusPaid to "PAID"
        LedgerStatus.PARTIALLY_PAID -> StatusPartiallyPaid to "PARTIAL"
        LedgerStatus.UNPAID -> StatusUnpaid to "UNPAID"
        LedgerStatus.NOT_APPLICABLE -> Color.Gray to "N/A"
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TenantStatusChip(status: TenantStatus, modifier: Modifier = Modifier) {
    val (color, label) = when (status) {
        TenantStatus.REGULAR -> StatusRegular to "REGULAR"
        TenantStatus.DEFAULTER -> StatusDefaulter to "DEFAULTER"
        TenantStatus.PARTIALLY_PAID -> StatusPartiallyPaid to "PARTIAL"
        TenantStatus.VACATED -> Color.Gray to "VACATED"
        TenantStatus.OTHER -> Color.Gray to "—"
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmText: String = "Confirm", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
