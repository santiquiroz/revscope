package com.revscope.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.intelligence.efficiency.TripScore
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import com.revscope.feature.dashboard.gauges.BoostBar
import com.revscope.feature.dashboard.gauges.GearDisplay
import com.revscope.feature.dashboard.gauges.RpmGauge
import com.revscope.feature.dashboard.gauges.SpeedGauge
import com.revscope.feature.dashboard.gauges.TempGauge
import com.revscope.feature.dashboard.ui.RevScopeColors
import kotlinx.coroutines.flow.channelFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAdapterScan: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTrackMode: () -> Unit = {},
    onNavigateToAlDia: () -> Unit = {},
    connectionVm: ConnectionViewModel = hiltViewModel(),
    dashboardVm: DashboardViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connectionState by connectionVm.connectionState.collectAsState()
    val readingsState = connectionVm.readings.collectAsState()
    val tripScore by dashboardVm.tripScore.collectAsState()
    val gearCalibrated by dashboardVm.gearCalibrated.collectAsState()
    val alDiaBanner by dashboardVm.alDiaBanner.collectAsState()
    val update by dashboardVm.updateAvailable.collectAsState()
    val isGpsTrip by connectionVm.isGpsTripActive.collectAsState()
    val speedSourceGps by dashboardVm.speedSourceGps.collectAsState()

    // "Viaje sin adaptador": only offered while there's no live/incoming BT link, so it
    // can never race connectToDevice()'s own GPS-session handover.
    val showGpsTripButton = connectionState is ConnectionState.Disconnected ||
        connectionState is ConnectionState.Error

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
    val rpm by remember { derivedStateOf { (readingsState.value["0C"]?.value ?: 0.0).toFloat() } }
    val speed by remember {
        derivedStateOf {
            val useGpsSpeed = isGpsTrip ||
                (speedSourceGps && readingsState.value.containsKey(ObdSessionManager.GPS_SPEED_PID))
            val speedPid = if (useGpsSpeed) ObdSessionManager.GPS_SPEED_PID else "0D"
            (readingsState.value[speedPid]?.value ?: 0.0).toFloat()
        }
    }
    val temp by remember { derivedStateOf { (readingsState.value["05"]?.value ?: 0.0).toFloat() } }
    val boost by remember { derivedStateOf { (readingsState.value["BOOST"]?.value ?: 0.0).toFloat() } }
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
    val gaugeMaxRpm = activeProfile?.maxRpm ?: 8000

    // Shift light: warn at 95% of redline, screaming red past it
    val redline = (activeProfile?.redlineRpm ?: dashboardVm.redlineRpm).toFloat()
    val shiftLightColor by remember {
        derivedStateOf {
            when {
                rpm >= redline -> RevScopeColors.Danger
                rpm >= redline * 0.95f -> RevScopeColors.Accent
                else -> null
            }
        }
    }

    var activeAlert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        connectionVm.alerts.collect { alert ->
            activeAlert = alert.message
        }
    }
    LaunchedEffect(Unit) {
        connectionVm.launchResults.collect { result ->
            activeAlert = when {
                result.to100Ms != null -> "🏁 0-100 en %.2fs".format(result.to100Ms!! / 1000.0) +
                    (result.to60Ms?.let { "  (0-60: %.2fs)".format(it / 1000.0) } ?: "")
                result.to60Ms != null -> "🏁 0-60 en %.2fs".format(result.to60Ms!! / 1000.0)
                else -> activeAlert
            }
        }
    }
    LaunchedEffect(activeAlert) {
        if (activeAlert != null) {
            delay(5_000)
            activeAlert = null
        }
    }

    Scaffold(
        modifier = shiftLightColor?.let { Modifier.border(6.dp, it) } ?: Modifier,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "RevScope",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RevScopeColors.TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToAdapterScan) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth",
                            tint = when (connectionState) {
                                is ConnectionState.Connected -> RevScopeColors.Success
                                is ConnectionState.Connecting -> RevScopeColors.Warning
                                is ConnectionState.Error -> RevScopeColors.Danger
                                else -> RevScopeColors.TextMuted
                            }
                        )
                    }
                },
                actions = {
                    vbat?.let { volts ->
                        Text(
                            text = "%.1fV".format(volts),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (volts < 11.8) RevScopeColors.Danger else RevScopeColors.TextMuted,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    IconButton(onClick = onNavigateToTrackMode) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Modo Pista",
                            tint = RevScopeColors.TextMuted,
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = RevScopeColors.TextMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RevScopeColors.Surface,
                ),
            )
        },
        containerColor = RevScopeColors.Background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            update?.let { info ->
                UpdateBanner(
                    version = info.version,
                    onDownload = {
                        val url = info.apkUrl ?: info.releaseUrl
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            )
                        }
                    },
                    onDismiss = { dashboardVm.dismissUpdate() },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            alDiaBanner?.let { message ->
                Text(
                    text = "⚠ $message",
                    color = RevScopeColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToAlDia)
                        .background(RevScopeColors.Danger, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            activeAlert?.let { message ->
                Text(
                    text = "⚠ $message",
                    color = RevScopeColors.Background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RevScopeColors.Danger, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (showGpsTripButton) {
                GpsTripButton(
                    isActive = isGpsTrip,
                    onStart = connectionVm::startGpsTrip,
                    onStop = connectionVm::stopGpsTrip,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            RpmGauge(
                rpm = rpm,
                maxRpm = gaugeMaxRpm,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .then(dimmedIfGpsTrip(isGpsTrip)),
            )

            if (isGpsTrip) {
                Text(
                    "RPM · temperatura · marcha · boost — requieren adaptador",
                    color = RevScopeColors.TextMuted,
                    fontSize = 11.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SpeedGauge(speed = speed)
                    if (connectionState is ConnectionState.Connected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SpeedSourceChip(
                            useGps = speedSourceGps,
                            onToggle = { dashboardVm.setSpeedSourceGps(!speedSourceGps) },
                        )
                    }
                }
                GearDisplay(
                    gear = gear,
                    isCalibrated = gearCalibrated,
                    modifier = Modifier.weight(0.6f).then(dimmedIfGpsTrip(isGpsTrip)),
                )
                TempGauge(
                    tempCelsius = temp,
                    modifier = Modifier.weight(0.6f).then(dimmedIfGpsTrip(isGpsTrip)),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BoostBar(
                boostKpa = boost,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .then(dimmedIfGpsTrip(isGpsTrip)),
            )

            Spacer(modifier = Modifier.height(12.dp))

            TripScoreBar(tripScore = tripScore)
        }
    }
}

private const val DIMMED_GAUGE_ALPHA = 0.35f

/** No-op when [isGpsTrip] is false, so the connected/OBD path never gains an extra modifier. */
private fun dimmedIfGpsTrip(isGpsTrip: Boolean): Modifier =
    if (isGpsTrip) Modifier.alpha(DIMMED_GAUGE_ALPHA) else Modifier

@Composable
private fun GpsTripButton(
    isActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Button(
        onClick = if (isActive) onStop else onStart,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) RevScopeColors.Danger else RevScopeColors.Accent,
        ),
    ) {
        Text(
            text = if (isActive) "Finalizar viaje GPS" else "Iniciar viaje GPS",
            color = RevScopeColors.Background,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Aviso de nueva versión en GitHub — descargar (abre navegador al APK) o descartar. */
@Composable
private fun UpdateBanner(version: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Nueva versión $version disponible", color = RevScopeColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Toca Descargar para actualizar", color = RevScopeColors.TextMuted, fontSize = 11.sp)
        }
        Text(
            "Descargar",
            color = RevScopeColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onDownload).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            "✕",
            color = RevScopeColors.TextMuted,
            fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** Clickable "OBD"/"GPS" pill under the speedometer — swaps its live source. */
@Composable
private fun SpeedSourceChip(
    useGps: Boolean,
    onToggle: () -> Unit,
) {
    Text(
        text = if (useGps) "GPS" else "OBD",
        color = if (useGps) RevScopeColors.Accent else RevScopeColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(onClick = onToggle)
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun TripScoreBar(tripScore: TripScore) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${tripScore.style.emoji} ${tripScore.style.label}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RevScopeColors.TextPrimary,
            )
            Text(
                text = "Score: ${tripScore.overall}/100",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = RevScopeColors.Accent,
            )
        }
    }
}
