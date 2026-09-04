@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pansare.sadan.ui.dashboard.DashboardScreen
import com.pansare.sadan.ui.defaulters.DefaultersScreen
import com.pansare.sadan.ui.import_.ImportScreen
import com.pansare.sadan.ui.issues.IssuesScreen
import com.pansare.sadan.ui.ledger.LedgerScreen
import com.pansare.sadan.ui.navigation.Routes
import com.pansare.sadan.ui.navigation.TopLevel
import com.pansare.sadan.ui.payments.PaymentsScreen
import com.pansare.sadan.ui.payments.RecordPaymentScreen
import com.pansare.sadan.ui.reports.ReportsScreen
import com.pansare.sadan.ui.rooms.RoomsScreen
import com.pansare.sadan.ui.settings.SettingsScreen
import com.pansare.sadan.ui.tenants.AddTenantScreen
import com.pansare.sadan.ui.tenants.EditTenantScreen
import com.pansare.sadan.ui.tenants.TenantProfileScreen

@Composable
fun SadanApp(vm: AppViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    // Every success and every failure is surfaced. No silent outcomes.
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            val message = when (event) {
                is UiEvent.Success -> event.message
                is UiEvent.Error -> event.message
            }
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    val isTopLevel = TopLevel.entries.any { it.route == route }

    Scaffold(
        topBar = {
            if (isTopLevel) {
                TopAppBar(
                    title = {
                        Column {
                            Text("PANSARE SADAN", fontWeight = FontWeight.Bold)
                            Text(
                                "Sakinaka, Mohili Village",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        val selected = route == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(TopLevel.DASHBOARD.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            NavHost(navController, startDestination = TopLevel.DASHBOARD.route) {

                composable(TopLevel.DASHBOARD.route) {
                    DashboardScreen(
                        vm = vm,
                        onAddTenant = { navController.navigate(Routes.addTenant()) },
                        onRecordPayment = { navController.navigate(TopLevel.ROOMS.route) },
                        onViewDefaulters = { navController.navigate(Routes.DEFAULTERS) },
                        onViewRooms = { navController.navigate(TopLevel.ROOMS.route) },
                        onViewIssues = { navController.navigate(Routes.ISSUES) }
                    )
                }

                composable(TopLevel.ROOMS.route) {
                    RoomsScreen(
                        vm = vm,
                        onOpenTenant = { navController.navigate(Routes.tenantProfile(it)) },
                        onAddTenant = { navController.navigate(Routes.addTenant(it)) }
                    )
                }

                composable(TopLevel.PAYMENTS.route) {
                    PaymentsScreen(
                        vm = vm,
                        onEditPayment = { navController.navigate(Routes.editPayment(it)) },
                        onAddPayment = { navController.navigate(TopLevel.ROOMS.route) }
                    )
                }

                composable(TopLevel.REPORTS.route) {
                    ReportsScreen(
                        vm = vm,
                        onOpenTenant = { navController.navigate(Routes.tenantProfile(it)) }
                    )
                }

                composable(TopLevel.SETTINGS.route) {
                    SettingsScreen(
                        vm = vm,
                        onViewIssues = { navController.navigate(Routes.ISSUES) },
                        onImport = { navController.navigate(Routes.IMPORT) }
                    )
                }

                composable(
                    Routes.ADD_TENANT,
                    arguments = listOf(navArgument("roomId") {
                        type = NavType.LongType; defaultValue = 0L
                    })
                ) { entry ->
                    AddTenantScreen(
                        vm = vm,
                        presetRoomId = entry.arguments?.getLong("roomId") ?: 0L,
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    Routes.TENANT_PROFILE,
                    arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("tenantId") ?: return@composable
                    TenantProfileScreen(
                        vm = vm,
                        tenantId = id,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.editTenant(id)) },
                        onRecordPayment = { navController.navigate(Routes.recordPayment(id)) },
                        onViewLedger = { navController.navigate(Routes.ledger(id)) }
                    )
                }

                composable(
                    Routes.EDIT_TENANT,
                    arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("tenantId") ?: return@composable
                    EditTenantScreen(
                        vm = vm,
                        tenantId = id,
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    Routes.LEDGER,
                    arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("tenantId") ?: return@composable
                    LedgerScreen(vm = vm, tenantId = id, onBack = { navController.popBackStack() })
                }

                composable(
                    Routes.RECORD_PAYMENT,
                    arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("tenantId") ?: return@composable
                    RecordPaymentScreen(
                        vm = vm,
                        tenantId = id,
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    Routes.EDIT_PAYMENT,
                    arguments = listOf(navArgument("paymentId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("paymentId") ?: return@composable
                    RecordPaymentScreen(
                        vm = vm,
                        tenantId = 0L,
                        editPaymentId = id,
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.DEFAULTERS) {
                    DefaultersScreen(
                        vm = vm,
                        onOpenTenant = { navController.navigate(Routes.tenantProfile(it)) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.IMPORT) {
                    ImportScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onViewIssues = { navController.navigate(Routes.ISSUES) }
                    )
                }

                composable(Routes.ISSUES) {
                    IssuesScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
