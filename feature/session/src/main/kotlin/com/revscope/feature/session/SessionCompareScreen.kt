package com.revscope.feature.session

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val RunAColor = Color(0xFFE8FF00)
private val RunBColor = Color(0xFF3D8BFF)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

private val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale("es"))

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
                title = { Text("Comparar viajes", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        containerColor = BgColor,
    ) { innerPadding ->
        when (val s = state) {
            SessionCompareViewModel.UiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center,
            ) { Text("Cargando…", color = TextMutedColor) }

            is SessionCompareViewModel.UiState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center,
            ) { Text(s.message, color = TextMutedColor) }

            is SessionCompareViewModel.UiState.Ready -> CompareContent(
                s.runA, s.runB, Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun CompareContent(
    runA: SessionCompareViewModel.RunData,
    runB: SessionCompareViewModel.RunData,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "A · ${dateFormat.format(Date(runA.session.startedAt))}",
                color = RunAColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "B · ${dateFormat.format(Date(runB.session.startedAt))}",
                color = RunBColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
        }

        CompareRow("Duración", runA.durationLabel(), runB.durationLabel())
        CompareRow(
            "Distancia",
            "%.1f km".format(runA.session.distanceKm), "%.1f km".format(runB.session.distanceKm),
            highlightMax = runA.session.distanceKm.compareTo(runB.session.distanceKm),
        )
        CompareRow(
            "Vel. máx", "${runA.session.maxSpeed}", "${runB.session.maxSpeed}",
            highlightMax = runA.session.maxSpeed.compareTo(runB.session.maxSpeed),
        )
        CompareRow(
            "Vel. prom", "${runA.avgSpeedKmh}", "${runB.avgSpeedKmh}",
            highlightMax = runA.avgSpeedKmh.compareTo(runB.avgSpeedKmh),
        )
        CompareRow(
            "RPM máx", "${runA.session.maxRpm}", "${runB.session.maxRpm}",
            highlightMax = runA.session.maxRpm.compareTo(runB.session.maxRpm),
        )
        CompareRow(
            "0-60",
            runA.session.best0to60Ms?.let { "%.2fs".format(it / 1000.0) } ?: "—",
            runB.session.best0to60Ms?.let { "%.2fs".format(it / 1000.0) } ?: "—",
            // lower is better
            highlightMax = -(runA.session.best0to60Ms ?: Long.MAX_VALUE)
                .compareTo(runB.session.best0to60Ms ?: Long.MAX_VALUE),
        )
        CompareRow(
            "G lat máx",
            runA.maxAbsG?.let { "%.2f".format(it) } ?: "—",
            runB.maxAbsG?.let { "%.2f".format(it) } ?: "—",
            highlightMax = (runA.maxAbsG ?: 0f).compareTo(runB.maxAbsG ?: 0f),
        )
        CompareRow(
            "Lean máx",
            runA.maxLean?.let { "%.0f°".format(it) } ?: "—",
            runB.maxLean?.let { "%.0f°".format(it) } ?: "—",
            highlightMax = (runA.maxLean ?: 0f).compareTo(runB.maxLean ?: 0f),
        )

        if (runA.speedSeries.size >= 2 && runB.speedSeries.size >= 2) {
            Text("Velocidad — A amarillo, B azul", color = TextMutedColor, fontSize = 11.sp)
            val producer = remember { CartesianChartModelProducer() }
            LaunchedEffect(runA, runB) {
                producer.runTransaction {
                    lineSeries {
                        series(y = runA.speedSeries.map { it })
                        series(y = runB.speedSeries.map { it })
                    }
                }
            }
            CartesianChartHost(
                chart = rememberCartesianChart(rememberLineCartesianLayer()),
                modelProducer = producer,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
        }

        if (runA.track.size >= 2 && runB.track.size >= 2) {
            Text("Trazados superpuestos", color = TextMutedColor, fontSize = 11.sp)
            OverlayTrackMap(
                trackA = runA.track,
                trackB = runB.track,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun SessionCompareViewModel.RunData.durationLabel(): String {
    val ms = (session.endedAt ?: session.startedAt) - session.startedAt
    return "%dm %02ds".format(
        TimeUnit.MILLISECONDS.toMinutes(ms),
        TimeUnit.MILLISECONDS.toSeconds(ms) % 60,
    )
}

@Composable
private fun CompareRow(label: String, a: String, b: String, highlightMax: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = TextMutedColor, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
        Text(
            a,
            color = if (highlightMax > 0) RunAColor else TextPrimaryColor,
            fontSize = 13.sp,
            fontWeight = if (highlightMax > 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            b,
            color = if (highlightMax < 0) RunBColor else TextPrimaryColor,
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
            .background(SurfaceColor, RoundedCornerShape(12.dp))
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
