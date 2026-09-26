package com.revscope.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp

fun textoPaso(actual: Int, total: Int, titulo: String? = null): String {
    val paso = "Paso ${actual.coerceIn(1, total)} de $total"
    return titulo?.let { "$paso: $it" } ?: paso
}

/** Progreso de un flujo guiado: «Paso 2 de 4» en texto y en segmentos, no solo con la barra. */
@Composable
fun IndicadorPasos(
    actual: Int,
    total: Int,
    modifier: Modifier = Modifier,
    titulo: String? = null,
) {
    val texto = textoPaso(actual, total, titulo)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = texto
            progressBarRangeInfo = ProgressBarRangeInfo(actual.toFloat(), 0f..total.toFloat(), steps = total - 1)
        },
    ) {
        Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(total) { indice -> SegmentoPaso(completado = indice < actual, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SegmentoPaso(completado: Boolean, modifier: Modifier) {
    val color = if (completado) RevScopeColors.Accent else RevScopeColors.TextMuted
    Box(modifier.height(4.dp).background(color, RoundedCornerShape(2.dp)))
}
