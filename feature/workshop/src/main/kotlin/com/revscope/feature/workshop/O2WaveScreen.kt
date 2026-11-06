package com.revscope.feature.workshop

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.common.i18n.LocaleNumbers
import java.util.Locale

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

// PID 14/15/18/19 formula is A/200 with A up to 255 -> max physical value is 1.275 V.
private const val O2_MAX_VOLTS = 1.3
private const val O2_MIN_VOLTS = 0.0
private const val O2_TICK_STEP = 0.2
private const val HEALTHY_CROSSINGS_PER_MIN = 8

private fun voltAxisFormatter(locale: Locale) = object : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?,
    ): CharSequence = "${LocaleNumbers.decimal(value, 1, locale)}V"
}

private fun secondsAxisFormatter(locale: Locale) = object : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?,
    ): CharSequence = "${LocaleNumbers.decimal(value, 0, locale)}s"
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
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
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

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.revscope.core.designsystem.R.string.ds_back), tint = TextColor)
            }
            Text(
                stringResource(R.string.workshop_o2_wave_title),
                color = TextColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { viewModel.exportWindow(context) },
                enabled = samples.size >= 2,
            ) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.workshop_o2_wave_export_csv), tint = AccentColor)
            }
        }
        Text(
            stringResource(R.string.workshop_o2_wave_description),
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
                stringResource(R.string.workshop_o2_wave_waiting, SENSOR_LABELS[selectedPid] ?: selectedPid),
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        } else {
            O2Chart(modelProducer, locale)
            Text(
                stringResource(R.string.workshop_o2_wave_threshold),
                color = TextMutedColor,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            val crossings = viewModel.crossingsPerMinute()
            Text(
                stringResource(
                    R.string.workshop_o2_wave_crossings,
                    LocaleNumbers.decimal(crossings, 0, locale),
                    HEALTHY_CROSSINGS_PER_MIN,
                ),
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
private fun O2Chart(modelProducer: CartesianChartModelProducer, locale: Locale) {
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = O2_MIN_VOLTS, maxY = O2_MAX_VOLTS),
            ),
            startAxis = VerticalAxis.rememberStart(
                title = "V",
                valueFormatter = voltAxisFormatter(locale),
                itemPlacer = VerticalAxis.ItemPlacer.step({ O2_TICK_STEP }),
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = secondsAxisFormatter(locale),
                title = stringResource(R.string.workshop_o2_wave_seconds),
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
    ChipSeleccion(
        texto = stringResource(R.string.workshop_o2_wave_sensor_label, SENSOR_LABELS[pid] ?: pid),
        seleccionado = selected,
        onClick = onClick,
    )
}
