package com.pansare.sadan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pansare.sadan.ui.AppViewModel
import com.pansare.sadan.ui.dashboard.DashboardScreen
import com.pansare.sadan.ui.rooms.RoomsScreen
import com.pansare.sadan.ui.payments.PaymentsScreen
import com.pansare.sadan.ui.reports.ReportsScreen
import com.pansare.sadan.ui.settings.SettingsScreen
import com.pansare.sadan.ui.tenants.TenantProfileScreen
import com.pansare.sadan.ui.payments.RecordPaymentScreen
import com.pansare.sadan.ui.ledger.LedgerScreen

enum class Route(val label: String, val icon: String) {
    DASHBOARD("Dashboard", "D"),
    ROOMS("Rooms", "R"),
    PAYMENTS("Payments", "P"),
    REPORTS("Reports", "R"),
    SETTINGS("Settings", "S")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    vm: AppViewModel
) {
    NavHost(navController = navController, startDestination = Route.DASHBOARD.name) {
        composable(Route.DASHBOARD.name) {
            DashboardScreen(vm)
        }
        composable(Route.ROOMS.name) {
            RoomsScreen(vm, navController)
        }
        composable(Route.PAYMENTS.name) {
            PaymentsScreen(vm)
        }
        composable(Route.REPORTS.name) {
            ReportsScreen(vm)
        }
        composable(Route.SETTINGS.name) {
            SettingsScreen(vm)
        }
        composable(
            route = "tenant_profile/{tenantId}",
            arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments?.getLong("tenantId") ?: return@composable
            TenantProfileScreen(vm, tenantId, navController)
        }
        composable(
            route = "edit_tenant/{tenantId}",
            arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments?.getLong("tenantId") ?: return@composable
            com.pansare.sadan.ui.tenants.EditTenantScreen(vm, tenantId, navController)
        }
        composable(
            route = "record_payment/{tenantId}",
            arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments?.getLong("tenantId") ?: return@composable
            RecordPaymentScreen(vm, tenantId, navController)
        }
        composable(
            route = "ledger/{tenantId}",
            arguments = listOf(navArgument("tenantId") { type = NavType.LongType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments?.getLong("tenantId") ?: return@composable
            LedgerScreen(vm, tenantId, navController)
        }
    }
}
