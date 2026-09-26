package com.revscope.feature.workshop.taller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.color
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.taller.sesion.Veredicto

@Composable
internal fun EventoLinea(evento: EventoUi, ultimo: Boolean, abierto: Boolean, onAlternar: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RielEvento(evento, ultimo)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
            Text(evento.hora, color = RevScopeColors.TextSecondary, style = RevScopeType.label.conCifrasTabulares())
            Text(evento.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
            if (evento.veredicto != Veredicto.INFO) NivelBadge(evento.veredicto.nivel(), texto = evento.veredicto.texto())
            if (evento.resumen.isNotBlank()) {
                Text(evento.resumen, color = RevScopeColors.TextPrimary, style = RevScopeType.body.conCifrasTabulares())
            }
            BotonDetalle(abierto, onAlternar)
            if (abierto) evento.detalle.forEach { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
        }
    }
}

@Composable
private fun RielEvento(evento: EventoUi, ultimo: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp).fillMaxHeight()) {
        Surface(shape = CircleShape, color = RevScopeColors.SurfaceHigh, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(evento.tipo.icono(), contentDescription = null, tint = colorIcono(evento.veredicto), modifier = Modifier.size(22.dp))
            }
        }
        if (!ultimo) {
            Box(Modifier.width(2.dp).weight(1f).background(RevScopeColors.TextMuted.copy(alpha = 0.5f)))
        }
    }
}

private fun colorIcono(veredicto: Veredicto) =
    if (veredicto == Veredicto.INFO) RevScopeColors.TextPrimary else veredicto.nivel().color()

@Composable
private fun BotonDetalle(abierto: Boolean, onAlternar: () -> Unit) {
    TextButton(
        onClick = onAlternar,
        contentPadding = PaddingValues(end = 12.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(if (abierto) "Ocultar detalle" else "Ver detalle", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        Icon(
            if (abierto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = RevScopeColors.TextPrimary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
