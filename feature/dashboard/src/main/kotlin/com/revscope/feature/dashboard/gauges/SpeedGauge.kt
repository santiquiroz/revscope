package com.revscope.feature.dashboard.gauges

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable

// Upper semicircle: West (180°) to East (0°) going through North, sweep = -180°
private const val ARC_START = 180f
private const val ARC_SWEEP = -180f
private const val FRACCION_TEXTO_ARCO = 0.7f
private const val FRACCION_ALTO_NUMERO = 0.36f

@Composable
fun SpeedGauge(
    speed: Float?,
    maxSpeed: Int = 260,
    unit: String = "km/h",
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
) {
    val fraction = ((speed ?: 0f) / maxSpeed).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "speed_arc"
    )

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val lado = ladoAcotado(size, maxWidth)
        Canvas(modifier = Modifier.size(lado)) {
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

        Column(
            modifier = Modifier.width(lado * FRACCION_TEXTO_ARCO),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextoAjustable(
                texto = speed?.toInt()?.toString() ?: "—",
                estilo = RevScopeType.numeros.copy(fontSize = 48.sp),
                color = RevScopeColors.TextPrimary,
                modifier = Modifier.fillMaxWidth().height(lado * FRACCION_ALTO_NUMERO),
            )
            TextoAjustable(
                texto = unit,
                estilo = RevScopeType.bodySmall,
                color = RevScopeColors.TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
