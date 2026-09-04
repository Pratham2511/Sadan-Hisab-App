@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.defaulters

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pansare.sadan.domain.DefaulterSummary
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.components.DetailRow
import com.pansare.sadan.ui.components.EmptyState
import com.pansare.sadan.util.CurrencyUtils
import com.pansare.sadan.util.DateUtils

/**
 * Defaulters, derived entirely from the ledger. The unpaid period column lists each
 * separate run of unpaid months, so a payment gap is never shown as one long stretch.
 */
@Composable
fun DefaultersScreen(
    vm: AppViewModel,
    onOpenTenant: (Long) -> Unit,
    onBack: () -> Unit
) {
    val defaulters by vm.defaulters.collectAsStateWithLifecycle()
    var summaries by remember { mutableStateOf<Map<Long, DefaulterSummary>>(emptyMap()) }

    LaunchedEffect(defaulters) {
        summaries = defaulters.mapNotNull { d ->
            d.tenantId?.let { it to vm.repo.summaryFor(it) }
        }.toMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Defaulters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                }
            )
        }
    ) { pad ->
        if (defaulters.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(
                    title = "No outstanding tenants",
                    message = "Every occupied room is fully settled.",
                    icon = Icons.Outlined.CheckCircle
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${defaulters.size} tenant(s) owing", fontWeight = FontWeight.SemiBold)
                        Text(
                            CurrencyUtils.format(defaulters.sumOf { it.outstanding }),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(defaulters, key = { it.roomId }) { d ->
                val summary = d.tenantId?.let { summaries[it] }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { d.tenantId?.let(onOpenTenant) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    d.displayRoomNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(d.tenantName.orEmpty())
                            }
                            Text(
                                CurrencyUtils.format(d.outstanding),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        DetailRow(
                            "Outstanding since",
                            summary?.outstandingSince?.let { DateUtils.formatMonth(it) } ?: "—"
                        )
                        DetailRow(
                            "Last paid up to",
                            summary?.lastPaidUpTo?.let { DateUtils.formatMonth(it) } ?: "—"
                        )
                        DetailRow("Unpaid months", d.unpaidMonths.toString())
                        DetailRow("Partly paid months", d.partialMonths.toString())

                        val periods = summary?.unpaidPeriods.orEmpty()
                        if (periods.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Unpaid periods",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            periods.forEach { p ->
                                Text(
                                    if (p.fromMonth == p.toMonth) {
                                        "${DateUtils.formatMonth(p.fromMonth)} — ${CurrencyUtils.format(p.amount)}"
                                    } else {
                                        "${DateUtils.formatMonth(p.fromMonth)} to " +
                                            "${DateUtils.formatMonth(p.toMonth)} " +
                                            "(${p.months} months) — ${CurrencyUtils.format(p.amount)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (d.hasUnresolvedHistory) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Some historical rent is unknown; this total may be incomplete.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
