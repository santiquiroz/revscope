package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

const val DESCRIPCION_VOLVER = "Volver"

/**
 * Barra de título de las pantallas secundarias con «Volver» visible. No usa TopAppBar porque su
 * alto fijo recorta un título que envuelve a dos líneas con la letra al 200 %.
 */
@Composable
fun BarraConVolver(
    titulo: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = RevScopeColors.Surface, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 56.dp)
                .padding(end = 4.dp),
        ) {
            IconButton(onClick = onVolver, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, DESCRIPCION_VOLVER, tint = RevScopeColors.TextPrimary)
            }
            TituloBarra(titulo, subtitulo, Modifier.weight(1f).padding(vertical = 8.dp))
            acciones()
        }
    }
}

@Composable
private fun TituloBarra(titulo: String, subtitulo: String?, modifier: Modifier) {
    Column(modifier) {
        Text(
            titulo,
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.title,
            modifier = Modifier.semantics { heading() },
        )
        subtitulo?.let { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
    }
}
