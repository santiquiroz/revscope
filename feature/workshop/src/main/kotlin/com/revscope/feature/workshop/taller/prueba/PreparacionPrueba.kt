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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.DialogoReferenciaVoltaje
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.obd.R as ObdR
import com.revscope.core.obd.taller.grafica.ResolutorVref
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.feature.workshop.R

// ── Elegir prueba ───────────────────────────────────────────────────────────

@Composable
internal fun ColumnScope.ElegirPrueba(fase: FasePantalla.Elegir, acciones: AccionesPrueba) {
    BarraConVolver(titulo = stringResource(R.string.workshop_prueba_pantalla_titulo), onVolver = acciones.onVolver)
    CuerpoDesplazable {
        Text(
            stringResource(R.string.workshop_prueba_pantalla_elegir_intro),
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
                Text(stringResource(opcion.tipo.tituloRes), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                Text(opcion.descripcion, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
                if (!opcion.disponible) Text(stringResource(R.string.workshop_prueba_pantalla_proxima_version), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            }
        }
    }
}

// ── Preparación: qué se hará, precondiciones detectadas y ajustes ───────────

@Composable
internal fun ColumnScope.PreparacionPrueba(fase: FasePantalla.Preparacion, estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    BarraConVolver(titulo = stringResource(fase.tipo.tituloRes), subtitulo = estado.vehiculo, onVolver = acciones.onVolver)
    CuerpoDesplazable {
        Text(fase.descripcion, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        Seccion(stringResource(R.string.workshop_prueba_pantalla_antes_de_empezar))
        if (fase.precondiciones.isEmpty()) {
            Text(stringResource(R.string.workshop_prueba_pantalla_comprobando), color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        }
        fase.precondiciones.forEach { FilaPrecondicion(it) }
        Seccion(stringResource(R.string.workshop_prueba_pantalla_pasos))
        fase.pasos.forEachIndexed { i, paso ->
            Text(stringResource(R.string.workshop_prueba_pantalla_paso_numerado, i + 1, paso), color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        Seccion(stringResource(R.string.workshop_prueba_pantalla_ajustes))
        if (fase.usaVref) FilaVref(estado.vref, LocalConfiguration.current.locales[0], acciones.onPedirVref)
        if (fase.usaDesfase) FilaDesfase(estado.desfase, acciones.onPedirDesfase)
        FilaVoz(estado.voz, acciones.onVoz)
    }
    PieConAccion(
        texto = stringResource(R.string.workshop_prueba_pantalla_empezar),
        onClick = acciones.onEmpezar,
        habilitado = fase.listas && !estado.ocupado,
        aviso = if (fase.listas || fase.precondiciones.isEmpty()) null else stringResource(R.string.workshop_prueba_pantalla_resolver_marcado),
    )
}

@Composable
private fun FilaPrecondicion(item: ItemPrecondicion) {
    val lado = with(LocalDensity.current) { 20.sp.toDp() }
    val (icono, color, nombre) = marcaPrecondicion(item)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icono, contentDescription = stringResource(nombre), tint = color, modifier = Modifier.size(lado))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            item.queHacer?.takeIf { !item.cumple || item.aviso }?.let {
                Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
            }
        }
    }
}

// Un aviso deja empezar, pero con ícono y texto propios: no se confunde con «cumple» ni con «no cumple».
private fun marcaPrecondicion(item: ItemPrecondicion): Triple<ImageVector, Color, Int> = when {
    !item.cumple -> Triple(Icons.Filled.Cancel, RevScopeColors.Danger, R.string.workshop_prueba_pantalla_no_cumple)
    item.aviso -> Triple(Icons.Filled.Warning, RevScopeColors.Warning, R.string.workshop_prueba_pantalla_aviso)
    else -> Triple(Icons.Filled.CheckCircle, RevScopeColors.Success, R.string.workshop_prueba_pantalla_cumple)
}

@Composable
private fun FilaVref(vref: ReferenciaVoltaje, locale: java.util.Locale, onCambiar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.workshop_prueba_pantalla_vref_titulo), color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Text(
                stringResource(R.string.workshop_prueba_pantalla_vref_valor, LocaleNumbers.decimal(vref.voltios, 2, locale), vref.origen),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body.copy(fontFeatureSettings = "tnum"),
            )
        }
        TextButton(onClick = onCambiar, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.workshop_prueba_pantalla_cambiar), color = RevScopeColors.Accent, style = RevScopeType.label)
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
            Text(stringResource(R.string.workshop_prueba_pantalla_voz), color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            Text(stringResource(R.string.workshop_prueba_pantalla_voz_descripcion), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
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
    val medida = estado.referencia.medida?.let { "${LocaleNumbers.decimal(it.voltios, 2, LocalConfiguration.current.locales[0])} V" }
    val textoRestablecer = when {
        estado.referencia.editadaV == null -> null
        medida != null -> stringResource(ObdR.string.obd_vref_use_multimeter, medida)
        else -> stringResource(ObdR.string.obd_vref_restore_typical)
    }
    DialogoReferenciaVoltaje(
        valorInicial = FormatoTaller.numero(estado.vref.voltios, 2),
        leer = ResolutorVref::leer,
        onGuardar = onGuardar,
        onCerrar = onCerrar,
        medida = medida,
        textoRestablecer = textoRestablecer,
    )
}
