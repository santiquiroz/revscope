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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.GraficaSerie
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.grafica.FormatoPosicion
import com.revscope.core.obd.taller.grafica.UnidadPosicion

data class AccionesGrafica(
    val onUnidad: (UnidadPosicion) -> Unit = {},
    val onVentana: (Long) -> Unit = {},
    val onPausa: (Boolean) -> Unit = {},
    val onCambiarVref: () -> Unit = {},
)

internal const val TEXTO_PAUSAR = "Pausar gráfica"
internal const val TEXTO_REANUDAR = "Reanudar gráfica"
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
        Text("Gráfica en vivo", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        if (ui.hayPosicion) {
            FilaChips("Posición") {
                ChipSeleccion("En %", ui.unidad == UnidadPosicion.PORCENTAJE, { acciones.onUnidad(UnidadPosicion.PORCENTAJE) })
                ChipSeleccion("En voltios", ui.unidad == UnidadPosicion.VOLTIOS, { acciones.onUnidad(UnidadPosicion.VOLTIOS) })
            }
        }
        FilaChips("Ventana") {
            VENTANAS_MS.forEach { ms -> ChipSeleccion("Últimos ${ms / 1_000} s", ui.ventanaS * 1_000L == ms, { acciones.onVentana(ms) }) }
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
        Text(if (pausada) TEXTO_REANUDAR else TEXTO_PAUSAR, color = RevScopeColors.Accent, style = RevScopeType.label)
    }
}

@Composable
private fun FilaReferencia(ui: GraficaCapturaUi, onCambiar: () -> Unit) {
    val vref = ui.referencia.usada
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Referencia para pasar % a V", color = RevScopeColors.TextPrimary, style = RevScopeType.bodySmall)
            Text("${FormatoPosicion.voltios(vref.voltios)} · ${vref.origen}", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
        TextButton(onClick = onCambiar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Cambiar", color = RevScopeColors.Accent, style = RevScopeType.label)
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
        Text("Gráfica en pausa: la captura sigue grabando", color = RevScopeColors.Warning, style = RevScopeType.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValorActual(v: ValorPidUi) {
    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${v.pid} ${v.nombre}: ${v.valor}" + (v.secundario?.let { " ($it)" } ?: "") + (v.hz?.let { ", $it" } ?: "")
        },
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
        Text("Esperando muestras…", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        return
    }
    ui.paneles.forEach { panel ->
        Text(panel.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.bodySmall)
        GraficaSerie(panel.modelo, alto = if (panel.modelo.bandas.isEmpty()) 150.dp else 200.dp)
        panel.leyenda.forEach { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
    }
}
