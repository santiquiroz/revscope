package com.revscope.feature.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.revscope.core.common.format.formatElapsedMmSs
import com.revscope.core.obd.viewmodel.ConnectionViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

/** Bottom-axis tick label — the x value is already elapsed seconds since the first reading. */
private val ElapsedTimeFormatter = object : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?,
    ): CharSequence = formatElapsedMmSs(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorGraphScreen(
    connectionVm: ConnectionViewModel = hiltViewModel(),
    vm: SensorViewModel = hiltViewModel(),
) {
    val selectedPid by vm.selectedPid.collectAsState()
    val history by vm.history.collectAsState()
    // Fresh producer per PID: cancelling a runTransaction mid-flight (old code keyed
    // the effect on `history`) leaves Vico with an empty partial → crash on next update.
    val modelProducer = remember(selectedPid) { CartesianChartModelProducer() }

    LaunchedEffect(Unit) {
        vm.observeReadings(connectionVm)
    }

    LaunchedEffect(selectedPid) {
        vm.history.collect { points ->
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        TopAppBar(
            title = {
                Text("Sensores", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.availablePids.forEach { def ->
                val selected = def.pid == selectedPid
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) AccentColor else SurfaceHighColor,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { vm.selectPid(def.pid) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = def.nameEs,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) BgColor else TextMutedColor,
                    )
                }
            }
        }

        val currentDef = vm.availablePids.find { it.pid == selectedPid }
        val latestReading = history.lastOrNull()
        if (currentDef != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = latestReading?.value?.let { "%.1f".format(it) } ?: "--",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentColor,
                )
                Text(
                    text = currentDef.unit,
                    fontSize = 16.sp,
                    color = TextMutedColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (history.size >= 2) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(title = currentDef?.unit),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = ElapsedTimeFormatter,
                        title = "tiempo (mm:ss)",
                    ),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 8.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Esperando datos del sensor…",
                    color = TextMutedColor,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
