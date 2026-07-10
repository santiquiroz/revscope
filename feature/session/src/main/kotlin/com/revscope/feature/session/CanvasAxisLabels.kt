package com.revscope.feature.session

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

internal const val CANVAS_CHART_GRID_ALPHA = 0.15f
internal val CanvasAxisLabelColor = Color(0xFF6B7089)

/** Small muted text paint for axis ticks and unit labels on hand-drawn Canvas charts. */
internal fun axisLabelPaint(
    textSizePx: Float,
    align: Paint.Align = Paint.Align.LEFT,
    color: Color = CanvasAxisLabelColor,
): Paint = Paint().apply {
    this.color = color.toArgb()
    this.textSize = textSizePx
    this.isAntiAlias = true
    this.textAlign = align
}

internal fun DrawScope.drawAxisText(text: String, x: Float, y: Float, paint: Paint) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
