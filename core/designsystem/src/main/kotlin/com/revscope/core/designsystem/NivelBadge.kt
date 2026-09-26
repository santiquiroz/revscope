package com.revscope.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NivelEstado(val textoPorDefecto: String) {
    OK("OK"),
    ATENCION("Atención"),
    FALLA("Falla"),
    SIN_DATO("Sin dato"),
}

fun NivelEstado.color(): Color = when (this) {
    NivelEstado.OK -> RevScopeColors.Success
    NivelEstado.ATENCION -> RevScopeColors.Warning
    NivelEstado.FALLA -> RevScopeColors.Danger
    NivelEstado.SIN_DATO -> RevScopeColors.TextSecondary
}

fun NivelEstado.icono(): ImageVector = when (this) {
    NivelEstado.OK -> Icons.Filled.CheckCircle
    NivelEstado.ATENCION -> Icons.Filled.Error
    NivelEstado.FALLA -> Icons.Filled.Cancel
    NivelEstado.SIN_DATO -> Icons.Filled.RemoveCircle
}

/**
 * Estado con ícono y texto, nunca solo con color. Va sobre SurfaceHigh porque ahí el rojo aún
 * da 4,6:1; el ícono es decorativo y el texto es lo que lee TalkBack.
 */
@Composable
fun NivelBadge(
    nivel: NivelEstado,
    modifier: Modifier = Modifier,
    texto: String = nivel.textoPorDefecto,
) {
    val color = nivel.color()
    val lado = with(LocalDensity.current) { 14.sp.toDp() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(nivel.icono(), contentDescription = null, tint = color, modifier = Modifier.size(lado))
        Text(texto, color = color, style = RevScopeType.bodySmall)
    }
}
