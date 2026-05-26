package com.investpro.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.investpro.app.data.auth.CredentialManager
import com.investpro.app.ui.screens.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class NavGateViewModel @Inject constructor(
    credentialManager: CredentialManager,
) : ViewModel() {
    val state: StateFlow<com.investpro.app.data.auth.CredentialState> = credentialManager.state
    val isConfigured: Boolean get() = state.value.let { it.webullAccessToken.isNotBlank() && it.webullAccountId.isNotBlank() }
}

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    data object Dashboard : Screen("dashboard", "Market", { Icon(Icons.Filled.ShowChart, contentDescription = null) })
    data object Signals : Screen("signals", "Signals", { Icon(Icons.Filled.Notifications, contentDescription = null) })
    data object Portfolio : Screen("portfolio", "Portfolio", { Icon(Icons.Filled.AccountBalance, contentDescription = null) })
    data object Assistant : Screen("assistant", "AI Chat", { Icon(Icons.Filled.SmartToy, contentDescription = null) })
    data object Settings : Screen("settings", "Settings", { Icon(Icons.Filled.Settings, contentDescription = null) })
}

val bottomBarScreens = listOf(Screen.Dashboard, Screen.Signals, Screen.Portfolio, Screen.Assistant, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestProNavHost(
    gateViewModel: NavGateViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val credState by gateViewModel.state.collectAsState()

    // First-launch gate: if no Webull creds saved, land on Settings immediately.
    val startDestination = remember(credState.userId) {
        if (credState.webullAccessToken.isBlank() || credState.webullAccountId.isBlank()) {
            Screen.Settings.route
        } else {
            Screen.Dashboard.route
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = NavigationBarDefaults.Elevation,
            ) {
                bottomBarScreens.forEach { screen ->
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
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.Signals.route) { SignalsScreen() }
            composable(Screen.Portfolio.route) { PortfolioScreen() }
            composable(Screen.Assistant.route) { AssistantScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onDone = {
                        // After a successful save, jump straight to the dashboard.
                        if (gateViewModel.isConfigured) {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Settings.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
            composable("ticker/{symbol}") { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: "AAPL"
                TickerDetailScreen(symbol, navController)
            }
        }
    }
}
