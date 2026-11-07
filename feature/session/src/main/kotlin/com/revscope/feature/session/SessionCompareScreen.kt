package com.revscope.feature.session

import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.rememberUnitFormatter
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
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
import com.revscope.core.common.i18n.LocaleNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.cos

private val RunAColor = Color(0xFFE8FF00)
private val RunBColor = Color(0xFF3D8BFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCompareScreen(
    onNavigateBack: () -> Unit = {},
    vm: SessionCompareViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_compare_trips), color = RevScopeColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.trips_back), tint = RevScopeColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RevScopeColors.Surface),
            )
        },
        containerColor = RevScopeColors.Background,
    ) { innerPadding ->
        when (val s = state) {
            SessionCompareViewModel.UiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.trips_loading), color = RevScopeColors.TextSecondary) }

            is SessionCompareViewModel.UiState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center,
            ) { Text(s.message, color = RevScopeColors.TextSecondary) }

            is SessionCompareViewModel.UiState.Ready -> CompareContent(
                s.runA, s.runB, s.showLean, Modifier.padding(innerPadding),
            )
        }
    }
}

// A y B no se distinguen solo por el color: A es continua y B discontinua.
@Composable
private fun lineasComparacion(): LineCartesianLayer.LineProvider = LineCartesianLayer.LineProvider.series(
    LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(RunAColor)),
        stroke = LineCartesianLayer.LineStroke.Continuous(),
    ),
    LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(RunBColor)),
        stroke = LineCartesianLayer.LineStroke.Dashed(),
    ),
)

@Composable
internal fun CompareContent(
    runA: SessionCompareViewModel.RunData,
    runB: SessionCompareViewModel.RunData,
    showLean: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd MMM HH:mm", locale) } // i18n-ignore
    val units = rememberUnitFormatter()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.trips_compare_run_a, dateFormat.format(Date(runA.session.startedAt))),
                color = RunAColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.trips_compare_run_b, dateFormat.format(Date(runB.session.startedAt))),
                color = RunBColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }

        CompareRow(stringResource(R.string.trips_duration), runA.durationLabel(), runB.durationLabel())
        CompareRow(
            stringResource(R.string.trips_distance),
            units.formatDistance(runA.session.distanceKm.toDouble()),
            units.formatDistance(runB.session.distanceKm.toDouble()),
            highlightMax = runA.session.distanceKm.compareTo(runB.session.distanceKm),
        )
        CompareRow(
            stringResource(R.string.trips_max_speed),
            units.formatSpeed(runA.session.maxSpeed.toDouble()),
            units.formatSpeed(runB.session.maxSpeed.toDouble()),
            highlightMax = runA.session.maxSpeed.compareTo(runB.session.maxSpeed),
        )
        CompareRow(
            stringResource(R.string.trips_avg_speed),
            units.formatSpeed(runA.avgSpeedKmh.toDouble()),
            units.formatSpeed(runB.avgSpeedKmh.toDouble()),
            highlightMax = runA.avgSpeedKmh.compareTo(runB.avgSpeedKmh),
        )
        CompareRow(
            stringResource(R.string.trips_max_rpm), "${runA.session.maxRpm}", "${runB.session.maxRpm}",
            highlightMax = runA.session.maxRpm.compareTo(runB.session.maxRpm),
        )
        CompareRow(
            sprintLabel(R.string.trips_0_60, SPRINT_SHORT_KMH, units),
            runA.session.best0to60Ms?.let { "%.2fs".format(it / 1000.0) } ?: "—",
            runB.session.best0to60Ms?.let { "%.2fs".format(it / 1000.0) } ?: "—",
            // lower is better
            highlightMax = -(runA.session.best0to60Ms ?: Long.MAX_VALUE)
                .compareTo(runB.session.best0to60Ms ?: Long.MAX_VALUE),
        )
        CompareRow(
            stringResource(R.string.trips_max_g_lat),
            runA.maxAbsG?.let { LocaleNumbers.decimal(it.toDouble(), 2, locale) } ?: "—",
            runB.maxAbsG?.let { LocaleNumbers.decimal(it.toDouble(), 2, locale) } ?: "—",
            highlightMax = (runA.maxAbsG ?: 0f).compareTo(runB.maxAbsG ?: 0f),
        )
        if (showLean) {
            CompareRow(
                stringResource(R.string.trips_lean_max),
                runA.maxLean?.let { stringResource(R.string.trips_lean_value, LocaleNumbers.decimal(it.toDouble(), 0, locale)) } ?: "—",
                runB.maxLean?.let { stringResource(R.string.trips_lean_value, LocaleNumbers.decimal(it.toDouble(), 0, locale)) } ?: "—",
                highlightMax = (runA.maxLean ?: 0f).compareTo(runB.maxLean ?: 0f),
            )
        }

        if (runA.speedSeries.size >= 2 && runB.speedSeries.size >= 2) {
            Text(
                stringResource(R.string.trips_compare_speed_legend),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
            val producer = remember { CartesianChartModelProducer() }
            LaunchedEffect(runA, runB) {
                producer.runTransaction {
                    lineSeries {
                        series(y = speedSeriesIn(runA.speedSeries, units))
                        series(y = speedSeriesIn(runB.speedSeries, units))
                    }
                }
            }
            val referenceDurationMs = maxOf(runA.durationMs(), runB.durationMs())
            val sampleCount = maxOf(runA.speedSeries.size, runB.speedSeries.size)
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(lineProvider = lineasComparacion()),
                    startAxis = VerticalAxis.rememberStart(title = units.speedLabel()),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = elapsedFractionFormatter(sampleCount, referenceDurationMs),
                        title = stringResource(R.string.trips_time_axis),
                    ),
                ),
                modelProducer = producer,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }

        if (runA.track.size >= 2 && runB.track.size >= 2) {
            Text(stringResource(R.string.trips_superimposed_tracks), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            OverlayTrackMap(
                trackA = runA.track,
                trackB = runB.track,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun SessionCompareViewModel.RunData.durationMs(): Long =
    (session.endedAt ?: session.startedAt) - session.startedAt

@Composable
private fun SessionCompareViewModel.RunData.durationLabel(): String {
    val ms = durationMs()
    return stringResource(
        R.string.trips_duration_format,
        TimeUnit.MILLISECONDS.toMinutes(ms),
        TimeUnit.MILLISECONDS.toSeconds(ms) % 60,
    )
}

@Composable
private fun CompareRow(label: String, a: String, b: String, highlightMax: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall, modifier = Modifier.weight(1.2f))
        Text(
            a,
            color = if (highlightMax > 0) RunAColor else RevScopeColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (highlightMax > 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            b,
            color = if (highlightMax < 0) RunBColor else RevScopeColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (highlightMax < 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Both routes projected into a common bounding box — A yellow, B blue. */
@Composable
private fun OverlayTrackMap(
    trackA: List<Pair<Double, Double>>,
    trackB: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        val all = trackA + trackB
        val midLatRad = Math.toRadians(all.map { it.first }.average())
        val lonScale = cos(midLatRad)
        val xs = all.map { it.second * lonScale }
        val ys = all.map { it.first }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).takeIf { it > 0 } ?: 1e-9
        val spanY = (maxY - minY).takeIf { it > 0 } ?: 1e-9
        val scale = minOf(size.width / spanX, size.height / spanY).toFloat()
        val offsetX = (size.width - (spanX * scale).toFloat()) / 2f
        val offsetY = (size.height - (spanY * scale).toFloat()) / 2f

        fun project(p: Pair<Double, Double>) = Offset(
            offsetX + ((p.second * lonScale - minX) * scale).toFloat(),
            offsetY + ((maxY - p.first) * scale).toFloat(),
        )

        fun drawTrack(track: List<Pair<Double, Double>>, color: Color) {
            val path = Path().apply {
                val first = project(track[0])
                moveTo(first.x, first.y)
                for (i in 1 until track.size) {
                    val p = project(track[i])
                    lineTo(p.x, p.y)
                }
            }
            drawPath(
                path, color,
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                alpha = 0.9f,
            )
        }
        drawTrack(trackA, RunAColor)
        drawTrack(trackB, RunBColor)
    }
}
