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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.feature.dashboard.ui.RevScopeColors
import kotlin.math.cos
import kotlin.math.sin

private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

@Composable
fun RpmGauge(
    rpm: Float,
    maxRpm: Int,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val fraction = (rpm / maxRpm).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rpm_needle"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 12.dp.toPx()
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

            val greenSweep = ARC_SWEEP * 0.60f
            val orangeSweep = ARC_SWEEP * 0.25f
            val redSweep = ARC_SWEEP * 0.15f

            drawArc(
                color = RevScopeColors.Success,
                startAngle = ARC_START,
                sweepAngle = greenSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = RevScopeColors.Warning,
                startAngle = ARC_START + greenSweep,
                sweepAngle = orangeSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = RevScopeColors.Danger,
                startAngle = ARC_START + greenSweep + orangeSweep,
                sweepAngle = redSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            val needleAngleDeg = ARC_START + animatedFraction * ARC_SWEEP
            val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension / 2f - padding)
            val needleLength = radius * 0.7f

            drawLine(
                color = RevScopeColors.TextPrimary,
                start = center,
                end = Offset(
                    x = center.x + cos(needleAngleRad).toFloat() * needleLength,
                    y = center.y + sin(needleAngleRad).toFloat() * needleLength,
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Center dot
            drawCircle(color = RevScopeColors.Accent, radius = 6.dp.toPx(), center = center)

            // Redline tick at 85% (start of danger zone)
            val redlineAngleDeg = ARC_START + 0.85f * ARC_SWEEP
            val redlineAngleRad = Math.toRadians(redlineAngleDeg.toDouble())
            val tickOuter = radius + stroke / 2f
            val tickInner = radius - stroke / 2f
            drawLine(
                color = RevScopeColors.Danger,
                start = Offset(
                    x = center.x + cos(redlineAngleRad).toFloat() * tickInner,
                    y = center.y + sin(redlineAngleRad).toFloat() * tickInner,
                ),
                end = Offset(
                    x = center.x + cos(redlineAngleRad).toFloat() * tickOuter,
                    y = center.y + sin(redlineAngleRad).toFloat() * tickOuter,
                ),
                strokeWidth = 3.dp.toPx(),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = rpm.toInt().toString(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = RevScopeColors.TextPrimary,
            )
            Text(
                text = "RPM",
                fontSize = 12.sp,
                color = RevScopeColors.TextMuted,
            )
        }
    }
}
