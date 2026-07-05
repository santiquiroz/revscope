package com.revscope.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onNavigateBack: () -> Unit = {},
    vm: SessionDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte de viaje", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            vm.exportCsv()?.let { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir viaje"))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar CSV", tint = AccentColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        containerColor = BgColor,
    ) { innerPadding ->
        when (val s = state) {
            SessionDetailViewModel.UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("Cargando…", color = TextMutedColor) }

            is SessionDetailViewModel.UiState.NotFound -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text(s.message, color = TextMutedColor) }

            is SessionDetailViewModel.UiState.Ready -> ReportContent(
                report = s.report,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ReportContent(
    report: SessionDetailViewModel.TripReport,
    modifier: Modifier = Modifier,
) {
    val session = report.session
    val durationMs = (session.endedAt ?: System.currentTimeMillis()) - session.startedAt
    val durationMin = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationSec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            dateFormat.format(Date(session.startedAt)),
            color = TextPrimaryColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(session.adapterName, color = TextMutedColor, fontSize = 12.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("Duración", "%dm %02ds".format(durationMin, durationSec), Modifier.weight(1f))
            StatCard("Distancia", "%.1f km".format(Locale("es"), session.distanceKm), Modifier.weight(1f))
            StatCard("Puntos", report.totalPoints.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("Vel. máx", "${session.maxSpeed} km/h", Modifier.weight(1f))
            StatCard("Vel. prom", "%.0f km/h".format(Locale("es"), report.avgSpeedKmh), Modifier.weight(1f))
            StatCard("Temp máx", "${report.maxCoolantTemp}°C", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("RPM máx", report.maxRpm.toString(), Modifier.weight(1f))
            StatCard("RPM prom", report.avgRpm.toString(), Modifier.weight(1f))
            StatCard("Acel. máx", "${report.maxThrottlePct}%", Modifier.weight(1f))
        }
        if (session.best0to60Ms != null || session.best0to100Ms != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    "🏁 0-60",
                    session.best0to60Ms?.let { "%.2fs".format(it / 1000.0) } ?: "—",
                    Modifier.weight(1f),
                )
                StatCard(
                    "🏁 0-100",
                    session.best0to100Ms?.let { "%.2fs".format(it / 1000.0) } ?: "—",
                    Modifier.weight(1f),
                )
            }
        }

        if (report.gpsTrack.size >= 2) {
            Text(
                "Recorrido GPS — %.1f km · máx %d km/h (GPS)".format(
                    java.util.Locale("es"), report.gpsDistanceKm, report.gpsMaxSpeedKmh,
                ),
                color = AccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            TrackMap(
                track = report.gpsTrack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }

        if (report.laps.isNotEmpty()) {
            val bestMs = report.laps.minOf { it.timeMs }
            Text(
                "Vueltas (${report.laps.size})",
                color = AccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            report.laps.forEach { lap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Vuelta ${lap.lapNumber}",
                        color = TextPrimaryColor,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    val minutes = lap.timeMs / 60_000
                    val seconds = (lap.timeMs % 60_000) / 1000
                    val hundredths = (lap.timeMs % 1000) / 10
                    Text(
                        "%d:%02d.%02d".format(minutes, seconds, hundredths) +
                            (if (lap.timeMs == bestMs) "  ★" else ""),
                        color = if (lap.timeMs == bestMs) Color(0xFF3DFF8E) else AccentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (report.rpmSeries.size >= 2) {
            ChartSection("RPM durante el viaje", report.rpmSeries)
        }
        if (report.speedSeries.size >= 2) {
            ChartSection("Velocidad durante el viaje (km/h)", report.speedSeries)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChartSection(title: String, series: List<Float>) {
    val modelProducer = remember(series) { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        modelProducer.runTransaction {
            lineSeries { series(y = series.map { it }) }
        }
    }
    Column {
        Text(
            title,
            color = AccentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        CartesianChartHost(
            chart = rememberCartesianChart(rememberLineCartesianLayer()),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = AccentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMutedColor, fontSize = 11.sp)
    }
}
