package com.revscope.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ColoresSerie = listOf(RevScopeColors.Accent, Color(0xFF4FC3F7), RevScopeColors.Warning, Color(0xFFE59CFF))

private val ColorBanda = RevScopeColors.Success
private const val ESCALA_LETRA_MAXIMA_GRAFICA = 1.5f

/**
 * Serie en el tiempo dibujada a mano: bandas de referencia sombreadas con su etiqueta, tramos del eje X
 * (pasos de una prueba), trazos continuo/discontinuo/punteado y etiqueta directa al final de cada serie.
 * TalkBack lee [ModeloGrafica.descripcion], que la pantalla arma con los valores y la tendencia.
 */
@Composable
fun GraficaSerie(modelo: ModeloGrafica, modifier: Modifier = Modifier, alto: Dp = 200.dp) {
    val medidor = rememberTextMeasurer()
    val estiloTexto = estiloTextoGrafica()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(alto)
            .semantics { contentDescription = modelo.descripcion },
    ) {
        dibujarGrafica(modelo, medidor, estiloTexto)
    }
}

// Las etiquetas del lienzo crecen con la letra del sistema, pero hasta 1,5×: más grandes taparían las curvas.
@Composable
private fun estiloTextoGrafica(): TextStyle {
    val escala = LocalDensity.current.fontScale
    val tamano = 11f * minOf(escala, ESCALA_LETRA_MAXIMA_GRAFICA) / escala
    return RevScopeType.bodySmall.conCifrasTabulares().copy(fontSize = tamano.sp, lineHeight = (tamano * 1.2f).sp)
}

private class Lienzo(val area: Rect, val x: ClosedFloatingPointRange<Double>, val y: ClosedFloatingPointRange<Double>) {
    fun px(valor: Double): Float = area.left + ((valor - x.start) / (x.endInclusive - x.start)).toFloat() * area.width
    fun py(valor: Double): Float = area.bottom - ((valor - y.start) / (y.endInclusive - y.start)).toFloat() * area.height
}

private fun DrawScope.dibujarGrafica(m: ModeloGrafica, medidor: TextMeasurer, estilo: TextStyle) {
    val rx = EscalaGrafica.rangoX(m)
    val ry = EscalaGrafica.rangoY(m)
    val marcasY = EscalaGrafica.marcas(ry)
    val pasoY = EscalaGrafica.paso(ry, 4)
    val textosY = marcasY.map { medidor.measure(EscalaGrafica.texto(it, pasoY), estilo.copy(color = RevScopeColors.TextSecondary)) }
    val altoTexto = medidor.measure("0", estilo).size.height.toFloat()
    val izquierda = (textosY.maxOfOrNull { it.size.width } ?: 0) + 6.dp.toPx()
    val arriba = if (m.tramos.isEmpty()) 4.dp.toPx() else altoTexto + 4.dp.toPx()
    val area = Rect(izquierda, arriba, size.width - 4.dp.toPx(), size.height - altoTexto - 6.dp.toPx())
    if (area.width <= 0f || area.height <= 0f) return
    val lienzo = Lienzo(area, rx, ry)
    dibujarTramos(m.tramos, lienzo, medidor, estilo)
    dibujarRejilla(marcasY, textosY, lienzo)
    dibujarEjeX(rx, m.unidadX, lienzo, medidor, estilo)
    clipRect(area.left, area.top, area.right, area.bottom) {
        dibujarBandas(m.bandas, lienzo)
        m.series.forEachIndexed { i, serie -> dibujarSerie(serie, ColoresSerie[i % ColoresSerie.size], lienzo) }
        dibujarEtiquetasBandas(m.bandas, lienzo, medidor, estilo)
    }
    dibujarEtiquetasDirectas(m.series, lienzo, medidor, estilo)
}

private fun DrawScope.dibujarTramos(tramos: List<TramoGrafica>, l: Lienzo, medidor: TextMeasurer, estilo: TextStyle) {
    tramos.forEachIndexed { i, tramo ->
        val desde = l.px(tramo.desde).coerceIn(l.area.left, l.area.right)
        val hasta = l.px(tramo.hasta).coerceIn(l.area.left, l.area.right)
        if (hasta - desde < 1f) return@forEachIndexed
        val alfa = if (i % 2 == 0) 0.10f else 0.05f
        drawRect(RevScopeColors.TextPrimary.copy(alpha = alfa), Offset(desde, l.area.top), Size(hasta - desde, l.area.height))
        val texto = medidor.measure(tramo.etiqueta, estilo.copy(color = RevScopeColors.TextSecondary))
        if (texto.size.width <= hasta - desde) {
            drawText(texto, topLeft = Offset(desde + (hasta - desde - texto.size.width) / 2, l.area.top - texto.size.height - 2.dp.toPx()))
        }
    }
}

private fun DrawScope.dibujarRejilla(marcas: List<Double>, textos: List<TextLayoutResult>, l: Lienzo) {
    marcas.zip(textos).forEach { (valor, texto) ->
        val y = l.py(valor)
        drawLine(RevScopeColors.TextMuted.copy(alpha = 0.35f), Offset(l.area.left, y), Offset(l.area.right, y), 1.dp.toPx())
        drawText(texto, topLeft = Offset(l.area.left - texto.size.width - 4.dp.toPx(), y - texto.size.height / 2))
    }
}

private fun DrawScope.dibujarEjeX(rango: ClosedFloatingPointRange<Double>, unidad: String, l: Lienzo, medidor: TextMeasurer, estilo: TextStyle) {
    val paso = EscalaGrafica.paso(rango, 4)
    val marcas = EscalaGrafica.marcas(rango)
    marcas.forEachIndexed { i, valor ->
        val etiqueta = EscalaGrafica.texto(valor, paso) + if (i == marcas.lastIndex) " $unidad" else ""
        val texto = medidor.measure(etiqueta, estilo.copy(color = RevScopeColors.TextSecondary))
        val x = (l.px(valor) - texto.size.width / 2).coerceIn(l.area.left, size.width - texto.size.width)
        drawText(texto, topLeft = Offset(x, l.area.bottom + 4.dp.toPx()))
    }
}

private fun DrawScope.dibujarBandas(bandas: List<BandaGrafica>, l: Lienzo) {
    bandas.forEach { banda ->
        val arriba = l.py(banda.hasta)
        val abajo = l.py(banda.desde)
        drawRect(ColorBanda.copy(alpha = 0.14f), Offset(l.area.left, arriba), Size(l.area.width, abajo - arriba))
        val borde = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        listOf(arriba, abajo).forEach { y ->
            drawLine(ColorBanda.copy(alpha = 0.6f), Offset(l.area.left, y), Offset(l.area.right, y), 1.dp.toPx(), pathEffect = borde)
        }
    }
}

// Encima de las curvas y con fondo: la línea no tapa el nombre de la banda.
private fun DrawScope.dibujarEtiquetasBandas(bandas: List<BandaGrafica>, l: Lienzo, medidor: TextMeasurer, estilo: TextStyle) {
    bandas.forEach { banda ->
        val arriba = l.py(banda.hasta)
        val abajo = l.py(banda.desde)
        val texto = medidor.measure(banda.etiqueta, estilo.copy(color = RevScopeColors.TextPrimary))
        val origen = Offset(l.area.left + 4.dp.toPx(), (arriba + 2.dp.toPx()).coerceAtMost(abajo - texto.size.height))
        drawRect(RevScopeColors.Background.copy(alpha = 0.7f), Offset(origen.x - 2.dp.toPx(), origen.y), Size(texto.size.width + 4.dp.toPx(), texto.size.height.toFloat()))
        drawText(texto, topLeft = origen)
    }
}

private fun DrawScope.dibujarSerie(serie: SerieGrafica, color: Color, l: Lienzo) {
    if (serie.puntos.size < 2) return
    val camino = Path()
    serie.puntos.forEachIndexed { i, p ->
        if (i == 0) camino.moveTo(l.px(p.x), l.py(p.y)) else camino.lineTo(l.px(p.x), l.py(p.y))
    }
    drawPath(camino, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = efecto(serie.estilo)))
}

private fun DrawScope.efecto(estilo: EstiloLinea): PathEffect? = when (estilo) {
    EstiloLinea.CONTINUA -> null
    EstiloLinea.DISCONTINUA -> PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
    EstiloLinea.PUNTEADA -> PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx()))
}

// Etiqueta al final de cada línea, sobre un fondo para que se lea encima de la rejilla; si dos chocan, se apilan.
private fun DrawScope.dibujarEtiquetasDirectas(series: List<SerieGrafica>, l: Lienzo, medidor: TextMeasurer, estilo: TextStyle) {
    var ocupadoHasta = Float.NEGATIVE_INFINITY
    series.mapIndexedNotNull { i, s -> s.puntos.lastOrNull()?.let { Triple(i, s, l.py(it.y)) } }
        .sortedBy { it.third }
        .forEach { (i, serie, y) ->
            val texto = medidor.measure(serie.etiqueta, estilo.copy(color = ColoresSerie[i % ColoresSerie.size]))
            val alto = texto.size.height.toFloat()
            val arriba = maxOf(y - alto - 2.dp.toPx(), ocupadoHasta).coerceIn(l.area.top, l.area.bottom - alto)
            val izquierda = (l.area.right - texto.size.width - 4.dp.toPx()).coerceAtLeast(l.area.left)
            drawRect(RevScopeColors.Background.copy(alpha = 0.75f), Offset(izquierda - 2.dp.toPx(), arriba), Size(texto.size.width + 4.dp.toPx(), alto))
            drawText(texto, topLeft = Offset(izquierda, arriba))
            ocupadoHasta = arriba + alto
        }
}
