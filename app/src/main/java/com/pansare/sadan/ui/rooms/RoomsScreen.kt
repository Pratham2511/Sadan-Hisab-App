@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.rooms

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.RoomCardState
import com.pansare.sadan.ui.RoomFilter
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.ui.components.StandingPill
import com.pansare.sadan.util.CurrencyUtils

@Composable
fun RoomsScreen(
    vm: AppViewModel,
    onOpenTenant: (Long) -> Unit,
    onAddTenant: (Long) -> Unit
) {
    val rooms by vm.visibleRooms.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var wing by remember { mutableStateOf("A") }

    val wingRooms = rooms.filter { it.wing == wing }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddTenant(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add Tenant") }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            TabRow(selectedTabIndex = if (wing == "A") 0 else 1) {
                Tab(
                    selected = wing == "A",
                    onClick = { wing = "A" },
                    text = { Text("A Wing") }
                )
                Tab(
                    selected = wing == "B",
                    onClick = { wing = "B" },
                    text = { Text("B Wing") }
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                label = { Text("Search room, tenant or mobile") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoomFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.setFilter(f) },
                        label = { Text(f.label) }
                    )
                }
            }

            if (wingRooms.isEmpty()) {
                EmptyState(
                    title = "No rooms match",
                    message = if (query.isBlank()) {
                        "No rooms in $wing Wing match the \"${filter.label}\" filter."
                    } else "Nothing matches \"$query\" in $wing Wing.",
                    icon = Icons.Outlined.MeetingRoom,
                    actionLabel = "Clear search",
                    onAction = { vm.setQuery(""); vm.setFilter(RoomFilter.ALL) }
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
                ) {
                    items(wingRooms, key = { it.roomId }) { room ->
                        RoomCard(
                            room = room,
                            onClick = {
                                if (room.tenantId != null) onOpenTenant(room.tenantId)
                                else onAddTenant(room.roomId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(room: RoomCardState, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (room.isOccupied) {
                MaterialTheme.colorScheme.surface
            } else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    room.displayRoomNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))

                if (room.isOccupied) {
                    Text(room.tenantName.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Rent ${CurrencyUtils.format(room.monthlyRent ?: 0)} per month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (room.outstanding > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Outstanding ${CurrencyUtils.format(room.outstanding)} · " +
                                "${room.unpaidMonths} unpaid, ${room.partialMonths} partial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (room.hasUnresolvedHistory) {
                        Text(
                            "Some historical rent is unknown — needs review",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                } else {
                    Text(
                        "Vacant — tap to add a tenant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            StandingPill(room.standing)
        }
    }
}
