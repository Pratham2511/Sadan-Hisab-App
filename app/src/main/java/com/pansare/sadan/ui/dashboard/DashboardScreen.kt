package com.pansare.sadan.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.ui.components.LoadingState
import com.pansare.sadan.ui.components.MetricCard
import com.pansare.sadan.ui.components.SectionHeader
import com.pansare.sadan.ui.components.UnresolvedNotice
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onAddTenant: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewDefaulters: () -> Unit,
    onViewRooms: () -> Unit,
    onViewIssues: () -> Unit
) {
    val state by vm.dashboard.collectAsStateWithLifecycle()
    val asOf by vm.asOf.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingState(label = "Loading your property summary")
        return
    }

    // Fresh install: no tenants yet. Show a genuinely useful welcome, not an empty grid.
    if (state.isFreshInstall) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            EmptyState(
                title = "Welcome to Sadan",
                message = "${state.totalRooms} rooms are ready — ${state.totalRooms - 20} in A Wing and 20 in B Wing. " +
                    "Add your first tenant to begin keeping the rent ledger.",
                icon = Icons.Outlined.Home,
                actionLabel = "Add your first tenant",
                onAction = onAddTenant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onViewRooms,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) { Text("Browse all ${state.totalRooms} rooms") }
        }
        return
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column {
                Text("Overview", style = MaterialTheme.typography.titleLarge)
                Text(
                    "As of ${DateUtils.formatMonth(asOf)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.totalOutstanding > 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Total outstanding",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        CurrencyUtils.format(state.totalOutstanding),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.totalOutstanding > 0) {
                            "${state.unpaidMonths} unpaid and ${state.partialMonths} partly paid months"
                        } else "Every month is settled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (state.unresolvedOutstanding > 0 || state.openIssues > 0) {
            item {
                UnresolvedNotice(
                    "Some figures depend on months whose rent is not known, so totals may be incomplete. " +
                        "${state.openIssues} item(s) need review."
                )
            }
        }

        item { SectionHeader("Occupancy") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Total rooms", state.totalRooms.toString(), Modifier.weight(1f))
                MetricCard("Occupied", state.occupiedRooms.toString(), Modifier.weight(1f))
                MetricCard("Vacant", state.vacantRooms.toString(), Modifier.weight(1f))
            }
        }

        item { SectionHeader("Tenants") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Tenants", state.totalTenants.toString(), Modifier.weight(1f))
                MetricCard(
                    "Regular", state.regularTenants.toString(), Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    "Defaulters", state.defaulters.toString(), Modifier.weight(1f),
                    valueColor = MaterialTheme.colorScheme.error
                )
                MetricCard("Partly paid", state.partiallyPaidTenants.toString(), Modifier.weight(1f))
            }
        }

        item { SectionHeader("Quick actions") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddTenant,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Add tenant")
                }
                OutlinedButton(
                    onClick = onRecordPayment,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Record payment")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onViewDefaulters,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Defaulters")
                }
                OutlinedButton(
                    onClick = onViewRooms,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.MeetingRoom, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Rooms")
                }
            }
        }

        if (state.openIssues > 0) {
            item {
                OutlinedButton(
                    onClick = onViewIssues,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) { Text("Review ${state.openIssues} data issue(s)") }
            }
        }
    }
}
