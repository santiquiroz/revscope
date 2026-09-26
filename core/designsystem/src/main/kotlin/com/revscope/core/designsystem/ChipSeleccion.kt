package com.revscope.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Chip de selección con los colores de RevScope. El seleccionado lleva check además del color,
 * y FilterChip da el área táctil de 48 dp y el estado «seleccionado» a TalkBack.
 */
@Composable
fun ChipSeleccion(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    monoespaciado: Boolean = false,
) {
    FilterChip(
        selected = seleccionado,
        onClick = onClick,
        label = {
            Text(
                texto,
                style = RevScopeType.bodySmall,
                fontFamily = if (monoespaciado) FontFamily.Monospace else null,
            )
        },
        leadingIcon = if (seleccionado) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = RevScopeColors.SurfaceHigh,
            labelColor = RevScopeColors.TextSecondary,
            selectedContainerColor = RevScopeColors.Accent,
            selectedLabelColor = RevScopeColors.Background,
            selectedLeadingIconColor = RevScopeColors.Background,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = seleccionado,
            borderColor = RevScopeColors.SurfaceHigh,
            selectedBorderColor = RevScopeColors.Accent,
        ),
        modifier = modifier,
    )
}
