@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pansare.sadan.ui.navigation.AppNavigation
import com.pansare.sadan.ui.navigation.Route
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PansareApp(vm: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Route.DASHBOARD.name

    val snackbar = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        vm.message.collectLatest { msg ->
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PANSARE SADAN", fontWeight = FontWeight.Bold)
                        Text("Sakinaka, Mohili Village", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Route.entries.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.name,
                        onClick = {
                            navController.navigate(item.name) {
                                popUpTo(Route.DASHBOARD.name) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(item.icon) },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            AppNavigation(navController, vm)
        }
    }
}
