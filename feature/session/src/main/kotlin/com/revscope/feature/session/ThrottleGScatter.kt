package com.revscope.feature.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    Canvas(
        modifier = modifier
            .background(ChartBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        // Grid: vertical every 25 % throttle, horizontal at 0 G and ±0.5 G
        for (pct in 0..4) {
            val x = size.width * pct / 4f
            drawLine(GridColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        listOf(-0.5f, 0f, 0.5f).forEach { g ->
            val y = size.height / 2f - (g / MAX_G) * (size.height / 2f)
            drawLine(GridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }

        points.forEach { (throttle, gLong) ->
            val x = size.width * (throttle.coerceIn(0f, 100f) / 100f)
            val y = size.height / 2f - (gLong.coerceIn(-MAX_G, MAX_G) / MAX_G) * (size.height / 2f)
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
    }
}
