package com.revscope.feature.sensors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.GraficaSerie
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.obd.taller.grafica.UnidadPosicion

data class AccionesGrafica(
    val onUnidad: (UnidadPosicion) -> Unit = {},
    val onVentana: (Long) -> Unit = {},
    val onPausa: (Boolean) -> Unit = {},
    val onCambiarVref: () -> Unit = {},
)

internal val VENTANAS_MS = listOf(10_000L, 30_000L)

/** Gráfica en vivo de la captura rápida: %/V en los PIDs de posición, bandas, ventana, pausa y valores actuales. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraficaCapturaContent(ui: GraficaCapturaUi, acciones: AccionesGrafica, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(stringResource(R.string.sensors_graph_live), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        if (ui.hayPosicion) {
            FilaChips(stringResource(R.string.sensors_graph_position)) {
                ChipSeleccion(
                    stringResource(R.string.sensors_graph_percent),
                    ui.unidad == UnidadPosicion.PORCENTAJE,
                    { acciones.onUnidad(UnidadPosicion.PORCENTAJE) },
                )
                ChipSeleccion(
                    stringResource(R.string.sensors_graph_volts),
                    ui.unidad == UnidadPosicion.VOLTIOS,
                    { acciones.onUnidad(UnidadPosicion.VOLTIOS) },
                )
            }
        }
        FilaChips(stringResource(R.string.sensors_graph_window)) {
            VENTANAS_MS.forEach { ms ->
                ChipSeleccion(
                    stringResource(R.string.sensors_graph_last_seconds, (ms / 1_000).toInt()),
                    ui.ventanaS * 1_000L == ms,
                    { acciones.onVentana(ms) },
                )
            }
        }
        BotonPausa(ui.pausada, acciones.onPausa)
        if (ui.hayPosicion) FilaReferencia(ui, acciones.onCambiarVref)
        if (ui.pausada) AvisoPausa()
        ui.valores.forEach { ValorActual(it) }
        Grafica(ui)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaChips(titulo: String, chips: @Composable () -> Unit) {
    Column {
        Text(titulo, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun BotonPausa(pausada: Boolean, onPausa: (Boolean) -> Unit) {
    val lado = with(LocalDensity.current) { 18.sp.toDp() }
    OutlinedButton(onClick = { onPausa(!pausada) }, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(if (pausada) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(lado))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (pausada) R.string.sensors_graph_resume else R.string.sensors_graph_pause),
            color = RevScopeColors.Accent,
            style = RevScopeType.label,
        )
    }
}

@Composable
private fun FilaReferencia(ui: GraficaCapturaUi, onCambiar: () -> Unit) {
    val vref = ui.referencia.usada
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sensors_graph_reference), color = RevScopeColors.TextPrimary, style = RevScopeType.bodySmall)
            Text(
                "${LocaleNumbers.decimal(vref.voltios, 2, locale)} V · ${vref.origen}",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        }
        TextButton(onClick = onCambiar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sensors_change), color = RevScopeColors.Accent, style = RevScopeType.label)
        }
    }
}

@Composable
private fun AvisoPausa() {
    val lado = with(LocalDensity.current) { 16.sp.toDp() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(Icons.Filled.Pause, contentDescription = null, tint = RevScopeColors.Warning, modifier = Modifier.size(lado))
        Text(stringResource(R.string.sensors_graph_paused_recording), color = RevScopeColors.Warning, style = RevScopeType.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValorActual(v: ValorPidUi) {
    val descripcionSecundaria = v.secundario?.let { stringResource(R.string.sensors_value_secondary, it) }.orEmpty()
    val descripcionHz = v.hz?.let { stringResource(R.string.sensors_value_rate, it) }.orEmpty()
    val descripcion = stringResource(
        R.string.sensors_value_content_description,
        v.pid,
        v.nombre,
        v.valor,
        descripcionSecundaria,
        descripcionHz,
    )
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = descripcion },
    ) {
        Text("${v.pid} ${v.nombre}", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.Bottom) {
            Text(v.valor, color = RevScopeColors.TextPrimary, style = RevScopeType.numeros.copy(fontSize = 28.sp))
            v.secundario?.let { Text(it, color = RevScopeColors.Accent, style = RevScopeType.numeros.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) }
            v.hz?.let { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall.copy(fontFeatureSettings = "tnum")) }
        }
    }
}

@Composable
private fun Grafica(ui: GraficaCapturaUi) {
    if (!ui.tieneDatos) {
        Text(stringResource(R.string.sensors_graph_waiting_samples), color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        return
    }
    ui.paneles.forEach { panel ->
        Text(panel.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.bodySmall)
        GraficaSerie(panel.modelo, alto = if (panel.modelo.bandas.isEmpty()) 150.dp else 200.dp)
        panel.leyenda.forEach { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
    }
}
