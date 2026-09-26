package com.revscope.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Una línea que se achica hasta [minimo] para caber en su espacio en vez de partirse o recortarse.
 * Para números de gauges y etiquetas cortas de navegación, donde envolver no tiene sentido.
 */
@Composable
fun TextoAjustable(
    texto: String,
    estilo: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maximo: TextUnit = estilo.fontSize,
    minimo: TextUnit = 10.sp,
    alineacion: TextAlign = TextAlign.Center,
) {
    BasicText(
        text = texto,
        style = estilo.copy(color = color, textAlign = alineacion),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = minimo, maxFontSize = maximo),
        modifier = modifier,
    )
}
