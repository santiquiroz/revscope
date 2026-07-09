package com.revscope.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import com.revscope.feature.session.SessionCompareScreen
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
    BottomNavItem(Screen.Dashboard, "Conducir", Icons.Default.Speed),
    BottomNavItem(Screen.LiveMap, "Mapa", Icons.Default.Map),
    BottomNavItem(Screen.Workshop, "Taller", Icons.Default.Build),
    BottomNavItem(Screen.Sessions, "Viajes", Icons.Default.History),
    BottomNavItem(Screen.Settings, "Ajustes", Icons.Default.Settings),
)

private val bottomNavRoutes = bottomNavItems.map { it.screen.route }.toSet()

@Composable
fun RevScopeNavGraph(
    navController: NavHostController = rememberNavController(),
    initialSessionId: Long? = null,
    onInitialSessionConsumed: () -> Unit = {},
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Single connection instance scoped to the Activity — hiltViewModel() inside each
    // destination would create one ViewModel PER SCREEN, so navigating away from the
    // adapter screen would clear its ViewModel and drop the Bluetooth socket.
    val connectionVm: ConnectionViewModel =
        hiltViewModel(LocalContext.current as ComponentActivity)

    LaunchedEffect(initialSessionId) {
        initialSessionId?.let {
            navController.navigate(Screen.SessionDetail.withId(it))
            onInitialSessionConsumed()
        }
    }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            if (currentRoute in bottomNavRoutes) {
                val connState by connectionVm.connectionState.collectAsState()
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    ConnectionChip(state = connState) {
                        navController.navigate(Screen.AdapterScan.route)
                    }
                }
            }
        },
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
                    onNavigateToTrackMode = { navController.navigate(Screen.TrackMode.route) },
                    connectionVm = connectionVm,
                )
            }
            composable(Screen.Workshop.route) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("En construcción")
                }
            }
            composable(Screen.LiveMap.route) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("En construcción")
                }
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
                    onCompareSessions = { a, b ->
                        navController.navigate(Screen.SessionCompare.withIds(a, b))
                    },
                )
            }
            composable(
                route = Screen.SessionCompare.route,
                arguments = listOf(
                    navArgument("sessionA") { type = NavType.LongType },
                    navArgument("sessionB") { type = NavType.LongType },
                ),
            ) {
                SessionCompareScreen(
                    onNavigateBack = { navController.popBackStack() },
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
                    onNavigateToVehicleProfiles = { navController.navigate(Screen.VehicleProfile.route) },
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
