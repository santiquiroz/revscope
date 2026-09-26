package com.revscope.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Cifras de ancho fijo: un valor en vivo que pasa de 99 a 100 no hace bailar lo que tiene al lado.
const val CIFRAS_TABULARES = "tnum"

fun TextStyle.conCifrasTabulares(): TextStyle = copy(fontFeatureSettings = CIFRAS_TABULARES)

object RevScopeType {
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val numeros = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold).conCifrasTabulares()
}
