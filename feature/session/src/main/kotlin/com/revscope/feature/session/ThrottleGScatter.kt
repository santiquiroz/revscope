package com.revscope.feature.session

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChartBg = Color(0xFF12121A)
private val GridColor = Color(0xFF2A2A38)
private val AccelColor = Color(0xFF3DFF8E)
private val CoastColor = Color(0xFFE8FF00)
private val BrakeColor = Color(0xFFFF3D5A)

private const val MAX_G = 1.0f

/**
 * Tuning view: throttle input (x, 0–100 %) vs what the vehicle actually did
 * (y, longitudinal G). A healthy drivetrain shows a rising diagonal band;
 * flat spots at high throttle reveal fueling/clutch issues. Braking while
 * on throttle shows up as red points on the right half.
 */
@Composable
fun ThrottleGScatter(
    points: List<Pair<Float, Float>>, // (throttle %, gLong)
    modifier: Modifier = Modifier,
) {
    val axisTextPx = with(LocalDensity.current) { 9.sp.toPx() }
    val leftPaint = remember(axisTextPx) { axisLabelPaint(axisTextPx, Paint.Align.LEFT) }
    val centerPaint = remember(axisTextPx) { axisLabelPaint(axisTextPx, Paint.Align.CENTER) }
    val rightPaint = remember(axisTextPx) { axisLabelPaint(axisTextPx, Paint.Align.RIGHT) }

    Canvas(
        modifier = modifier
            .background(ChartBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        // Grid: vertical every 25 % throttle, horizontal at 0 G and ±0.5 G
        for (pct in 0..4) {
            val x = size.width * pct / 4f
            drawLine(GridColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(), alpha = CANVAS_CHART_GRID_ALPHA)
        }
        listOf(-0.5f, 0f, 0.5f).forEach { g ->
            val y = size.height / 2f - (g / MAX_G) * (size.height / 2f)
            drawLine(GridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), alpha = CANVAS_CHART_GRID_ALPHA)
        }

        // Reserva un gutter para que las marcas extremas (acelerador 100 %, frenada
        // fuerte) no queden debajo de las etiquetas de las esquinas.
        val plotGutter = 12.dp.toPx()
        val plotWidth = size.width - plotGutter
        val plotHeight = size.height - plotGutter

        points.forEach { (throttle, gLong) ->
            val x = plotWidth * (throttle.coerceIn(0f, 100f) / 100f)
            val y = plotHeight / 2f - (gLong.coerceIn(-MAX_G, MAX_G) / MAX_G) * (plotHeight / 2f)
            drawCircle(
                color = when {
                    gLong < -0.15f -> BrakeColor
                    gLong > 0.1f -> AccelColor
                    else -> CoastColor
                },
                radius = 1.5.dp.toPx(),
                center = Offset(x, y),
                alpha = 0.5f,
            )
        }

        val margin = 4.dp.toPx()
        val bottomRow = size.height - 2.dp.toPx()
        val raisedRow = size.height - 14.dp.toPx()

        // X ticks: throttle % (min/mid/max), bottom edge
        drawAxisText("0%", margin, bottomRow, leftPaint)
        drawAxisText("50%", size.width / 2f, bottomRow, centerPaint)
        drawAxisText("100%", size.width - margin, bottomRow, rightPaint)

        // Y ticks: longitudinal G (min/mid/max), left edge
        drawAxisText("+${MAX_G}G", margin, 10.dp.toPx(), leftPaint)
        drawAxisText("0G", margin, size.height / 2f + 4.dp.toPx(), leftPaint)
        drawAxisText("-${MAX_G}G", margin, raisedRow, leftPaint)

        // Axis names in the corners, raised above the tick row they belong to — this
        // chart plots longitudinal G (accel/brake), not lateral G.
        drawAxisText("G longitudinal", size.width - margin, 10.dp.toPx(), rightPaint)
        drawAxisText("% acelerador", size.width - margin, raisedRow, rightPaint)
    }
}
