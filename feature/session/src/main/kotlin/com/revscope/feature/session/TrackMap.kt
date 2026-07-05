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
private val SlowColor = Color(0xFF3D8BFF)
private val MidColor = Color(0xFFE8FF00)
private val FastColor = Color(0xFFFF3D5A)

private fun speedColor(fraction: Float): Color = if (fraction < 0.5f) {
    lerpColor(SlowColor, MidColor, fraction * 2f)
} else {
    lerpColor(MidColor, FastColor, (fraction - 0.5f) * 2f)
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
)

/**
 * Offline racing-line view of the GPS track: the route drawn as a normalized
 * polyline, no map tiles needed. Longitude is scaled by cos(latitude) so shapes
 * keep real-world proportions. With [speeds], segments grade from slow (blue)
 * to fast (red) like real telemetry software.
 */
@Composable
fun TrackMap(
    track: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    speeds: List<Float> = emptyList(),
    /** true at indices where the vehicle was braking hard — drawn as red dots */
    brakingMask: List<Boolean> = emptyList(),
) {
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

        if (speeds.size == track.size && speeds.isNotEmpty()) {
            // Speed-graded segments: blue (slow) → yellow → red (fast)
            val maxSpeed = speeds.max().coerceAtLeast(1f)
            for (i in 1 until track.size) {
                val fraction = ((speeds[i - 1] + speeds[i]) / 2f / maxSpeed).coerceIn(0f, 1f)
                drawLine(
                    color = speedColor(fraction),
                    start = project(i - 1),
                    end = project(i),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        } else {
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
        }
        if (brakingMask.size == track.size) {
            for (i in track.indices) {
                if (brakingMask[i]) {
                    drawCircle(EndColor, radius = 3.dp.toPx(), center = project(i), alpha = 0.85f)
                }
            }
        }
        drawCircle(StartColor, radius = 6.dp.toPx(), center = project(0))
        drawCircle(EndColor, radius = 6.dp.toPx(), center = project(track.size - 1))
    }
}
