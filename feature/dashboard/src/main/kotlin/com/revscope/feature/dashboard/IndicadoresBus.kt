package com.revscope.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.perfilcan.ClavesSenal

internal data class IndicadoresBus(
    val pataAbajo: Boolean? = null,
    val embrague: Boolean? = null,
    val killOff: Boolean? = null,
    val alerta: Boolean = false,
) {
    val visible: Boolean get() = pataAbajo != null || embrague != null || killOff != null
}

// Una bandera sin trama reciente no se muestra: nunca un estado viejo como si fuera actual.
private const val VIGENCIA_MS = 3_000L
private const val RPM_MOTOR_EN_MARCHA = 300.0

internal fun indicadoresBus(
    lecturas: Map<String, ObdReading>,
    ahora: Long = System.currentTimeMillis(),
): IndicadoresBus {
    fun fresca(clave: String): Double? = lecturas[clave]?.takeIf { ahora - it.timestamp <= VIGENCIA_MS }?.value
    fun bandera(clave: String): Boolean? = fresca(clave)?.let { it != 0.0 }

    val pataAbajo = bandera(ClavesSenal.PATA_LATERAL)
    val motorEnMarcha = bandera(ClavesSenal.MOTOR_EN_MARCHA)
        ?: ((fresca(ClavesSenal.RPM) ?: 0.0) >= RPM_MOTOR_EN_MARCHA)
    return IndicadoresBus(
        pataAbajo = pataAbajo,
        embrague = bandera(ClavesSenal.EMBRAGUE),
        killOff = bandera(ClavesSenal.KILL_SWITCH)?.not(),
        alerta = pataAbajo == true && motorEnMarcha,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilaIndicadoresBus(indicadores: IndicadoresBus) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        indicadores.pataAbajo?.let { abajo ->
            when {
                indicadores.alerta -> Indicador(
                    stringResource(R.string.drive_bus_stand_down_alert),
                    RevScopeColors.Background,
                    RevScopeColors.Danger,
                )
                abajo -> Indicador(stringResource(R.string.drive_bus_stand_down), RevScopeColors.Warning)
                else -> Indicador(stringResource(R.string.drive_bus_stand_up))
            }
        }
        indicadores.embrague?.let { apretado ->
            if (apretado) {
                Indicador(stringResource(R.string.drive_bus_clutch_in), RevScopeColors.Accent)
            } else {
                Indicador(stringResource(R.string.drive_bus_clutch_released))
            }
        }
        indicadores.killOff?.let { off ->
            if (off) {
                Indicador(stringResource(R.string.drive_bus_kill_off), RevScopeColors.Warning)
            } else {
                Indicador(stringResource(R.string.drive_bus_kill_run))
            }
        }
    }
}

@Composable
private fun Indicador(
    texto: String,
    color: Color = RevScopeColors.TextSecondary,
    fondo: Color = RevScopeColors.SurfaceHigh,
) {
    Text(
        texto,
        color = color,
        style = RevScopeType.label,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(fondo, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
