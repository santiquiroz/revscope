package com.revscope.feature.dashboard.gauges

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.feature.dashboard.ui.RevScopeColors

// Upper semicircle: West (180°) to East (0°) going through North, sweep = -180°
private const val ARC_START = 180f
private const val ARC_SWEEP = -180f

@Composable
fun SpeedGauge(
    speed: Float,
    maxSpeed: Int = 260,
    unit: String = "km/h",
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
) {
    val fraction = (speed / maxSpeed).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "speed_arc"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 10.dp.toPx()
            val padding = stroke / 2f + 4.dp.toPx()
            val arcSize = Size(this.size.width - padding * 2, this.size.height - padding * 2)
            val arcTopLeft = Offset(padding, padding)

            // Background arc
            drawArc(
                color = RevScopeColors.SurfaceHigh,
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Value arc
            if (animatedFraction > 0f) {
                drawArc(
                    color = RevScopeColors.Accent,
                    startAngle = ARC_START,
                    sweepAngle = ARC_SWEEP * animatedFraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = speed.toInt().toString(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = RevScopeColors.TextPrimary,
            )
            Text(
                text = unit,
                fontSize = 12.sp,
                color = RevScopeColors.TextMuted,
            )
        }
    }
}
