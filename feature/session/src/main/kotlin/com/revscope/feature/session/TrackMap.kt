package com.revscope.feature.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos

private val MapBg = Color(0xFF12121A)
private val TrackColor = Color(0xFFE8FF00)
private val StartColor = Color(0xFF3DFF8E)
private val EndColor = Color(0xFFFF3D5A)

/**
 * Offline racing-line view of the GPS track: the route drawn as a normalized
 * polyline, no map tiles needed. Longitude is scaled by cos(latitude) so shapes
 * keep real-world proportions.
 */
@Composable
fun TrackMap(track: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .background(MapBg, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        if (track.size < 2) return@Canvas

        val midLatRad = Math.toRadians(track.map { it.first }.average())
        val lonScale = cos(midLatRad)

        // Project: x = lon·cos(midLat), y = lat (inverted — screen y grows downward)
        val xs = track.map { it.second * lonScale }
        val ys = track.map { it.first }
        val minX = xs.min(); val maxX = xs.max()
        val minY = ys.min(); val maxY = ys.max()
        val spanX = (maxX - minX).takeIf { it > 0 } ?: 1e-9
        val spanY = (maxY - minY).takeIf { it > 0 } ?: 1e-9

        // Aspect-fit into the canvas, centered
        val scale = minOf(size.width / spanX, size.height / spanY).toFloat()
        val offsetX = (size.width - (spanX * scale).toFloat()) / 2f
        val offsetY = (size.height - (spanY * scale).toFloat()) / 2f

        fun project(index: Int) = Offset(
            offsetX + ((xs[index] - minX) * scale).toFloat(),
            offsetY + ((maxY - ys[index]) * scale).toFloat(),
        )

        val path = Path().apply {
            val first = project(0)
            moveTo(first.x, first.y)
            for (i in 1 until track.size) {
                val p = project(i)
                lineTo(p.x, p.y)
            }
        }
        drawPath(
            path = path,
            color = TrackColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(StartColor, radius = 6.dp.toPx(), center = project(0))
        drawCircle(EndColor, radius = 6.dp.toPx(), center = project(track.size - 1))
    }
}
