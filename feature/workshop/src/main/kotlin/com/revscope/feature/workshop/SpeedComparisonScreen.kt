package com.revscope.feature.workshop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.workshop.SpeedDeltaAverager
import com.revscope.core.common.i18n.LocaleNumbers
import kotlin.math.roundToInt

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)
private val WarnColor = Color(0xFFFFC107)

@Composable
fun SpeedComparisonScreen(
    onNavigateBack: () -> Unit,
    viewModel: SpeedComparisonViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val obdSpeed by viewModel.obdSpeedKmh.collectAsState()
    val gpsSpeed by viewModel.gpsSpeedKmh.collectAsState()
    val averageDeltaPercent by viewModel.averageDeltaPercent.collectAsState()
    SpeedComparisonContent(
        conectado = connectionState is ConnectionState.Connected,
        velocidadObd = obdSpeed,
        velocidadGps = gpsSpeed,
        promedioPorcentaje = averageDeltaPercent,
        onReiniciar = viewModel::resetAverage,
        onVolver = onNavigateBack,
    )
}

@Composable
internal fun SpeedComparisonContent(
    conectado: Boolean,
    velocidadObd: Double?,
    velocidadGps: Double?,
    promedioPorcentaje: Double?,
    onReiniciar: () -> Unit,
    onVolver: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolver) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.revscope.core.designsystem.R.string.ds_back), tint = TextColor)
            }
            Text(
                stringResource(R.string.workshop_speed_comparison_title),
                color = TextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            !conectado -> EmptyState(stringResource(R.string.workshop_speed_comparison_connect_adapter))
            velocidadGps == null -> EmptyState(
                stringResource(R.string.workshop_speed_comparison_waiting_gps),
            )
            else -> ComparisonContent(
                obdSpeed = velocidadObd ?: 0.0,
                gpsSpeed = velocidadGps,
                averageDeltaPercent = promedioPorcentaje,
                onReset = onReiniciar,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(message, color = TextMutedColor, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun ComparisonContent(
    obdSpeed: Double,
    gpsSpeed: Double?,
    averageDeltaPercent: Double?,
    onReset: () -> Unit,
) {
    val gps = gpsSpeed ?: 0.0
    val locale = LocalConfiguration.current.locales[0]
    val deltaAbs = obdSpeed - gps
    val deltaPercent = deltaPercentOrNull(obdSpeed, gps)

    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SpeedColumn(stringResource(R.string.workshop_speed_comparison_obd), obdSpeed, Modifier.weight(1f))
        SpeedColumn(stringResource(R.string.workshop_speed_comparison_gps), gps, Modifier.weight(1f))
    }

    Spacer(Modifier.height(20.dp))
    DeltaCard(deltaAbs, deltaPercent, locale)

    Spacer(Modifier.height(16.dp))
    AverageCard(averageDeltaPercent, onReset, locale)

    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.workshop_speed_comparison_explanation),
        color = TextMutedColor,
        fontSize = 12.sp,
    )
}

private fun deltaPercentOrNull(obdKmh: Double, gpsKmh: Double): Double? =
    if (gpsKmh > SpeedDeltaAverager.MIN_GPS_SPEED_KMH) SpeedDeltaAverager.deltaPercent(obdKmh, gpsKmh) else null

@Composable
private fun SpeedColumn(label: String, speed: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextoAjustable(
            texto = speed.roundToInt().toString(),
            estilo = RevScopeType.numeros.copy(fontSize = 44.sp),
            color = TextColor,
            minimo = 20.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(label, color = TextMutedColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeltaCard(deltaAbs: Double, deltaPercent: Double?, locale: java.util.Locale) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.workshop_speed_comparison_difference), color = TextMutedColor, fontSize = 12.sp)
            Text(
                stringResource(
                    R.string.workshop_speed_comparison_delta,
                    LocaleNumbers.signed(deltaAbs, 1, locale),
                ),
                color = TextColor,
                style = RevScopeType.numeros.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                deltaPercent?.let {
                    stringResource(R.string.workshop_speed_comparison_percent, LocaleNumbers.signed(it, 1, locale))
                } ?: stringResource(R.string.workshop_speed_comparison_speed_up),
                color = if ((deltaPercent ?: 0.0) > 0) WarnColor else TextMutedColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AverageCard(averageDeltaPercent: Double?, onReset: () -> Unit, locale: java.util.Locale) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.workshop_speed_comparison_average), color = TextMutedColor, fontSize = 12.sp)
            Text(
                averageDeltaPercent?.let {
                    stringResource(R.string.workshop_speed_comparison_percent, LocaleNumbers.signed(it, 1, locale))
                } ?: stringResource(R.string.workshop_speed_comparison_no_data),
                color = AccentColor,
                style = RevScopeType.numeros.copy(fontSize = 26.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workshop_speed_comparison_reset_average))
            }
        }
    }
}
