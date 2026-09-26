package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Etiqueta a la izquierda y valor a la derecha. La etiqueta toma el espacio que sobra y envuelve;
 * el valor nunca queda empujado fuera de la fila y usa cifras tabulares.
 */
@Composable
fun FilaEtiquetaValor(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    colorEtiqueta: Color = RevScopeColors.TextSecondary,
    colorValor: Color = RevScopeColors.TextPrimary,
    estiloEtiqueta: TextStyle = RevScopeType.bodySmall,
    estiloValor: TextStyle = RevScopeType.label.conCifrasTabulares(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, color = colorEtiqueta, style = estiloEtiqueta, modifier = Modifier.weight(1f))
        Text(valor, color = colorValor, style = estiloValor, textAlign = TextAlign.End)
    }
}
