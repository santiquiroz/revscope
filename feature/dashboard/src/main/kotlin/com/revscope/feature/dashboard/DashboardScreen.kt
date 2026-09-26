package com.revscope.feature.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.data.db.entities.vehicleType
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow

private const val AVISO_VISIBLE_MS = 5_000L

@Composable
fun DashboardScreen(
    onNavigateToAdapterScan: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTrackMode: () -> Unit = {},
    onNavigateToAlDia: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    selectorVehiculo: @Composable () -> Unit = {},
    connectionVm: ConnectionViewModel = hiltViewModel(),
    dashboardVm: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val connectionState by connectionVm.connectionState.collectAsState()
    val readingsState = connectionVm.readings.collectAsState()
    val tripScore by dashboardVm.tripScore.collectAsState()
    val gearCalibrated by dashboardVm.gearCalibrated.collectAsState()
    val alDiaBanner by dashboardVm.alDiaBanner.collectAsState()
    val update by dashboardVm.updateAvailable.collectAsState()
    val isGpsTrip by connectionVm.isGpsTripActive.collectAsState()
    val speedSourceGps by dashboardVm.speedSourceGps.collectAsState()
    val estadoViaje by connectionVm.estadoViaje.collectAsState()
    val avisoViaje = rememberAvisoTemporal(connectionVm)
    val activeAlert = rememberAlertaActiva(connectionVm)

    // D2: elegido en el wizard o inferido por ausencia de adaptador configurado (VM) — sin
    // conexión OBD activa: invertir jerarquía — viaje GPS + mapa arriba, gauges (sin datos
    // posibles) abajo con una sola CTA.
    val gpsHeroMode by dashboardVm.gpsHeroMode.collectAsState()

    // Start intelligence once connected; restart on reconnect
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            val readingsFlow = channelFlow {
                connectionVm.readings.collect { map ->
                    map.values.forEach { send(it) }
                }
            }
            dashboardVm.startIntelligence(readingsFlow, connectionVm)
        } else {
            dashboardVm.stopIntelligence()
        }
    }

    // derivedStateOf: the readings map mutates ~20×/s, but each gauge only recomposes
    // when ITS value actually changes — not on every unrelated PID update.
    val rpm by remember { derivedStateOf { valorDe(readingsState.value, "0C") } }
    val speed by remember {
        derivedStateOf {
            val useGpsSpeed = isGpsTrip ||
                (speedSourceGps && readingsState.value.containsKey(ObdSessionManager.GPS_SPEED_PID))
            val speedPid = if (useGpsSpeed) ObdSessionManager.GPS_SPEED_PID else "0D"
            valorDe(readingsState.value, speedPid)
        }
    }
    val temp by remember { derivedStateOf { valorDe(readingsState.value, "05") } }
    val boost by remember { derivedStateOf { valorDe(readingsState.value, "BOOST") } }
    val gear by remember { derivedStateOf { readingsState.value["GEAR"]?.value?.toInt() ?: 0 } }
    val vbat by remember { derivedStateOf { readingsState.value["VBAT"]?.value } }

    // Riding with the screen off is useless — keep it on while telemetry flows.
    // Gateado por ajuste: en carro montado en soporte, horas de pantalla forzada
    // son el mayor drenaje de batería de todo el sistema.
    val keepScreenOnSetting by dashboardVm.keepScreenOn.collectAsState()
    val view = LocalView.current
    DisposableEffect(connectionState, keepScreenOnSetting) {
        view.keepScreenOn = keepScreenOnSetting && connectionState is ConnectionState.Connected
        onDispose { view.keepScreenOn = false }
    }

    // Active vehicle profile drives gauge scale and redline; falls back to globals
    val activeProfile by connectionVm.activeProfile.collectAsState()
    val redline = (activeProfile?.redlineRpm ?: dashboardVm.redlineRpm).toFloat()

    // Shift light: warn at 95% of redline, screaming red past it
    val shiftLightColor by remember {
        derivedStateOf {
            when {
                rpm >= redline -> RevScopeColors.Danger
                rpm >= redline * 0.95f -> RevScopeColors.Accent
                else -> null
            }
        }
    }

    val estado = EstadoConducirUi(
        versionNueva = update?.version,
        avisoAlDia = alDiaBanner,
        alertaActiva = activeAlert,
        // "Viaje sin adaptador": only offered while there's no live/incoming BT link, so it
        // can never race connectToDevice()'s own GPS-session handover.
        ofrecerViajeGps = connectionState is ConnectionState.Disconnected || connectionState is ConnectionState.Error,
        viajeGpsActivo = isGpsTrip,
        modoGpsHero = gpsHeroMode,
        conectado = connectionState is ConnectionState.Connected,
        estadoViaje = estadoViaje,
        avisoViaje = avisoViaje,
        velocidadPorGps = speedSourceGps,
        lecturas = LecturasConducir(rpm = rpm, velocidad = speed, temperatura = temp, boost = boost, marcha = gear),
        escalas = EscalasConducir(
            maxRpm = activeProfile?.maxRpm ?: 8000,
            redlineRpm = redline.toInt(),
            maxVelocidad = if (activeProfile?.vehicleType == VehicleType.MOTORCYCLE) 299 else 260,
            marchas = activeProfile?.gearCount ?: 6,
            marchasCalibradas = gearCalibrated,
        ),
        puntaje = tripScore,
    )
    val acciones = remember(connectionVm, dashboardVm) {
        AccionesConducir(
            onDescargarActualizacion = {
                dashboardVm.updateAvailable.value?.let { abrirEnNavegador(context, it.apkUrl ?: it.releaseUrl) }
            },
            onDescartarActualizacion = dashboardVm::dismissUpdate,
            onAbrirAlDia = onNavigateToAlDia,
            onIniciarViajeGps = connectionVm::startGpsTrip,
            onFinalizarViajeGps = connectionVm::stopGpsTrip,
            onAbrirMapa = onOpenMap,
            onConfigurarAdaptador = onNavigateToAdapterScan,
            onFinalizarViaje = connectionVm::finalizarViaje,
            onIniciarViaje = connectionVm::iniciarViaje,
            onDesconectar = connectionVm::disconnect,
            onAlternarFuenteVelocidad = { dashboardVm.setSpeedSourceGps(!dashboardVm.speedSourceGps.value) },
        )
    }

    Scaffold(
        modifier = shiftLightColor?.let { Modifier.border(6.dp, it) } ?: Modifier,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            DashboardTopBar(
                enlace = enlaceAdaptador(connectionState),
                voltaje = vbat,
                selectorVehiculo = selectorVehiculo,
                onAdaptador = onNavigateToAdapterScan,
                onModoPista = onNavigateToTrackMode,
                onAjustes = onNavigateToSettings,
            )
        },
        containerColor = RevScopeColors.Background,
    ) { innerPadding ->
        DashboardContent(
            estado = estado,
            acciones = acciones,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

private fun valorDe(lecturas: Map<String, ObdReading>, pid: String): Float =
    (lecturas[pid]?.value ?: 0.0).toFloat()

private fun abrirEnNavegador(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun rememberAvisoTemporal(connectionVm: ConnectionViewModel): String? {
    var aviso by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { connectionVm.eventosViaje.collect { aviso = it } }
    LaunchedEffect(aviso) {
        if (aviso != null) {
            delay(AVISO_VISIBLE_MS)
            aviso = null
        }
    }
    return aviso
}

@Composable
private fun rememberAlertaActiva(connectionVm: ConnectionViewModel): String? {
    var alerta by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        connectionVm.alerts.collect { alert -> alerta = alert.message }
    }
    LaunchedEffect(Unit) {
        connectionVm.launchResults.collect { result ->
            alerta = textoArrancada(result.to60Ms, result.to100Ms) ?: alerta
        }
    }
    LaunchedEffect(alerta) {
        if (alerta != null) {
            delay(AVISO_VISIBLE_MS)
            alerta = null
        }
    }
    return alerta
}

internal fun textoArrancada(to60Ms: Long?, to100Ms: Long?): String? = when {
    to100Ms != null -> "🏁 0-100 en %.2fs".format(to100Ms / 1000.0) +
        (to60Ms?.let { "  (0-60: %.2fs)".format(it / 1000.0) } ?: "")
    to60Ms != null -> "🏁 0-60 en %.2fs".format(to60Ms / 1000.0)
    else -> null
}
