package com.revscope.feature.workshop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.revscope.core.obd.workshop.DiagnosticRules

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)

private val SENSOR_LABELS = mapOf(
    "14" to "B1S1",
    "15" to "B1S2",
    "18" to "B2S1",
    "19" to "B2S2",
)

private const val O2_MAX_VOLTS = 1.0
private const val O2_MIN_VOLTS = 0.0
private const val O2_TICK_STEP = 0.2
private const val HEALTHY_CROSSINGS_PER_MIN = 8

private val VoltAxisFormatter = object : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?,
    ): CharSequence = "%.1fV".format(value)
}

private val SecondsAxisFormatter = object : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?,
    ): CharSequence = "%.0fs".format(value)
}

@Composable
fun O2WaveScreen(
    onNavigateBack: () -> Unit,
    viewModel: O2WaveViewModel = hiltViewModel(),
) {
    DisposableEffect(Unit) {
        viewModel.setWorkshopMode(true)
        onDispose { viewModel.setWorkshopMode(false) }
    }

    val selectedPid by viewModel.selectedPid.collectAsState()
    val availableSensors by viewModel.availableSensors.collectAsState()
    val samples by viewModel.samples.collectAsState()
    val modelProducer = remember(selectedPid) { CartesianChartModelProducer() }

    LaunchedEffect(selectedPid) {
        viewModel.samples.collect { points ->
            if (points.size >= 2) {
                val startMs = points.first().timestamp
                modelProducer.runTransaction {
                    lineSeries {
                        series(
                            x = points.map { (it.timestamp - startMs) / 1000.0 },
                            y = points.map { it.value },
                        )
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
            }
            Text(
                "Onda del sensor O2",
                color = TextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Últimos 60 s de voltaje del sensor — motor encendido y en marcha mínima estable.",
            color = TextMutedColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (availableSensors.size > 1) {
            SensorSelector(availableSensors, selectedPid, viewModel::selectSensor)
            Spacer(Modifier.height(12.dp))
        }

        if (samples.size < 2) {
            Text(
                "Esperando datos del sensor O2 ${SENSOR_LABELS[selectedPid] ?: selectedPid}… (requiere conexión y motor encendido)",
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        } else {
            O2Chart(modelProducer)
            Text(
                "Umbral de conmutación: 0.45V · pobre <0.2V · rica >0.8V",
                color = TextMutedColor,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            val crossings = viewModel.crossingsPerMinute()
            Text(
                "Cruces por 0.45V: %.0f/min (sensor sano: >$HEALTHY_CROSSINGS_PER_MIN/min)".format(crossings),
                color = TextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )

            val diagnosis = remember(samples) { DiagnosticRules.evaluarO2(samples.map { it.value }) }
            DiagnosisChip(diagnosis)
        }
    }
}

@Composable
private fun O2Chart(modelProducer: CartesianChartModelProducer) {
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = O2_MIN_VOLTS, maxY = O2_MAX_VOLTS),
            ),
            startAxis = VerticalAxis.rememberStart(
                title = "V",
                valueFormatter = VoltAxisFormatter,
                itemPlacer = VerticalAxis.ItemPlacer.step({ O2_TICK_STEP }),
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = SecondsAxisFormatter,
                title = "segundos",
            ),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
    )
}

@Composable
private fun SensorSelector(sensors: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sensors.forEach { pid -> SensorChip(pid, pid == selected) { onSelect(pid) } }
    }
}

@Composable
private fun SensorChip(pid: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentColor else SurfaceHighColor,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            "O2 ${SENSOR_LABELS[pid] ?: pid}",
            color = if (selected) SurfaceColor else TextMutedColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
