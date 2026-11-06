package com.revscope.feature.dtc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.taller.dtc.GuiaDtc
import com.revscope.core.obd.taller.dtc.PasosGuia
import com.revscope.core.obd.taller.dtc.UrgenciaDtc
import com.revscope.feature.dtc.R

data class EstadoTarjetaDtc(
    val item: DtcCodeUi,
    val guiaAbierta: Boolean,
    val pasosMarcados: Set<String>,
    val borradoHabilitado: Boolean,
)

@Composable
internal fun TarjetaCodigoDtc(estado: EstadoTarjetaDtc, acciones: AccionesDtc, resolver: StringResolver) {
    val item = estado.item
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        EncabezadoCodigo(item, resolver)
        item.guia?.let { guia ->
            Text(guia.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
            UrgenciaFila(guia.urgencia, resolver)
            HorizontalDivider(color = RevScopeColors.SurfaceHigh)
            GuiaPlegable(guia, estado, acciones, resolver)
        } ?: SinGuiaLocal(item)
        ExplicacionIaSeccion(item, acciones)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncabezadoCodigo(item: DtcCodeUi, resolver: StringResolver) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.codigo,
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.title.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.semantics { heading() },
        )
        item.modos.forEach { NivelBadge(nivelModo(it), texto = etiquetaModo(it, resolver)) }
    }
}

private fun nivelModo(modo: DtcMode): NivelEstado = if (modo == DtcMode.Pending) NivelEstado.ATENCION else NivelEstado.FALLA

@Composable
private fun UrgenciaFila(urgencia: UrgenciaDtc, resolver: StringResolver) {
    val (icono, color) = estiloUrgencia(urgencia)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.dtc_urgency_label, urgencia.etiqueta(resolver)),
            color = color,
            style = RevScopeType.label,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun estiloUrgencia(urgencia: UrgenciaDtc): Pair<ImageVector, Color> = when (urgencia) {
    UrgenciaDtc.DETENERSE -> Icons.Filled.Warning to RevScopeColors.Danger
    UrgenciaDtc.REVISAR_PRONTO -> Icons.Filled.PriorityHigh to RevScopeColors.Warning
    UrgenciaDtc.PUEDE_ESPERAR -> Icons.Filled.Schedule to RevScopeColors.TextSecondary
}

@Composable
private fun GuiaPlegable(
    guia: GuiaDtc,
    estado: EstadoTarjetaDtc,
    acciones: AccionesDtc,
    resolver: StringResolver,
) {
    val total = guia.verificaciones.size
    val hechos = PasosGuia.contarMarcados(estado.pasosMarcados, guia.codigo, total)
    EncabezadoGuia(
        abierta = estado.guiaAbierta,
        progreso = pluralStringResource(R.plurals.dtc_guide_progress, total, hechos, total),
    ) {
        acciones.onAlternarGuia(guia.codigo)
    }
    if (estado.guiaAbierta) CuerpoGuia(guia, estado, acciones, resolver)
}

@Composable
private fun EncabezadoGuia(abierta: Boolean, progreso: String, onAlternar: () -> Unit) {
    val descripcionEstado = stringResource(if (abierta) R.string.dtc_expanded else R.string.dtc_collapsed)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onAlternar)
            .semantics { stateDescription = descripcionEstado },
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.dtc_diagnostic_guide), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            Text(progreso, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
        Icon(
            if (abierta) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = RevScopeColors.TextSecondary,
        )
    }
}

@Composable
private fun CuerpoGuia(guia: GuiaDtc, estado: EstadoTarjetaDtc, acciones: AccionesDtc, resolver: StringResolver) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvisoOrientacion()
        ListaNumerada(stringResource(R.string.dtc_likely_causes), guia.causas)
        TituloSeccion(stringResource(R.string.dtc_checks_in_order))
        guia.verificaciones.forEachIndexed { indice, verificacion ->
            val numero = indice + 1
            PasoVerificacion(
                PasoVerificacionUi(
                    numero = numero,
                    verificacion = verificacion,
                    marcado = PasosGuia.marcado(estado.pasosMarcados, guia.codigo, numero),
                    borradoHabilitado = estado.borradoHabilitado,
                ),
                onMarcar = { marcado -> acciones.onMarcarPaso(PasosGuia.clave(guia.codigo, numero), marcado) },
                acciones = acciones,
                resolver = resolver,
            )
        }
        if (guia.notasMoto.isNotEmpty()) ListaNumerada(stringResource(R.string.dtc_motorcycle_notes), guia.notasMoto, numerada = false)
        if (guia.relacionados.isNotEmpty()) {
            Text(
                stringResource(R.string.dtc_related_codes, guia.relacionados.joinToString(" · ")),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
        }
    }
}

@Composable
private fun AvisoOrientacion() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.dtc_orientation_notice),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        texto,
        color = RevScopeColors.TextPrimary,
        style = RevScopeType.label,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ListaNumerada(titulo: String, lineas: List<String>, numerada: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TituloSeccion(titulo)
        lineas.forEachIndexed { indice, linea ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (numerada) "${indice + 1}." else "•",
                    color = RevScopeColors.TextSecondary,
                    style = RevScopeType.body.conCifrasTabulares(),
                )
                Text(linea, color = RevScopeColors.TextPrimary, style = RevScopeType.body, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SinGuiaLocal(item: DtcCodeUi) {
    item.sinGuia ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(18.dp))
        Text(item.sinGuia, color = RevScopeColors.TextSecondary, style = RevScopeType.body, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ExplicacionIaSeccion(item: DtcCodeUi, acciones: AccionesDtc) {
    when (val explicacion = item.explicacion) {
        ExplicacionIa.NoPedida -> BotonExplicarIa(texto = stringResource(R.string.dtc_explain_with_ai)) {
            acciones.onExplicarIa(item.codigo)
        }
        ExplicacionIa.Cargando -> CargandoExplicacion()
        ExplicacionIa.NoDisponible -> Column {
            Text(stringResource(R.string.dtc_ai_explanation_unavailable), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            BotonExplicarIa(texto = stringResource(R.string.dtc_retry_ai_explanation)) { acciones.onExplicarIa(item.codigo) }
        }
        is ExplicacionIa.Lista -> ExplicacionLista(explicacion, acciones.onOpenAiValue)
    }
}

@Composable
private fun BotonExplicarIa(texto: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
        Text(texto, color = RevScopeColors.Accent, style = RevScopeType.label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CargandoExplicacion() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RevScopeColors.Accent)
        Text(stringResource(R.string.dtc_loading_ai_explanation), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@Composable
private fun ExplicacionLista(explicacion: ExplicacionIa.Lista, onOpenAiValue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.dtc_ai_explanation), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        Text(explicacion.texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        if (explicacion.faltaConfigurar) {
            TextButton(onClick = onOpenAiValue, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.dtc_configure_ai), color = RevScopeColors.Accent, style = RevScopeType.label)
            }
        }
    }
}
