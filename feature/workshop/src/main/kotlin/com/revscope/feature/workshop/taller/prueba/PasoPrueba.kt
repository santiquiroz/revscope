package com.revscope.feature.workshop.taller.prueba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.GraficaSerie
import com.revscope.core.designsystem.IndicadorPasos
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable
import com.revscope.feature.workshop.taller.TarjetaTaller

internal const val TEXTO_REPETIR_PASO = "Repetir paso"

@Composable
internal fun ColumnScope.PasoPrueba(paso: PasoUi, estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    BarraPrueba(estado.titulo, estado.vehiculo, acciones.onPedirCancelar)
    CuerpoDesplazable {
        IndicadorPasos(actual = paso.indice, total = paso.total, titulo = paso.titulo)
        Instruccion(paso)
        CuentaYValor(paso)
        MiniGrafica(paso)
        AccionesSecundarias(estado.voz, acciones)
    }
    paso.accionPrincipal?.let { PieConAccion(it, acciones.onAvanzar, habilitado = !estado.ocupado) }
}

@Composable
private fun Instruccion(paso: PasoUi) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            paso.instruccion,
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.title.copy(fontSize = 20.sp, lineHeight = 26.sp),
        )
        Text(paso.subtexto, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
    }
}

// Anillo y valor lado a lado; con letra grande el valor baja debajo del anillo en vez de apretarse.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CuentaYValor(paso: PasoUi) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CuentaRegresivaAnillo(
            segundos = paso.restanteS ?: paso.duracionS,
            fraccion = paso.fraccionRestante,
            anuncio = paso.anuncio,
        )
        paso.vivo?.let { ValorVivo(it, Modifier.width(200.dp)) }
    }
}

@Composable
private fun ValorVivo(vivo: ValorVivoUi, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = listOfNotNull(vivo.principal, vivo.secundario).joinToString(", ", prefix = "${vivo.etiqueta}: ")
        },
    ) {
        TextoAjustable(vivo.principal, RevScopeType.numeros, RevScopeColors.TextPrimary, maximo = 40.sp, alineacion = TextAlign.Start)
        vivo.secundario?.let {
            TextoAjustable(it, RevScopeType.numeros.copy(fontWeight = FontWeight.SemiBold), RevScopeColors.Accent, maximo = 28.sp, alineacion = TextAlign.Start)
        }
        Text(vivo.etiqueta, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@Composable
private fun MiniGrafica(paso: PasoUi) {
    TarjetaTaller {
        Text(paso.tituloGrafica, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        if (paso.grafica.tieneDatos) {
            GraficaSerie(paso.grafica, alto = 160.dp)
        } else {
            Text("Esperando muestras del adaptador…", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        }
        paso.leyendaGrafica.forEach { Text("Banda $it", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
    }
}

@Composable
private fun AccionesSecundarias(voz: Boolean, acciones: AccionesPrueba) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = acciones.onRepetirPaso, modifier = Modifier.heightIn(min = 48.dp)) {
            val lado = with(LocalDensity.current) { 18.sp.toDp() }
            Icon(Icons.Filled.Replay, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(lado))
            Spacer(Modifier.width(8.dp))
            Text(TEXTO_REPETIR_PASO, color = RevScopeColors.Accent, style = RevScopeType.label)
        }
        FilaVoz(voz, acciones.onVoz)
    }
}
