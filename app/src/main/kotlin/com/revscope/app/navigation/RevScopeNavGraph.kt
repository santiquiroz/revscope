package com.revscope.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.revscope.app.onboarding.OnboardingScreen
import com.revscope.app.onboarding.OnboardingViewModel
import com.revscope.app.safety.CrashAlertDialog
import com.revscope.app.safety.CrashAlertViewModel
import com.revscope.feature.dashboard.AdapterScanScreen
import com.revscope.feature.dashboard.DashboardScreen
import com.revscope.feature.dashboard.TrackModeScreen
import com.revscope.feature.dtc.DtcScreen
import com.revscope.feature.gear.GearAnalyzerScreen
import com.revscope.feature.map.LiveMapScreen
import com.revscope.feature.sensors.SensorGraphScreen
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import com.revscope.feature.session.SessionCompareScreen
import com.revscope.feature.session.SessionDetailScreen
import com.revscope.feature.session.SessionHistoryScreen
import com.revscope.feature.settings.Mode22ScannerScreen
import com.revscope.feature.settings.SettingsScreen
import com.revscope.feature.vehicle.VehicleProfileScreen
import com.revscope.feature.workshop.AlDiaScreen
import com.revscope.feature.workshop.HealthCheckScreen
import com.revscope.feature.workshop.LiveMixtureScreen
import com.revscope.feature.workshop.MaintenanceScreen
import com.revscope.feature.workshop.MechanicChatScreen
import com.revscope.feature.workshop.Mode06Screen
import com.revscope.feature.workshop.O2WaveScreen
import com.revscope.feature.workshop.OdometerScreen
import com.revscope.feature.workshop.SpeedComparisonScreen
import com.revscope.feature.workshop.WorkshopScreen

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
    initialOpenAlDia: Boolean = false,
    onInitialOpenAlDiaConsumed: () -> Unit = {},
) {
    val onboardingVm: OnboardingViewModel =
        hiltViewModel(LocalContext.current as ComponentActivity)
    val onboardingDone by onboardingVm.onboardingDone.collectAsState()

    // Nothing to compose until we know whether to start at Onboarding or Dashboard —
    // composing NavHost picks its startDestination once, on first composition.
    if (onboardingDone == null) return

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Single connection instance scoped to the Activity — hiltViewModel() inside each
    // destination would create one ViewModel PER SCREEN, so navigating away from the
    // adapter screen would clear its ViewModel and drop the Bluetooth socket.
    val connectionVm: ConnectionViewModel =
        hiltViewModel(LocalContext.current as ComponentActivity)
    val vehiclePickerVm: VehiclePickerViewModel =
        hiltViewModel(LocalContext.current as ComponentActivity)
    val crashAlertVm: CrashAlertViewModel =
        hiltViewModel(LocalContext.current as ComponentActivity)
    val crashAlarmState by crashAlertVm.alarmState.collectAsState()

    LaunchedEffect(initialSessionId) {
        initialSessionId?.let {
            navController.navigate(Screen.SessionDetail.withId(it))
            onInitialSessionConsumed()
        }
    }

    LaunchedEffect(initialOpenAlDia) {
        if (initialOpenAlDia) {
            navController.navigate(Screen.AlDia.route)
            onInitialOpenAlDiaConsumed()
        }
    }

    var showVehiclePicker by rememberSaveable { mutableStateOf(false) }
    var vehiclePickerIsStartupPrompt by rememberSaveable { mutableStateOf(false) }
    var hasOfferedVehiclePicker by rememberSaveable { mutableStateOf(false) }
    val vehicleProfiles by vehiclePickerVm.profiles.collectAsState()
    val askVehicleOnStart by vehiclePickerVm.askOnStart.collectAsState()
    val activeVehicleProfile by vehiclePickerVm.activeProfile.collectAsState()

    LaunchedEffect(vehicleProfiles, askVehicleOnStart, currentRoute) {
        val onOnboarding = currentRoute == Screen.Onboarding.route
        if (!onOnboarding && !hasOfferedVehiclePicker && askVehicleOnStart && vehicleProfiles.isNotEmpty() && !showVehiclePicker) {
            showVehiclePicker = true
            vehiclePickerIsStartupPrompt = true
            hasOfferedVehiclePicker = true
        }
    }

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
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = if (onboardingDone == true) Screen.Dashboard.route else Screen.Onboarding.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        vm = onboardingVm,
                        onFinished = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToAdapterScan = { navController.navigate(Screen.AdapterScan.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToTrackMode = { navController.navigate(Screen.TrackMode.route) },
                        onNavigateToAlDia = { navController.navigate(Screen.AlDia.route) },
                        connectionVm = connectionVm,
                    )
                }
                composable(Screen.Workshop.route) {
                    WorkshopScreen(
                        connectionVm = connectionVm,
                        onOpenAlDia = { navController.navigate(Screen.AlDia.route) },
                        onOpenHealthCheck = { navController.navigate(Screen.HealthCheck.route) },
                        onOpenDtc = { navController.navigate(Screen.Dtc.route) },
                        onOpenLiveMixture = { navController.navigate(Screen.LiveMixture.route) },
                        onOpenSensors = { navController.navigate(Screen.Sensors.route) },
                        onOpenScanner = { navController.navigate(Screen.Mode22Scanner.route) },
                        onOpenGearAnalyzer = { navController.navigate(Screen.GearAnalyzer.route) },
                        onOpenProfiles = { navController.navigate(Screen.VehicleProfile.route) },
                        onOpenMaintenance = { navController.navigate(Screen.Maintenance.route) },
                        onOpenO2Wave = { navController.navigate(Screen.O2Wave.route) },
                        onOpenMode06 = { navController.navigate(Screen.Mode06.route) },
                        onOpenOdometer = { navController.navigate(Screen.Odometer.route) },
                        onOpenSpeedComparison = { navController.navigate(Screen.SpeedComparison.route) },
                        onOpenMechanicChat = { navController.navigate(Screen.MechanicChat.route) },
                    )
                }
                composable(Screen.MechanicChat.route) {
                    MechanicChatScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    )
                }
                composable(Screen.Odometer.route) {
                    OdometerScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.SpeedComparison.route) {
                    SpeedComparisonScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.AlDia.route) {
                    AlDiaScreen(
                        onOpenHealthCheck = { navController.navigate(Screen.HealthCheck.route) },
                        onOpenProfiles = { navController.navigate(Screen.VehicleProfile.route) },
                        onOpenMaintenance = { navController.navigate(Screen.Maintenance.route) },
                    )
                }
                composable(Screen.Maintenance.route) {
                    MaintenanceScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.HealthCheck.route) {
                    HealthCheckScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.LiveMixture.route) {
                    LiveMixtureScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.O2Wave.route) {
                    O2WaveScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Mode06.route) {
                    Mode06Screen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.LiveMap.route) { LiveMapScreen() }
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
            if (currentRoute in bottomNavRoutes) {
                val connState by connectionVm.connectionState.collectAsState()
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 4.dp),
                ) {
                    VehicleSwitcherPill(connectionState = connState, activeProfile = activeVehicleProfile) {
                        hasOfferedVehiclePicker = true
                        vehiclePickerIsStartupPrompt = false
                        showVehiclePicker = true
                    }
                }
            }
            if (showVehiclePicker) {
                VehiclePickerSheet(
                    vm = vehiclePickerVm,
                    isStartupPrompt = vehiclePickerIsStartupPrompt,
                    onDismiss = { showVehiclePicker = false },
                    onAddVehicle = {
                        showVehiclePicker = false
                        navController.navigate(Screen.VehicleProfile.route)
                    },
                    onManageAdapter = {
                        showVehiclePicker = false
                        navController.navigate(Screen.AdapterScan.route)
                    },
                )
            }
            crashAlarmState?.let { alarm ->
                CrashAlertDialog(state = alarm, onEstoyBien = crashAlertVm::confirmSafe)
            }
        }
    }
}
