package com.revscope.feature.workshop.taller.prueba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.GraficaSerie
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.feature.workshop.taller.TarjetaTaller
import com.revscope.feature.workshop.taller.nivel
import com.revscope.feature.workshop.taller.texto

@Composable
internal fun ColumnScope.ResultadoPrueba(r: ResultadoUi, estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    BarraConVolver(titulo = r.tipo.titulo, subtitulo = estado.vehiculo, onVolver = acciones.onTerminar)
    CuerpoDesplazable {
        TarjetaVeredicto(r)
        r.siguientePaso?.let { TarjetaSiguiente(it) }
        if (r.pesas.isNotEmpty()) PesasPorPaso(r)
        if (r.medidas.isNotEmpty()) Medidas(r.medidas)
        if (r.comprobaciones.isNotEmpty()) Comprobaciones(r)
        r.graficas.filter { it.modelo.tieneDatos }.forEach { SerieCompleta(it) }
        AccionesResultado(r, estado, acciones)
    }
    PieConAccion("Listo", acciones.onTerminar)
}

@Composable
private fun TarjetaVeredicto(r: ResultadoUi) {
    TarjetaTaller {
        NivelBadge(r.veredicto.nivel(), texto = r.veredicto.texto())
        Text(r.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title.copy(fontSize = 20.sp, lineHeight = 26.sp))
        Text(r.interpretacion, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        r.hallazgos.forEach { FilaIcono(Icons.Filled.Error, RevScopeColors.Warning, it) }
    }
}

@Composable
private fun TarjetaSiguiente(texto: String) {
    TarjetaTaller {
        Text("Siguiente paso sugerido", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        Text(texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
    }
}

@Composable
private fun PesasPorPaso(r: ResultadoUi) {
    Seccion("Medido contra la banda")
    TarjetaTaller {
        r.pesas.forEach { PesaFila(it) }
        Text("${r.referencia}. Cada barra va de 0 a la referencia.", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PesaFila(p: PesaUi) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        // Con letra grande el estado baja debajo del título en vez de partirlo letra a letra.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            NivelBadge(p.nivel.nivel(), texto = textoNivelPesa(p.nivel))
        }
        Text(p.valores, color = RevScopeColors.TextPrimary, style = RevScopeType.body.copy(fontFeatureSettings = "tnum"))
        PesaReferencia(p)
        Text(
            p.banda?.let { "Banda ${it.texto}" } ?: "Sin banda fija: debe quedar entre cerrado y fondo",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
    }
}

private fun textoNivelPesa(nivel: Veredicto): String = when (nivel) {
    Veredicto.OK -> "Dentro"
    Veredicto.FALLA -> "Fuera de banda"
    Veredicto.ATENCION -> "Revisar"
    Veredicto.INFO -> "Sin banda"
}

@Composable
private fun Comprobaciones(r: ResultadoUi) {
    Seccion("Comprobaciones")
    TarjetaTaller {
        r.comprobaciones.forEach { c ->
            if (c.cumple) {
                FilaIcono(Icons.Filled.CheckCircle, RevScopeColors.Success, c.texto, "Cumple")
            } else {
                FilaIcono(Icons.Filled.Cancel, RevScopeColors.Danger, c.texto, "No cumple")
            }
        }
    }
}

@Composable
private fun Medidas(medidas: List<String>) {
    Seccion("Medidas")
    TarjetaTaller {
        medidas.forEach { Text(it, color = RevScopeColors.TextPrimary, style = RevScopeType.body.copy(fontFeatureSettings = "tnum")) }
    }
}

@Composable
private fun SerieCompleta(g: GraficaResultadoUi) {
    Seccion(g.titulo)
    TarjetaTaller {
        GraficaSerie(g.modelo, alto = 200.dp)
        g.leyenda.forEach { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
    }
}

@Composable
private fun AccionesResultado(r: ResultadoUi, estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r.guardado) {
            FilaIcono(Icons.Filled.CheckCircle, RevScopeColors.Success, "Ya guardado en la sesión")
        } else {
            BotonSecundario("Guardar en una sesión nueva", habilitado = !estado.ocupado, onClick = acciones.onGuardarEnSesion)
        }
        val verGuia = acciones.onVerGuia
        if (r.codigoGuia != null && verGuia != null) BotonSecundario("Ver guía ${r.codigoGuia}") { verGuia(r.codigoGuia) }
        BotonSecundario("Repetir la prueba", onClick = acciones.onReintentar)
    }
}

@Composable
private fun BotonSecundario(texto: String, habilitado: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = habilitado, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(
            texto,
            color = if (habilitado) RevScopeColors.Accent else RevScopeColors.TextSecondary,
            style = RevScopeType.label,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FilaIcono(icono: ImageVector, color: Color, texto: String, descripcionIcono: String? = null) {
    val lado = with(LocalDensity.current) { 18.sp.toDp() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icono, contentDescription = descripcionIcono, tint = color, modifier = Modifier.padding(top = 1.dp).size(lado))
        Text(texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body, modifier = Modifier.weight(1f))
    }
}
