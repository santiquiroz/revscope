package com.revscope.feature.session

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import androidx.annotation.StringRes
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.Region
import com.revscope.core.common.i18n.detect
import com.revscope.core.common.i18n.formatRegionalMoney
import com.revscope.core.common.i18n.systemLocale
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.rememberUnitFormatter
import com.revscope.core.obd.trip.EcoScoreCalculator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onNavigateBack: () -> Unit = {},
    onOpenAiValue: () -> Unit = {},
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
                title = { Text(stringResource(R.string.trips_trip_report), color = RevScopeColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.trips_back), tint = RevScopeColors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showVehicleDialog = true }) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = stringResource(R.string.trips_assign_vehicle), tint = RevScopeColors.Accent)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            vm.shareCardUri()?.let { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.trips_trip_image_share_chooser)))
                            }
                        }
                    }) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.trips_trip_image_share_chooser),
                            tint = RevScopeColors.TextPrimary,
                        )
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
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.trips_trip_share_chooser)))
                                }
                            }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RevScopeColors.Surface),
            )
        },
        containerColor = RevScopeColors.Background,
    ) { innerPadding ->
        when (val s = state) {
            SessionDetailViewModel.UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.trips_loading), color = RevScopeColors.TextSecondary) }

            is SessionDetailViewModel.UiState.NotFound -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text(s.message, color = RevScopeColors.TextSecondary) }

            is SessionDetailViewModel.UiState.Ready -> ReportContent(
                report = s.report,
                profiles = profiles,
                vm = vm,
                locale = LocalConfiguration.current.locales[0],
                onOpenAiValue = onOpenAiValue,
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
            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.trips_export), tint = RevScopeColors.Accent)
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
                text = { Text(stringResource(R.string.trips_export_all)) },
                enabled = report != null,
                onClick = {
                    onExpandedChange(false)
                    onExportAll()
                },
            )
        }
    }
}

@Composable
private fun exportMenuOptions(report: SessionDetailViewModel.TripReport?): List<ExportMenuOption> = listOf(
    ExportMenuOption(stringResource(R.string.trips_export_speed), SessionDetailViewModel.ExportMetric.VELOCIDAD, report?.speedSeries?.isNotEmpty() == true),
    ExportMenuOption(stringResource(R.string.trips_export_rpm), SessionDetailViewModel.ExportMetric.RPM, report?.rpmSeries?.isNotEmpty() == true),
    ExportMenuOption(stringResource(R.string.trips_export_temp), SessionDetailViewModel.ExportMetric.TEMPERATURA, (report?.maxCoolantTemp ?: 0) > 0),
    ExportMenuOption(stringResource(R.string.trips_export_heart), SessionDetailViewModel.ExportMetric.RITMO_CARDIACO, report?.hrSeries?.isNotEmpty() == true),
    ExportMenuOption(stringResource(R.string.trips_export_imu), SessionDetailViewModel.ExportMetric.IMU, report?.frictionPoints?.isNotEmpty() == true),
    ExportMenuOption(stringResource(R.string.trips_export_gps), SessionDetailViewModel.ExportMetric.GPS, report?.gpsTrack?.isNotEmpty() == true),
)

@Composable
private fun ReportContent(
    report: SessionDetailViewModel.TripReport,
    profiles: List<VehicleProfileEntity>,
    vm: SessionDetailViewModel,
    locale: Locale,
    onOpenAiValue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = report.session
    val durationMs = (session.endedAt ?: System.currentTimeMillis()) - session.startedAt
    val durationMin = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationSec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    val assignedVehicleName = profiles.find { it.id == session.vehicleProfileId }?.name ?: stringResource(R.string.trips_no_vehicle)
    val isMotorcycle by vm.isMotorcycle.collectAsState()
    val sessionDateFormat = remember(locale) { SimpleDateFormat("dd MMM yyyy  HH:mm", locale) } // i18n-ignore
    val units = rememberUnitFormatter()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            sessionDateFormat.format(Date(session.startedAt)),
            color = RevScopeColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(assignedVehicleName, color = RevScopeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            if (session.adapterName == "GPS") stringResource(R.string.trips_trip_source_gps) else session.adapterName,
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                stringResource(R.string.trips_duration),
                stringResource(R.string.trips_duration_format, durationMin, durationSec),
                Modifier.weight(1f),
            )
            StatCard(
                stringResource(R.string.trips_distance),
                units.formatDistance(session.distanceKm.toDouble()),
                Modifier.weight(1f),
            )
            StatCard(stringResource(R.string.trips_points), report.totalPoints.toString(), Modifier.weight(1f))
        }
        SpeedTempRow(session.maxSpeed, report.avgSpeedKmh, report.maxCoolantTemp, units)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(stringResource(R.string.trips_max_rpm), report.maxRpm.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.trips_avg_rpm), report.avgRpm.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.trips_max_accel), "${report.maxThrottlePct}%", Modifier.weight(1f))
        }
        val context = LocalContext.current
        val region = remember { Region.detect(context) }
        FuelCostCard(session.fuelCostCop, region, systemLocale(), locale)

        val ecoScore = session.ecoScore
        if (ecoScore != null) {
            EcoCard(score = ecoScore, desglose = report.ecoDesglose)
        }

        DebriefCard(vm, onOpenAiValue)

        if (session.best0to60Ms != null || session.best0to100Ms != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    sprintLabel(R.string.trips_0_60, SPRINT_SHORT_KMH, units),
                    session.best0to60Ms?.let { "%.2fs".format(Locale.ROOT, it / 1000.0) } ?: "—",
                    Modifier.weight(1f),
                )
                StatCard(
                    sprintLabel(R.string.trips_0_100, SPRINT_LONG_KMH, units),
                    session.best0to100Ms?.let { "%.2fs".format(Locale.ROOT, it / 1000.0) } ?: "—",
                    Modifier.weight(1f),
                )
            }
        }

        if (report.gpsTrack.size >= 2) {
            Text(
                routeSummary(report.gpsDistanceKm, report.gpsMaxSpeedKmh.toDouble(), units),
                color = RevScopeColors.Accent,
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
                stringResource(R.string.trips_route_legend),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
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
                stringResource(R.string.trips_route_racing),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        }

        if (report.frictionPoints.isNotEmpty()) {
            Text(
                stringResource(R.string.trips_friction_circle),
                color = RevScopeColors.Accent,
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
                stringResource(R.string.trips_friction_legend),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        }

        if (report.maxLateralG != null || (isMotorcycle && report.maxLeanDeg != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    stringResource(R.string.trips_max_g_lat),
                    report.maxLateralG?.let { LocaleNumbers.decimal(it.toDouble(), 2, locale) } ?: "—",
                    Modifier.weight(1f),
                )
                StatCard(
                    stringResource(R.string.trips_max_braking),
                    report.maxBrakingG?.let {
                        stringResource(R.string.trips_g_value, LocaleNumbers.decimal((-it).toDouble(), 2, locale))
                    } ?: "—",
                    Modifier.weight(1f),
                )
                if (isMotorcycle) {
                    StatCard(
                        stringResource(R.string.trips_lean_max),
                        report.maxLeanDeg?.let {
                            stringResource(R.string.trips_lean_value, LocaleNumbers.decimal(it.toDouble(), 0, locale))
                        } ?: "—",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        if (report.avgBpm != null || report.maxBpm != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(stringResource(R.string.trips_avg_heart_short), report.avgBpm?.toString() ?: "—", Modifier.weight(1f))
                StatCard(stringResource(R.string.trips_max_heart_short), report.maxBpm?.toString() ?: "—", Modifier.weight(1f))
            }
        }

        if (report.laps.isNotEmpty()) {
            val bestMs = report.laps.minOf { it.timeMs }
            Text(
                stringResource(R.string.trips_laps, report.laps.size),
                color = RevScopeColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            report.laps.forEachIndexed { index, lap ->
                val stat = report.lapStats.getOrNull(index)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.trips_lap_label, lap.lapNumber), color = RevScopeColors.TextPrimary, fontSize = 13.sp)
                        if (stat?.maxAbsG != null || (isMotorcycle && stat?.maxAbsLean != null) || stat?.maxBpm != null) {
                            Text(
                                buildList {
                                    stat.maxAbsG?.let {
                                        add(stringResource(R.string.trips_lap_g, LocaleNumbers.decimal(it.toDouble(), 2, locale)))
                                    }
                                    if (isMotorcycle) stat.maxAbsLean?.let {
                                        add(stringResource(R.string.trips_lean_value, LocaleNumbers.decimal(it.toDouble(), 0, locale)))
                                    }
                                    stat.maxBpm?.let { add("♥%.0f".format(it)) }
                                }.joinToString(" · "),
                                color = RevScopeColors.TextSecondary,
                                style = RevScopeType.bodySmall,
                            )
                        }
                    }
                    val minutes = lap.timeMs / 60_000
                    val seconds = (lap.timeMs % 60_000) / 1000
                    val hundredths = (lap.timeMs % 1000) / 10
                    Text(
                        "%d:%02d.%02d".format(Locale.ROOT, minutes, seconds, hundredths) +
                            (if (lap.timeMs == bestMs) "  ★" else ""),
                        color = if (lap.timeMs == bestMs) Color(0xFF3DFF8E) else RevScopeColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (report.throttleGPoints.size >= 10) {
            Text(
                stringResource(R.string.trips_throttle_g),
                color = RevScopeColors.Accent,
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
                stringResource(R.string.trips_throttle_g_legend),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        }

        if (report.rpmSeries.size >= 2) {
            ChartSection(stringResource(R.string.trips_rpm_chart), report.rpmSeries, unit = "RPM", durationMs = durationMs)
        }
        if (report.speedSeries.size >= 2) {
            ChartSection(
                stringResource(R.string.trips_speed_chart, units.speedLabel()),
                remember(report.speedSeries, units) { speedSeriesIn(report.speedSeries, units) },
                unit = units.speedLabel(),
                durationMs = durationMs,
            )
        }
        if (report.hrSeries.size >= 2) {
            ChartSection(stringResource(R.string.trips_heart_chart), report.hrSeries, unit = "bpm", durationMs = durationMs)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun SpeedTempRow(maxSpeedKmh: Int, avgSpeedKmh: Float, maxCoolantC: Int, units: UnitFormatter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(stringResource(R.string.trips_max_speed), units.formatSpeed(maxSpeedKmh.toDouble()), Modifier.weight(1f))
        StatCard(stringResource(R.string.trips_avg_speed), units.formatSpeed(avgSpeedKmh.toDouble()), Modifier.weight(1f))
        StatCard(stringResource(R.string.trips_max_temp), units.formatTemperature(maxCoolantC.toDouble()), Modifier.weight(1f))
    }
}

internal const val SPRINT_SHORT_KMH = 60.0
internal const val SPRINT_LONG_KMH = 100.0

@Composable
internal fun sprintLabel(@StringRes metricLabel: Int, kmh: Double, units: UnitFormatter): String =
    if (units.system == UnitSystem.METRIC) {
        stringResource(metricLabel)
    } else {
        stringResource(R.string.trips_units_sprint, units.formatSpeed(kmh))
    }

@Composable
internal fun routeSummary(distanceKm: Double, maxSpeedKmh: Double, units: UnitFormatter): String =
    stringResource(R.string.trips_route_summary, units.formatDistance(distanceKm), units.formatSpeed(maxSpeedKmh))

internal fun speedSeriesIn(seriesKmh: List<Float>, units: UnitFormatter): List<Float> =
    seriesKmh.map { units.speed(it.toDouble()).toFloat() }

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
            color = RevScopeColors.Accent,
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
                    title = stringResource(R.string.trips_time_axis),
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
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = RevScopeColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(label, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@Composable
internal fun FuelCostCard(cost: Double?, region: Region, systemLocale: Locale, uiLocale: Locale) {
    if (cost == null) return
    StatCard(
        stringResource(R.string.trips_fuel_cost),
        formatRegionalMoney(cost, region, systemLocale, uiLocale),
        Modifier.fillMaxWidth(),
    )
}

/** Análisis IA del viaje: agregados locales → una llamada corta al proveedor configurado. */
@Composable
private fun DebriefCard(vm: SessionDetailViewModel, onOpenAiValue: () -> Unit) {
    val debrief by vm.debrief.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.trips_ai_analysis), color = RevScopeColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            when (debrief) {
                is SessionDetailViewModel.DebriefState.Generating ->
                    Text(stringResource(R.string.trips_generating), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
                else -> Text(
                    stringResource(if (debrief is SessionDetailViewModel.DebriefState.Ready) R.string.trips_regenerate else R.string.trips_analyze),
                    color = RevScopeColors.Accent,
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
                d.text, color = RevScopeColors.TextPrimary, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            is SessionDetailViewModel.DebriefState.Error -> Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(d.message, color = Color(0xFFFF5252), fontSize = 12.sp)
                TextButton(onClick = onOpenAiValue, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.trips_configure_ai), color = RevScopeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> Text(
                stringResource(R.string.trips_ai_summary),
                color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall,
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
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.trips_eco),
                color = RevScopeColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("$score", color = ecoScoreColor(score), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        if (desglose != null) {
            Text(
                stringResource(R.string.trips_eco_accel_brakes, desglose.aceleradasBruscas, desglose.frenadasBruscas),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
            Text(
                stringResource(R.string.trips_eco_rpm_bonus, desglose.tiempoAltasRpmSeg, desglose.bonusCrucero),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        } else {
            Text(stringResource(R.string.trips_no_breakdown), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
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
        containerColor = RevScopeColors.Surface,
        title = { Text(stringResource(R.string.trips_assign_vehicle_title), color = RevScopeColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            if (profiles.isEmpty()) {
                Text(stringResource(R.string.trips_no_saved_vehicles), color = RevScopeColors.TextSecondary, fontSize = 13.sp)
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
                                tint = RevScopeColors.Accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(profile.name, color = RevScopeColors.TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.trips_close), color = RevScopeColors.TextSecondary, fontSize = 13.sp)
            }
        },
    )
}
