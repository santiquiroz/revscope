package com.revscope.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Aviso con un botón de cerrar visible y con nombre, en vez de cerrarse tocando el texto. */
@Composable
fun AvisoDescartable(
    texto: String,
    onDescartar: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = RevScopeColors.Warning,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(start = 12.dp),
    ) {
        Text(
            texto,
            color = color,
            style = RevScopeType.bodySmall,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        IconButton(onClick = onDescartar) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", tint = RevScopeColors.TextSecondary)
        }
    }
}
