package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class AccionEstado(val texto: String, val onClick: () -> Unit)

/** Pantalla o lista sin contenido: qué pasa y, si la hay, la acción que lo resuelve. */
@Composable
fun EmptyState(
    mensaje: String,
    modifier: Modifier = Modifier,
    accion: AccionEstado? = null,
    icono: ImageVector = Icons.Filled.Inbox,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
    ) {
        Icon(icono, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(40.dp))
        Text(mensaje, color = RevScopeColors.TextSecondary, style = RevScopeType.body, textAlign = TextAlign.Center)
        accion?.let { BotonPrincipalEstado(it) }
    }
}

/** Error con mensaje y un «Reintentar» que repite la operación, no que solo limpia el aviso. */
@Composable
fun ErrorState(
    mensaje: String,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    textoReintentar: String = "Reintentar",
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = RevScopeColors.Danger, modifier = Modifier.size(40.dp))
        Text(
            mensaje,
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedButton(onClick = onReintentar, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(textoReintentar, color = RevScopeColors.Accent, style = RevScopeType.label)
        }
    }
}

@Composable
private fun BotonPrincipalEstado(accion: AccionEstado) {
    Button(
        onClick = accion.onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = RevScopeColors.Accent,
            contentColor = RevScopeColors.Background,
        ),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(accion.texto, style = RevScopeType.label, textAlign = TextAlign.Center)
    }
}
