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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val CircleBg = Color(0xFF12121A)
private val GridColor = Color(0xFF2A2A38)
private val PointColor = Color(0xFFE8FF00)
private val BrakePointColor = Color(0xFFFF3D5A)

private const val MAX_G_SCALE = 1.2f

// cos(45°) == sin(45°): coloca las etiquetas de los anillos guía en la diagonal,
// lejos del eje vertical donde se agrupan los puntos de frenada/aceleración.
private const val RING_LABEL_DIAGONAL_FACTOR = 0.707f

/**
 * Friction circle: every IMU sample plotted as (lateral G, longitudinal G).
 * The envelope of the cloud IS the grip you actually used — braking points
 * (strong negative longitudinal) drawn in red. Guide rings at 0.5 G and 1.0 G.
 */
@Composable
fun FrictionCircle(
    points: List<Pair<Float, Float>>, // (gLat, gLong)
    modifier: Modifier = Modifier,
) {
    val axisTextPx = with(LocalDensity.current) { 9.sp.toPx() }
    val leftPaint = remember(axisTextPx) { axisLabelPaint(axisTextPx, Paint.Align.LEFT) }
    val rightPaint = remember(axisTextPx) { axisLabelPaint(axisTextPx, Paint.Align.RIGHT) }

    Canvas(
        modifier = modifier
            .background(CircleBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val pxPerG = radius / MAX_G_SCALE

        // Guide rings + axes
        listOf(0.5f, 1.0f).forEach { g ->
            drawCircle(
                color = GridColor,
                radius = g * pxPerG,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
                alpha = CANVAS_CHART_GRID_ALPHA,
            )
        }
        drawLine(
            GridColor,
            Offset(center.x - radius, center.y),
            Offset(center.x + radius, center.y),
            1.dp.toPx(),
            alpha = CANVAS_CHART_GRID_ALPHA,
        )
        drawLine(
            GridColor,
            Offset(center.x, center.y - radius),
            Offset(center.x, center.y + radius),
            1.dp.toPx(),
            alpha = CANVAS_CHART_GRID_ALPHA,
        )

        points.forEach { (gLat, gLong) ->
            val x = center.x + (gLat.coerceIn(-MAX_G_SCALE, MAX_G_SCALE) * pxPerG)
            // Screen up = acceleration, down = braking
            val y = center.y - (gLong.coerceIn(-MAX_G_SCALE, MAX_G_SCALE) * pxPerG)
            drawCircle(
                color = if (gLong < -0.15f) BrakePointColor else PointColor,
                radius = 1.5.dp.toPx(),
                center = Offset(x, y),
                alpha = 0.45f,
            )
        }

        // Ring labels — G magnitude where each guide ring crosses its 45° diagonal,
        // away from the vertical axis where braking/accel points cluster.
        val labelGap = 2.dp.toPx()
        listOf(0.5f, 1.0f).forEach { g ->
            val ringRadius = g * pxPerG
            drawAxisText(
                "${g}G",
                center.x + ringRadius * RING_LABEL_DIAGONAL_FACTOR + labelGap,
                center.y - ringRadius * RING_LABEL_DIAGONAL_FACTOR - labelGap,
                leftPaint,
            )
        }

        // Axis names in the corners.
        val margin = 4.dp.toPx()
        drawAxisText("G longitudinal", margin, 10.dp.toPx(), leftPaint)
        drawAxisText("G lateral", size.width - margin, size.height - 2.dp.toPx(), rightPaint)
    }
}
