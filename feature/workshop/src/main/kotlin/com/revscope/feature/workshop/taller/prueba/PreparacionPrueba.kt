package com.revscope.feature.workshop.taller.prueba

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.DialogoReferenciaVoltaje
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.grafica.FormatoPosicion
import com.revscope.core.obd.taller.grafica.ResolutorVref
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje

// ── Elegir prueba ───────────────────────────────────────────────────────────

@Composable
internal fun ColumnScope.ElegirPrueba(fase: FasePantalla.Elegir, acciones: AccionesPrueba) {
    BarraConVolver(titulo = "Pruebas guiadas", onVolver = acciones.onVolver)
    CuerpoDesplazable {
        Text(
            "Paso a paso, leyendo a la tasa máxima y con veredicto. Si hay una sesión abierta, el resultado queda en ella.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        fase.opciones.forEach { opcion -> TarjetaOpcion(opcion) { acciones.onElegir(opcion.tipo) } }
    }
}

@Composable
private fun TarjetaOpcion(opcion: OpcionPrueba, onClick: () -> Unit) {
    val base = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = RevScopeColors.Surface,
        modifier = if (opcion.disponible) base.clickable(role = Role.Button, onClick = onClick) else base,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Icon(
                if (opcion.disponible) Icons.Filled.Science else Icons.Filled.Schedule,
                contentDescription = null,
                tint = if (opcion.disponible) RevScopeColors.Accent else RevScopeColors.TextSecondary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(opcion.tipo.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                Text(opcion.descripcion, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
                if (!opcion.disponible) Text("Llega en una próxima versión", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            }
        }
    }
}

// ── Preparación: qué se hará, precondiciones detectadas y ajustes ───────────

@Composable
internal fun ColumnScope.PreparacionPrueba(fase: FasePantalla.Preparacion, estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    BarraConVolver(titulo = fase.tipo.titulo, subtitulo = estado.vehiculo, onVolver = acciones.onVolver)
    CuerpoDesplazable {
        Text(fase.descripcion, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        Seccion("Antes de empezar")
        if (fase.precondiciones.isEmpty()) {
            Text("Comprobando el adaptador y la moto…", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        }
        fase.precondiciones.forEach { FilaPrecondicion(it) }
        Seccion("Pasos")
        fase.pasos.forEachIndexed { i, paso ->
            Text("${i + 1}. $paso", color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        Seccion("Ajustes")
        FilaVref(estado.vref, acciones.onPedirVref)
        FilaVoz(estado.voz, acciones.onVoz)
    }
    PieConAccion(
        texto = "Empezar prueba",
        onClick = acciones.onEmpezar,
        habilitado = fase.listas && !estado.ocupado,
        aviso = if (fase.listas || fase.precondiciones.isEmpty()) null else "Resuelve lo marcado para empezar.",
    )
}

@Composable
private fun FilaPrecondicion(item: ItemPrecondicion) {
    val lado = with(LocalDensity.current) { 20.sp.toDp() }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (item.cumple) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = if (item.cumple) "Cumple" else "No cumple",
            tint = if (item.cumple) RevScopeColors.Success else RevScopeColors.Danger,
            modifier = Modifier.size(lado),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            item.queHacer?.takeIf { !item.cumple }?.let { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.body) }
        }
    }
}

@Composable
private fun FilaVref(vref: ReferenciaVoltaje, onCambiar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Referencia para pasar % a V", color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Text(
                "${FormatoPosicion.voltios(vref.voltios)} · ${vref.origen}",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body.copy(fontFeatureSettings = "tnum"),
            )
        }
        TextButton(onClick = onCambiar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Cambiar", color = RevScopeColors.Accent, style = RevScopeType.label)
        }
    }
}

@Composable
internal fun FilaVoz(voz: Boolean, onVoz: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = voz, role = Role.Switch, onValueChange = onVoz),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Guía por voz", color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Text("Dice cada paso y la cuenta: las manos van en el acelerador", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
        Switch(
            checked = voz,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = RevScopeColors.Accent, checkedThumbColor = RevScopeColors.Background),
        )
    }
}

// ── Editor de la referencia ─────────────────────────────────────────────────

@Composable
internal fun DialogoVref(estado: PruebaGuiadaUi, onGuardar: (Double?) -> Unit, onCerrar: () -> Unit) {
    DialogoReferenciaVoltaje(
        valorInicial = FormatoTaller.numero(estado.vref.voltios, 2),
        leer = ResolutorVref::leer,
        onGuardar = onGuardar,
        onCerrar = onCerrar,
        medida = estado.referencia.medida?.let { FormatoPosicion.voltios(it.voltios) },
        textoRestablecer = ResolutorVref.textoRestablecer(estado.referencia),
    )
}
