@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.issues

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
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.data.IssueStatus
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.ui.components.StatusPill
import com.pansare.sadan.ui.theme.StatusColors

/**
 * Everything the app could not determine or reconcile. Nothing is hidden here — this is
 * where unknown historical rent, malformed imports and source contradictions surface.
 */
@Composable
fun IssuesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val issues by vm.issues.collectAsStateWithLifecycle()
    val open = issues.filter { it.status == IssueStatus.OPEN }
    val resolved = issues.filter { it.status == IssueStatus.RESOLVED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data issues") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { pad ->
        if (issues.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(
                    title = "Nothing needs review",
                    message = "No validation or reconciliation issues have been raised.",
                    icon = Icons.Outlined.FactCheck
                )
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (open.isNotEmpty()) {
                item {
                    Text(
                        "${open.size} item(s) need review",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            items(open, key = { it.id }) { issue ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = StatusColors.partial.copy(alpha = 0.10f)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(
                                issue.reference,
                                Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold
                            )
                            StatusPill(
                                issue.kind.replace('_', ' '),
                                StatusColors.partial
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                        if (issue.sourceValue.isNotBlank()) {
                            Text(
                                "Source: ${issue.sourceValue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { vm.resolveIssue(issue.id) }) {
                            Text("Mark as reviewed")
                        }
                    }
                }
            }

            if (resolved.isNotEmpty()) {
                item {
                    Text(
                        "Reviewed",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(resolved, key = { it.id }) { issue ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(issue.reference, fontWeight = FontWeight.Medium)
                            Text(
                                issue.message,
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
