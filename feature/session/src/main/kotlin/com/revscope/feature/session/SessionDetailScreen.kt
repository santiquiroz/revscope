package com.revscope.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.trip.EcoScoreCalculator
import kotlinx.coroutines.launch
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
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
    val profiles by vm.profiles.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showVehicleDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val report = (state as? SessionDetailViewModel.UiState.Ready)?.report

    if (showVehicleDialog) {
        VehiclePickerDialog(
            profiles = profiles,
            onSelect = { profile ->
                vm.assignVehicle(profile.id)
                showVehicleDialog = false
            },
            onDismiss = { showVehicleDialog = false },
        )
    }

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
                    IconButton(onClick = { showVehicleDialog = true }) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = "Asignar vehículo", tint = AccentColor)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            vm.shareCardUri()?.let { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir imagen del viaje"))
                            }
                        }
                    }) {
                        Text("📷", fontSize = 18.sp)
                    }
                    ExportMenuButton(
                        expanded = showExportMenu,
                        onExpandedChange = { showExportMenu = it },
                        report = report,
                        onExportMetric = { metric -> vm.exportMetric(context, metric) },
                        onExportAll = {
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
                        },
                    )
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
                profiles = profiles,
                vm = vm,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private data class ExportMenuOption(
    val label: String,
    val metric: SessionDetailViewModel.ExportMetric,
    val hasData: Boolean,
)

@Composable
private fun ExportMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    report: SessionDetailViewModel.TripReport?,
    onExportMetric: (SessionDetailViewModel.ExportMetric) -> Unit,
    onExportAll: () -> Unit,
) {
    val options = exportMenuOptions(report)
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Default.Download, contentDescription = "Exportar…", tint = AccentColor)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    enabled = option.hasData,
                    onClick = {
                        onExpandedChange(false)
                        onExportMetric(option.metric)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Todo") },
                enabled = report != null,
                onClick = {
                    onExpandedChange(false)
                    onExportAll()
                },
            )
        }
    }
}

private fun exportMenuOptions(report: SessionDetailViewModel.TripReport?): List<ExportMenuOption> = listOf(
    ExportMenuOption("Velocidad", SessionDetailViewModel.ExportMetric.VELOCIDAD, report?.speedSeries?.isNotEmpty() == true),
    ExportMenuOption("RPM", SessionDetailViewModel.ExportMetric.RPM, report?.rpmSeries?.isNotEmpty() == true),
    ExportMenuOption("Temperatura", SessionDetailViewModel.ExportMetric.TEMPERATURA, (report?.maxCoolantTemp ?: 0) > 0),
    ExportMenuOption("Ritmo cardíaco", SessionDetailViewModel.ExportMetric.RITMO_CARDIACO, report?.hrSeries?.isNotEmpty() == true),
    ExportMenuOption("IMU", SessionDetailViewModel.ExportMetric.IMU, report?.frictionPoints?.isNotEmpty() == true),
    ExportMenuOption("GPS", SessionDetailViewModel.ExportMetric.GPS, report?.gpsTrack?.isNotEmpty() == true),
)

@Composable
private fun ReportContent(
    report: SessionDetailViewModel.TripReport,
    profiles: List<VehicleProfileEntity>,
    vm: SessionDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val session = report.session
    val durationMs = (session.endedAt ?: System.currentTimeMillis()) - session.startedAt
    val durationMin = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationSec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    val assignedVehicleName = profiles.find { it.id == session.vehicleProfileId }?.name ?: "Sin vehículo"

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
        Text(assignedVehicleName, color = AccentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(
            if (session.adapterName == "GPS") "Fuente: GPS" else session.adapterName,
            color = TextMutedColor,
            fontSize = 12.sp,
        )

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
        val fuelCostCop = session.fuelCostCop
        if (fuelCostCop != null) {
            StatCard(
                "Costo combustible",
                "$" + "%,.0f".format(Locale("es", "CO"), fuelCostCop),
                Modifier.fillMaxWidth(),
            )
        }

        val ecoScore = session.ecoScore
        if (ecoScore != null) {
            EcoCard(score = ecoScore, desglose = report.ecoDesglose)
        }

        DebriefCard(vm)

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
            RealTrackMap(
                track = report.gpsTrack,
                speeds = report.gpsTrackSpeeds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Text(
                "azul = lento · amarillo = medio · rojo = rápido",
                color = TextMutedColor,
                fontSize = 10.sp,
            )
            TrackMap(
                track = report.gpsTrack,
                speeds = report.gpsTrackSpeeds,
                brakingMask = report.brakingMask,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            Text(
                "racing line — puntos rojos = frenadas fuertes",
                color = TextMutedColor,
                fontSize = 10.sp,
            )
        }

        if (report.frictionPoints.isNotEmpty()) {
            Text(
                "Círculo de fricción — agarre usado (rojo = frenando)",
                color = AccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            FrictionCircle(
                points = report.frictionPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )
            Text(
                "anillos = 0.5G y 1.0G · arriba acelera · abajo frena · izq/der curvas",
                color = TextMutedColor,
                fontSize = 10.sp,
            )
        }

        if (report.maxLateralG != null || report.maxLeanDeg != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("G lat máx", report.maxLateralG?.let { "%.2f".format(it) } ?: "—", Modifier.weight(1f))
                StatCard("Frenada máx", report.maxBrakingG?.let { "%.2f G".format(-it) } ?: "—", Modifier.weight(1f))
                StatCard("Lean máx", report.maxLeanDeg?.let { "%.0f°".format(it) } ?: "—", Modifier.weight(1f))
            }
        }
        if (report.avgBpm != null || report.maxBpm != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("♥ prom", report.avgBpm?.toString() ?: "—", Modifier.weight(1f))
                StatCard("♥ máx", report.maxBpm?.toString() ?: "—", Modifier.weight(1f))
            }
        }

        if (report.laps.isNotEmpty()) {
            val bestMs = report.laps.minOf { it.timeMs }
            Text(
                "Vueltas (${report.laps.size})",
                color = AccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            report.laps.forEachIndexed { index, lap ->
                val stat = report.lapStats.getOrNull(index)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vuelta ${lap.lapNumber}", color = TextPrimaryColor, fontSize = 13.sp)
                        if (stat?.maxAbsG != null || stat?.maxAbsLean != null || stat?.maxBpm != null) {
                            Text(
                                buildList {
                                    stat.maxAbsG?.let { add("%.2fG".format(it)) }
                                    stat.maxAbsLean?.let { add("%.0f° lean".format(it)) }
                                    stat.maxBpm?.let { add("♥%.0f".format(it)) }
                                }.joinToString(" · "),
                                color = TextMutedColor,
                                fontSize = 11.sp,
                            )
                        }
                    }
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

        if (report.throttleGPoints.size >= 10) {
            Text(
                "Acelerador vs fuerza G (tuning)",
                color = AccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            ThrottleGScatter(
                points = report.throttleGPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            Text(
                "x: % acelerador · y: G (+acelera / −frena) · banda diagonal sana = entrega pareja",
                color = TextMutedColor,
                fontSize = 10.sp,
            )
        }

        if (report.rpmSeries.size >= 2) {
            ChartSection("RPM durante el viaje", report.rpmSeries, unit = "RPM", durationMs = durationMs)
        }
        if (report.speedSeries.size >= 2) {
            ChartSection("Velocidad durante el viaje (km/h)", report.speedSeries, unit = "km/h", durationMs = durationMs)
        }
        if (report.hrSeries.size >= 2) {
            ChartSection("♥ Pulso del piloto (bpm)", report.hrSeries, unit = "bpm", durationMs = durationMs)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ChartSection(title: String, series: List<Float>, unit: String, durationMs: Long) {
    val modelProducer = remember(series) { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        modelProducer.runTransaction {
            lineSeries { series(y = series.map { it }) }
        }
    }
    val bottomFormatter = remember(series.size, durationMs) {
        elapsedFractionFormatter(series.size, durationMs)
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
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(title = unit),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = bottomFormatter,
                    title = "tiempo (mm:ss)",
                ),
            ),
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

/** Análisis IA del viaje: agregados locales → una llamada corta al proveedor configurado. */
@Composable
private fun DebriefCard(vm: SessionDetailViewModel) {
    val debrief by vm.debrief.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🤖 Análisis IA", color = TextPrimaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            when (debrief) {
                is SessionDetailViewModel.DebriefState.Generating ->
                    Text("Generando…", color = TextMutedColor, fontSize = 12.sp)
                else -> Text(
                    if (debrief is SessionDetailViewModel.DebriefState.Ready) "Regenerar" else "Analizar",
                    color = AccentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { vm.generateDebrief() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        when (val d = debrief) {
            is SessionDetailViewModel.DebriefState.Ready -> Text(
                d.text, color = TextPrimaryColor, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            is SessionDetailViewModel.DebriefState.Error -> Text(
                d.message, color = Color(0xFFFF5252), fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> Text(
                "Resumen y consejos de tu coach IA con los datos de este viaje, comparados con tu historial.",
                color = TextMutedColor, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EcoCard(score: Int, desglose: EcoScoreCalculator.Desglose?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Eco",
                color = AccentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("$score", color = ecoScoreColor(score), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        if (desglose != null) {
            Text(
                "Aceleradas bruscas: ${desglose.aceleradasBruscas}  ·  Frenadas bruscas: ${desglose.frenadasBruscas}",
                color = TextMutedColor,
                fontSize = 11.sp,
            )
            Text(
                "RPM alto sostenido: ${desglose.tiempoAltasRpmSeg} s  ·  Bonus crucero: +${desglose.bonusCrucero}",
                color = TextMutedColor,
                fontSize = 11.sp,
            )
        } else {
            Text("Desglose no disponible para este viaje", color = TextMutedColor, fontSize = 11.sp)
        }
    }
}

private fun ecoScoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF3DFF8E)
    score >= 50 -> Color(0xFFE8FF00)
    else -> Color(0xFFFF5C5C)
}

@Composable
private fun VehiclePickerDialog(
    profiles: List<VehicleProfileEntity>,
    onSelect: (VehicleProfileEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("Asignar vehículo", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
        text = {
            if (profiles.isEmpty()) {
                Text("No hay vehículos guardados", color = TextMutedColor, fontSize = 13.sp)
            } else {
                Column {
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(profile) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (profile.type == "MOTORCYCLE") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = AccentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(profile.name, color = TextPrimaryColor, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = TextMutedColor, fontSize = 13.sp)
            }
        },
    )
}
