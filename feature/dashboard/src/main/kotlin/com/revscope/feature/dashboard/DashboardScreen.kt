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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.intelligence.efficiency.TripScore
import com.revscope.core.obd.connection.ConnectionState
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
    val connectionState by connectionVm.connectionState.collectAsState()
    val readingsState = connectionVm.readings.collectAsState()
    val tripScore by dashboardVm.tripScore.collectAsState()
    val gearCalibrated by dashboardVm.gearCalibrated.collectAsState()
    val alDiaBanner by dashboardVm.alDiaBanner.collectAsState()

    // Start intelligence once connected; restart on reconnect
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            val readingsFlow = channelFlow {
                connectionVm.readings.collect { map ->
                    map.values.forEach { send(it) }
                }
            }
            dashboardVm.startIntelligence(readingsFlow, connectionVm)
        }
    }

    // derivedStateOf: the readings map mutates ~20×/s, but each gauge only recomposes
    // when ITS value actually changes — not on every unrelated PID update.
    val rpm by remember { derivedStateOf { (readingsState.value["0C"]?.value ?: 0.0).toFloat() } }
    val speed by remember { derivedStateOf { (readingsState.value["0D"]?.value ?: 0.0).toFloat() } }
    val temp by remember { derivedStateOf { (readingsState.value["05"]?.value ?: 0.0).toFloat() } }
    val boost by remember { derivedStateOf { (readingsState.value["BOOST"]?.value ?: 0.0).toFloat() } }
    val gear by remember { derivedStateOf { readingsState.value["GEAR"]?.value?.toInt() ?: 0 } }
    val vbat by remember { derivedStateOf { readingsState.value["VBAT"]?.value } }

    // Riding with the screen off is useless — keep it on while telemetry flows
    val view = LocalView.current
    DisposableEffect(connectionState) {
        view.keepScreenOn = connectionState is ConnectionState.Connected
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
            RpmGauge(
                rpm = rpm,
                maxRpm = gaugeMaxRpm,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedGauge(
                    speed = speed,
                    modifier = Modifier.weight(1f),
                )
                GearDisplay(
                    gear = gear,
                    isCalibrated = gearCalibrated,
                    modifier = Modifier.weight(0.6f),
                )
                TempGauge(
                    tempCelsius = temp,
                    modifier = Modifier.weight(0.6f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BoostBar(
                boostKpa = boost,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            TripScoreBar(tripScore = tripScore)
        }
    }
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
