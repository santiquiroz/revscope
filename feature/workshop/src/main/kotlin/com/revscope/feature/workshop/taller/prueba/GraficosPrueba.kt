package com.revscope.feature.workshop.taller.prueba

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable
import com.revscope.core.obd.taller.sesion.Veredicto

/**
 * Cuenta regresiva en anillo con los segundos en el centro. TalkBack no lee cada décima: el nodo es una región
 * viva cuyo texto ([anuncio]) solo cambia al cambiar de paso o de fase y en los últimos 3 segundos.
 */
@Composable
internal fun CuentaRegresivaAnillo(
    segundos: Int,
    fraccion: Float?,
    anuncio: String,
    modifier: Modifier = Modifier,
    lado: Dp = 112.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(lado).clearAndSetSemantics {
            contentDescription = anuncio
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Canvas(Modifier.size(lado)) {
            val grosor = 8.dp.toPx()
            val arco = Size(size.width - grosor, size.height - grosor)
            val esquina = Offset(grosor / 2, grosor / 2)
            drawArc(RevScopeColors.SurfaceHigh, 0f, 360f, false, esquina, arco, style = Stroke(grosor))
            fraccion?.let {
                drawArc(RevScopeColors.Accent, -90f, 360f * it, false, esquina, arco, style = Stroke(grosor, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextoAjustable(
                texto = "$segundos",
                estilo = RevScopeType.numeros,
                color = if (fraccion == null) RevScopeColors.TextSecondary else RevScopeColors.TextPrimary,
                maximo = 40.sp,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Text("s", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
    }
}

/**
 * «Pesa» de una posición: la franja es la banda de referencia y la barra va del mínimo al máximo medidos,
 * con la media marcada. La escala es de 0 a la referencia (5 V típico) para que se vea cuán lejos queda.
 */
@Composable
internal fun PesaReferencia(pesa: PesaUi, modifier: Modifier = Modifier) {
    val color = when (pesa.nivel) {
        Veredicto.OK -> RevScopeColors.Success
        Veredicto.FALLA -> RevScopeColors.Danger
        else -> RevScopeColors.Accent
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .clearAndSetSemantics { contentDescription = pesa.descripcion },
    ) {
        val escala = pesa.escalaV.coerceAtLeast(0.1)
        fun x(v: Double) = (v / escala).toFloat().coerceIn(0f, 1f) * size.width
        val centro = size.height / 2
        drawLine(RevScopeColors.SurfaceHigh, Offset(0f, centro), Offset(size.width, centro), 4.dp.toPx(), StrokeCap.Round)
        pesa.banda?.let { b ->
            drawRect(RevScopeColors.Success.copy(alpha = 0.22f), Offset(x(b.desdeV), 0f), Size(x(b.hastaV) - x(b.desdeV), size.height))
        }
        val desde = x(pesa.minV)
        val hasta = maxOf(x(pesa.maxV), desde + 1f)
        drawLine(color, Offset(desde, centro), Offset(hasta, centro), 6.dp.toPx(), StrokeCap.Round)
        drawCircle(color, 7.dp.toPx(), Offset(x(pesa.mediaV), centro))
        drawCircle(RevScopeColors.Background, 3.dp.toPx(), Offset(x(pesa.mediaV), centro))
    }
}
