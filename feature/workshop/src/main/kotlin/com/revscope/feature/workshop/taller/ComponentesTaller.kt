package com.revscope.feature.workshop.taller

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto

internal fun Veredicto.nivel(): NivelEstado = when (this) {
    Veredicto.OK -> NivelEstado.OK
    Veredicto.ATENCION -> NivelEstado.ATENCION
    Veredicto.FALLA -> NivelEstado.FALLA
    Veredicto.INFO -> NivelEstado.SIN_DATO
}

internal fun Veredicto.texto(): String = when (this) {
    Veredicto.OK -> "OK"
    Veredicto.ATENCION -> "Atención"
    Veredicto.FALLA -> "Falla"
    Veredicto.INFO -> "Registro"
}

internal fun TipoEvento.icono(): ImageVector = when (this) {
    TipoEvento.NOTA -> Icons.AutoMirrored.Filled.Notes
    TipoEvento.SINTOMAS -> Icons.Filled.ReportProblem
    TipoEvento.DTC_LECTURA -> Icons.Filled.BugReport
    TipoEvento.DTC_BORRADO -> Icons.Filled.DeleteSweep
    TipoEvento.CHEQUEO -> Icons.Filled.MonitorHeart
    TipoEvento.PRUEBA_GUIADA -> Icons.Filled.Science
    TipoEvento.CAPTURA -> Icons.AutoMirrored.Filled.ShowChart
    TipoEvento.MEDICION_MULTIMETRO -> Icons.Filled.ElectricalServices
    TipoEvento.INSTANTANEA_SENSORES -> Icons.Filled.Sensors
}

@Composable
internal fun EncabezadoSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        color = RevScopeColors.TextSecondary,
        style = RevScopeType.label,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
internal fun TarjetaTaller(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = RevScopeColors.Surface, modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp), content = contenido)
    }
}

@Composable
internal fun PastillaCodigo(codigo: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = RevScopeColors.SurfaceHigh,
        modifier = Modifier.border(1.dp, RevScopeColors.Danger.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
    ) {
        Text(
            codigo,
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.label,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun PastillaTexto(texto: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = RevScopeColors.SurfaceHigh) {
        Text(
            texto,
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

// La única CTA lima de cada pantalla del Taller pasa por aquí.
@Composable
internal fun BotonPrincipalTaller(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = habilitado,
        colors = ButtonDefaults.buttonColors(
            containerColor = RevScopeColors.Accent,
            contentColor = RevScopeColors.Background,
            disabledContainerColor = RevScopeColors.SurfaceHigh,
            disabledContentColor = RevScopeColors.TextSecondary,
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(texto, style = RevScopeType.label, textAlign = TextAlign.Center)
    }
}
