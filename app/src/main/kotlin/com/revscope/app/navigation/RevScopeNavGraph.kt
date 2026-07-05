package com.revscope.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.revscope.feature.dashboard.AdapterScanScreen
import com.revscope.feature.dashboard.DashboardScreen
import com.revscope.feature.dashboard.TrackModeScreen
import com.revscope.feature.dtc.DtcScreen
import com.revscope.feature.gear.GearAnalyzerScreen
import com.revscope.feature.sensors.SensorGraphScreen
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import com.revscope.feature.session.SessionDetailScreen
import com.revscope.feature.session.SessionHistoryScreen
import com.revscope.feature.settings.Mode22ScannerScreen
import com.revscope.feature.settings.SettingsScreen
import com.revscope.feature.vehicle.VehicleProfileScreen

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextMutedColor = Color(0xFF6B7089)

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Dashboard", Icons.Default.Speed),
    BottomNavItem(Screen.Sensors, "Sensores", Icons.Default.Timeline),
    BottomNavItem(Screen.Dtc, "DTC", Icons.Default.BugReport),
    BottomNavItem(Screen.Sessions, "Historial", Icons.Default.History),
)

private val bottomNavRoutes = bottomNavItems.map { it.screen.route }.toSet()

@Composable
fun RevScopeNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Single connection instance scoped to the Activity — hiltViewModel() inside each
    // destination would create one ViewModel PER SCREEN, so navigating away from the
    // adapter screen would clear its ViewModel and drop the Bluetooth socket.
    val connectionVm: ConnectionViewModel =
        hiltViewModel(LocalContext.current as ComponentActivity)

    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar(containerColor = SurfaceColor) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.screen.route,
                            onClick = {
                                if (currentRoute != item.screen.route) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentColor,
                                selectedTextColor = AccentColor,
                                unselectedIconColor = TextMutedColor,
                                unselectedTextColor = TextMutedColor,
                                indicatorColor = Color(0xFF1C1C28),
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToAdapterScan = { navController.navigate(Screen.AdapterScan.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    connectionVm = connectionVm,
                )
            }
            composable(Screen.AdapterScan.route) {
                AdapterScanScreen(
                    onNavigateBack = { navController.popBackStack() },
                    connectionVm = connectionVm,
                )
            }
            composable(Screen.GearAnalyzer.route) {
                GearAnalyzerScreen()
            }
            composable(Screen.Sensors.route) {
                SensorGraphScreen(connectionVm = connectionVm)
            }
            composable(Screen.Dtc.route) {
                DtcScreen(connectionVm = connectionVm)
            }
            composable(Screen.Sessions.route) {
                SessionHistoryScreen(
                    onOpenSession = { sessionId ->
                        navController.navigate(Screen.SessionDetail.withId(sessionId))
                    },
                )
            }
            composable(
                route = Screen.SessionDetail.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) {
                SessionDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Screen.VehicleProfile.route) {
                VehicleProfileScreen(connectionVm = connectionVm)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToScanner = { navController.navigate(Screen.Mode22Scanner.route) },
                    onNavigateToGearAnalyzer = { navController.navigate(Screen.GearAnalyzer.route) },
                    onNavigateToVehicleProfiles = { navController.navigate(Screen.VehicleProfile.route) },
                    onNavigateToTrackMode = { navController.navigate(Screen.TrackMode.route) },
                )
            }
            composable(Screen.TrackMode.route) {
                TrackModeScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Mode22Scanner.route) {
                Mode22ScannerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    connectionVm = connectionVm,
                )
            }
        }
    }
}
