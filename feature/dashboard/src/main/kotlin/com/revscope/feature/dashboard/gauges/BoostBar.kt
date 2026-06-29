package com.revscope.feature.dashboard.gauges

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.feature.dashboard.ui.RevScopeColors
import kotlin.math.abs

private const val BOOST_MIN = -30f
private const val BOOST_MAX = 30f

@Composable
fun BoostBar(
    boostKpa: Float,
    optimalBoostKpa: Float = 10f,
    modifier: Modifier = Modifier,
    barHeight: Dp = 20.dp,
) {
    val clampedBoost = boostKpa.coerceIn(BOOST_MIN, BOOST_MAX)
    val animatedBoost by animateFloatAsState(
        targetValue = clampedBoost,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "boost_bar"
    )

    val label = when {
        boostKpa > 0f -> "+%.1f kPa".format(boostKpa)
        boostKpa < 0f -> "%.1f kPa".format(boostKpa)
        else -> "0 kPa"
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "BOOST",
            fontSize = 10.sp,
            color = RevScopeColors.TextMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(barHeight)) {
            val trackHeight = this.size.height
            val trackWidth = this.size.width
            val cornerRadius = CornerRadius(4.dp.toPx())
            val centerX = trackWidth / 2f

            // Background track
            drawRoundRect(
                color = RevScopeColors.SurfaceHigh,
                cornerRadius = cornerRadius,
            )

            // Value fill from center outward
            val fillFraction = abs(animatedBoost) / BOOST_MAX
            val fillWidth = (trackWidth / 2f) * fillFraction

            if (animatedBoost >= 0f) {
                // Positive boost — fills right of center
                clipRect(left = centerX, right = centerX + fillWidth) {
                    drawRoundRect(
                        color = RevScopeColors.Warning,
                        cornerRadius = cornerRadius,
                    )
                }
            } else {
                // Vacuum — fills left of center
                clipRect(left = centerX - fillWidth, right = centerX) {
                    drawRoundRect(
                        color = RevScopeColors.TextMuted,
                        cornerRadius = cornerRadius,
                    )
                }
            }

            // Center line (atmospheric)
            drawLine(
                color = RevScopeColors.TextMuted,
                start = Offset(centerX, 0f),
                end = Offset(centerX, trackHeight),
                strokeWidth = 2.dp.toPx(),
            )

            // Optimal boost marker (dashed vertical line)
            val optimalX = centerX + (optimalBoostKpa / BOOST_MAX) * (trackWidth / 2f)
            drawLine(
                color = RevScopeColors.AccentDim,
                start = Offset(optimalX, 0f),
                end = Offset(optimalX, trackHeight),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (boostKpa > 0f) RevScopeColors.Warning else RevScopeColors.TextMuted,
        )
    }
}
