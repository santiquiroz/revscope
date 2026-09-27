package com.revscope.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Scaffold
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.revscope.app.onboarding.AiValueScreen
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.SelectorVehiculo
import com.revscope.app.onboarding.OnboardingScreen
import com.revscope.app.onboarding.OnboardingViewModel
import com.revscope.app.safety.CrashAlertDialog
import com.revscope.app.safety.CrashAlertViewModel
import com.revscope.feature.dashboard.AdapterScanScreen
import com.revscope.feature.dashboard.DashboardScreen
import com.revscope.feature.dashboard.TrackModeScreen
import com.revscope.feature.dtc.DestinosDtc
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
import com.revscope.feature.workshop.taller.DestinosSesion
import com.revscope.feature.workshop.taller.multimetro.MultimetroScreen
import com.revscope.feature.workshop.taller.multimetro.MultimetroViewModel
import com.revscope.feature.workshop.taller.prueba.PruebaGuiadaScreen
import com.revscope.feature.workshop.taller.prueba.PruebaGuiadaViewModel
import com.revscope.feature.workshop.taller.NuevaSesionScreen
import com.revscope.feature.workshop.taller.SesionTallerScreen
import com.revscope.feature.workshop.taller.SesionTallerViewModel
import com.revscope.feature.workshop.taller.TallerHubScreen
import com.revscope.feature.workshop.taller.modelo.ConocimientoModeloScreen
import com.revscope.feature.workshop.taller.modelo.ReferenciasScreen

internal data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
)

internal val bottomNavItems = listOf(
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
    val actividad = LocalActivity.current as ComponentActivity
    val onboardingVm: OnboardingViewModel = hiltViewModel(actividad)
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
        hiltViewModel(actividad)
    val vehiclePickerVm: VehiclePickerViewModel =
        hiltViewModel(actividad)
    val crashAlertVm: CrashAlertViewModel =
        hiltViewModel(actividad)
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

    // El selector de vehículo se arma acá (lo comparten las pestañas) pero cada pestaña lo monta
    // en su propio encabezado: flotando encima tapaba los títulos. El mapa lo oculta mientras navega.
    var showVehiclePicker by rememberSaveable { mutableStateOf(false) }
    var vehiclePickerIsStartupPrompt by rememberSaveable { mutableStateOf(false) }
    var hasOfferedVehiclePicker by rememberSaveable { mutableStateOf(false) }
    val vehicleProfiles by vehiclePickerVm.profiles.collectAsState()
    val askVehicleOnStart by vehiclePickerVm.askOnStart.collectAsState()
    val activeVehicleProfile by vehiclePickerVm.activeProfile.collectAsState()

    val connState by connectionVm.connectionState.collectAsState()
    val selectorVehiculo: @Composable () -> Unit = {
        SelectorVehiculo(
            vehiculo = vehiculoEnEncabezado(connState, activeVehicleProfile),
            onClick = {
                hasOfferedVehiclePicker = true
                vehiclePickerIsStartupPrompt = false
                showVehiclePicker = true
            },
        )
    }

    LaunchedEffect(vehicleProfiles, askVehicleOnStart, currentRoute) {
        val onOnboarding = currentRoute == Screen.Onboarding.route
        if (!onOnboarding && !hasOfferedVehiclePicker && askVehicleOnStart && vehicleProfiles.isNotEmpty() && !showVehiclePicker) {
            showVehiclePicker = true
            vehiclePickerIsStartupPrompt = true
            hasOfferedVehiclePicker = true
        }
    }

    Scaffold(
        containerColor = RevScopeColors.Background,
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                BarraInferior(rutaActual = currentRoute) { item ->
                    navController.navigate(item.screen.navRoute) {
                        popUpTo(Screen.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
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
                        onFinished = { goToAdapterScan ->
                            // M8: re-run desde Settings deja [Dashboard, Settings] debajo —
                            // solo el arranque en frío (Onboarding es el startDestination,
                            // sin entrada previa) necesita navigate+popUpTo a Dashboard.
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                            if (goToAdapterScan) {
                                navController.navigate(Screen.AdapterScan.route)
                            }
                        },
                    )
                }
                composable(Screen.AiValue.route) {
                    AiValueScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToAdapterScan = { navController.navigate(Screen.AdapterScan.route) },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.navRoute) },
                        onNavigateToTrackMode = { navController.navigate(Screen.TrackMode.route) },
                        onNavigateToAlDia = { navController.navigate(Screen.AlDia.route) },
                        onOpenMap = {
                            // Misma semántica que el tap en la tab "Mapa" del bottom bar.
                            navController.navigate(Screen.LiveMap.route) {
                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        selectorVehiculo = selectorVehiculo,
                        connectionVm = connectionVm,
                    )
                }
                composable(Screen.Workshop.route) {
                    TallerHubScreen(
                        selectorVehiculo = selectorVehiculo,
                        acciones = accionesHub(navController),
                    )
                }
                composable(Screen.TallerNuevaSesion.route) {
                    NuevaSesionScreen(
                        onVolver = { navController.popBackStack() },
                        onSesionAbierta = { id ->
                            navController.navigate(Screen.TallerSesion.withId(id)) {
                                popUpTo(Screen.TallerNuevaSesion.route) { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    route = Screen.TallerSesion.route,
                    arguments = listOf(navArgument(SesionTallerViewModel.ARG_SESION) { type = NavType.LongType }),
                ) {
                    SesionTallerScreen(
                        destinos = DestinosSesion(
                            onVolver = { navController.popBackStack() },
                            onLeerCodigos = { navController.navigate(Screen.Dtc.route) },
                            onCaptura = { navController.navigate(Screen.Sensors.route) },
                            onChequeo = { navController.navigate(Screen.HealthCheck.route) },
                            onPruebaGuiada = { navController.navigate(Screen.PruebaGuiada.navRoute) },
                            onMultimetro = { navController.navigate(Screen.Multimetro.navRoute) },
                        ),
                    )
                }
                composable(
                    route = Screen.PruebaGuiada.route,
                    arguments = listOf(
                        navArgument(PruebaGuiadaViewModel.ARG_TIPO) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    PruebaGuiadaScreen(
                        onVolver = { navController.popBackStack() },
                        onVerGuia = { navController.navigate(Screen.Dtc.route) },
                    )
                }
                composable(
                    route = Screen.Multimetro.route,
                    arguments = listOf(
                        navArgument(MultimetroViewModel.ARG_SENSOR) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    MultimetroScreen(onVolver = { navController.popBackStack() })
                }
                composable(Screen.ConocimientoModelo.route) {
                    ConocimientoModeloScreen(
                        onVolver = { navController.popBackStack() },
                        onReferencias = { navController.navigate(Screen.ReferenciasModelo.route) },
                    )
                }
                composable(Screen.ReferenciasModelo.route) {
                    ReferenciasScreen(onVolver = { navController.popBackStack() })
                }
                composable(Screen.MechanicChat.route) {
                    MechanicChatScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.navRoute) },
                        onOpenAiValue = { navController.navigate(Screen.AiValue.route) },
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
                        onNavigateBack = { navController.popBackStack() },
                        onOpenHealthCheck = { navController.navigate(Screen.HealthCheck.route) },
                        onOpenProfiles = { navController.navigate(Screen.VehicleProfile.route) },
                        onOpenMaintenance = { navController.navigate(Screen.Maintenance.route) },
                    )
                }
                composable(Screen.Maintenance.route) {
                    MaintenanceScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onElegirVehiculo = { navController.navigate(Screen.VehicleProfile.route) },
                    )
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
                composable(Screen.LiveMap.route) {
                    LiveMapScreen(
                        selectorVehiculo = selectorVehiculo,
                        // Único uso de onNavigateToSettings dentro de LiveMapScreen es el CTA
                        // del diálogo de descarga de mapa offline — abre Ajustes con la sección
                        // Mapa ya expandida en vez de aterrizar en la lista colapsada.
                        // navOptions de tab (popUpTo+saveState+launchSingleTop): un push liso
                        // dejaba a Settings PEGADO al chunk restaurable del tab Mapa — tocar la
                        // tab "Mapa" después restauraba [map, settings] y aterrizaba en Ajustes
                        // otra vez, en loop. Con semántica de tab, Settings es destino de tab
                        // normal y la tab Mapa vuelve al mapa.
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.withExpand("mapa")) {
                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Screen.AdapterScan.route) {
                    AdapterScanScreen(
                        onNavigateBack = { navController.popBackStack() },
                        connectionVm = connectionVm,
                    )
                }
                composable(Screen.GearAnalyzer.route) {
                    GearAnalyzerScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Sensors.route) {
                    SensorGraphScreen(
                        onNavigateBack = { navController.popBackStack() },
                        connectionVm = connectionVm,
                    )
                }
                composable(Screen.Dtc.route) {
                    DtcScreen(
                        onNavigateBack = { navController.popBackStack() },
                        connectionVm = connectionVm,
                        onOpenAiValue = { navController.navigate(Screen.AiValue.route) },
                        destinos = DestinosDtc(
                            onPrueba = { tipo -> navController.navigate(Screen.PruebaGuiada.withTipo(tipo.name)) },
                            onMultimetro = { sensor -> navController.navigate(Screen.Multimetro.withSensor(sensor.name)) },
                        ),
                    )
                }
                composable(Screen.Sessions.route) {
                    SessionHistoryScreen(
                        selectorVehiculo = selectorVehiculo,
                        onOpenSession = { sessionId ->
                            navController.navigate(Screen.SessionDetail.withId(sessionId))
                        },
                        onCompareSessions = { a, b ->
                            navController.navigate(Screen.SessionCompare.withIds(a, b))
                        },
                        onIniciarViaje = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = false }
                                launchSingleTop = true
                            }
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
                        onOpenAiValue = { navController.navigate(Screen.AiValue.route) },
                    )
                }
                composable(Screen.VehicleProfile.route) {
                    VehicleProfileScreen(
                        onNavigateBack = { navController.popBackStack() },
                        connectionVm = connectionVm,
                    )
                }
                composable(
                    route = Screen.Settings.route,
                    arguments = listOf(
                        navArgument(Screen.Settings.ARG_EXPAND) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { backStackEntry ->
                    SettingsScreen(
                        selectorVehiculo = selectorVehiculo,
                        onNavigateToVehicleProfiles = { navController.navigate(Screen.VehicleProfile.route) },
                        onOpenAiValue = { navController.navigate(Screen.AiValue.route) },
                        onRerunOnboarding = {
                            onboardingVm.goTo(0)
                            navController.navigate(Screen.Onboarding.route)
                        },
                        initialExpandedSection = backStackEntry.arguments?.getString(Screen.Settings.ARG_EXPAND),
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
