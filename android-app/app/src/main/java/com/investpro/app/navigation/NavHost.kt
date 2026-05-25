package com.investpro.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.investpro.app.ui.screens.*

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    data object Dashboard : Screen("dashboard", "Market", { Icon(Icons.Filled.ShowChart, contentDescription = null) })
    data object Signals : Screen("signals", "Signals", { Icon(Icons.Filled.Notifications, contentDescription = null) })
    data object Portfolio : Screen("portfolio", "Portfolio", { Icon(Icons.Filled.AccountBalance, contentDescription = null) })
    data object Assistant : Screen("assistant", "AI Chat", { Icon(Icons.Filled.SmartToy, contentDescription = null) })
}

val screens = listOf(Screen.Dashboard, Screen.Signals, Screen.Portfolio, Screen.Assistant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestProNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = screen.icon,
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.Signals.route) { SignalsScreen() }
            composable(Screen.Portfolio.route) { PortfolioScreen() }
            composable(Screen.Assistant.route) { AssistantScreen() }
            composable("ticker/{symbol}") { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: "AAPL"
                TickerDetailScreen(symbol, navController)
            }
        }
    }
}
