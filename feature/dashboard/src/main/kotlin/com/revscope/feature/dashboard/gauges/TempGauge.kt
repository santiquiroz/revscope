package com.revscope.feature.dashboard.gauges

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable
import com.revscope.core.designsystem.conCifrasTabulares

private const val TEMP_MIN = -40f
private const val TEMP_MAX = 130f

@Composable
fun TempGauge(
    tempCelsius: Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 120.dp,
    barWidth: Dp = 16.dp,
) {
    val fraction = ((tempCelsius - TEMP_MIN) / (TEMP_MAX - TEMP_MIN)).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "temp_fill"
    )
    val fillColor = when {
        tempCelsius > 105f -> RevScopeColors.Danger
        tempCelsius >= 60f -> RevScopeColors.Success
        else               -> RevScopeColors.TextSecondary
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextoAjustable(
            texto = "${tempCelsius.toInt()} °C",
            estilo = RevScopeType.label.conCifrasTabulares(),
            color = fillColor,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.width(barWidth).height(barHeight)) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cornerRadius = CornerRadius(4.dp.toPx())

                // Background track
                drawRoundRect(
                    color = RevScopeColors.SurfaceHigh,
                    cornerRadius = cornerRadius,
                )

                // Fill from bottom up
                val fillHeight = this.size.height * animatedFraction
                clipRect(
                    left = 0f,
                    top = this.size.height - fillHeight,
                    right = this.size.width,
                    bottom = this.size.height,
                ) {
                    drawRoundRect(
                        color = fillColor,
                        cornerRadius = cornerRadius,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextoAjustable(
            texto = "TEMP",
            estilo = RevScopeType.bodySmall,
            color = RevScopeColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
