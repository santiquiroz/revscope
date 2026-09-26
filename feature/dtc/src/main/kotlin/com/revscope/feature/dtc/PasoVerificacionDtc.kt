package com.revscope.feature.dtc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.dtc.AccionGuia
import com.revscope.core.obd.taller.dtc.VerificacionDtc

data class PasoVerificacionUi(
    val numero: Int,
    val verificacion: VerificacionDtc,
    val marcado: Boolean,
    val borradoHabilitado: Boolean,
)

fun textoAccionGuia(accion: AccionGuia): String = when (accion) {
    is AccionGuia.Prueba -> "Iniciar prueba: ${accion.tipo.titulo}"
    is AccionGuia.Multimetro -> "Medir con multímetro"
    AccionGuia.BorrarCodigos -> "Borrar códigos"
}

// Una acción sin destino todavía (p. ej. una prueba que la app aún no trae) no muestra botón.
fun AccionesDtc.ejecutor(accion: AccionGuia): (() -> Unit)? = when (accion) {
    is AccionGuia.Prueba -> onPrueba?.let { abrir -> { abrir(accion.tipo) } }
    is AccionGuia.Multimetro -> onMultimetro?.let { abrir -> { abrir(accion.sensor) } }
    AccionGuia.BorrarCodigos -> onBorrar
}

@Composable
internal fun PasoVerificacion(paso: PasoVerificacionUi, onMarcar: (Boolean) -> Unit, acciones: AccionesDtc) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CasillaPaso(paso, onMarcar)
        paso.verificacion.accion?.let { accion ->
            acciones.ejecutor(accion)?.let { ejecutar ->
                BotonAccionPaso(accion, habilitado = accion != AccionGuia.BorrarCodigos || paso.borradoHabilitado, ejecutar)
            }
        }
    }
}

@Composable
private fun CasillaPaso(paso: PasoVerificacionUi, onMarcar: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = paso.marcado, role = Role.Checkbox, onValueChange = onMarcar),
    ) {
        Checkbox(
            checked = paso.marcado,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = RevScopeColors.Accent,
                checkmarkColor = RevScopeColors.Background,
                uncheckedColor = RevScopeColors.TextSecondary,
            ),
            modifier = Modifier.padding(12.dp),
        )
        Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp)) {
            Text(
                "${paso.numero}. ${paso.verificacion.paso}",
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.label,
            )
            if (paso.verificacion.detalle.isNotBlank()) {
                Text(paso.verificacion.detalle, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
            }
        }
    }
}

@Composable
private fun BotonAccionPaso(accion: AccionGuia, habilitado: Boolean, onClick: () -> Unit) {
    val (icono, color) = estiloAccion(accion)
    val colorVisible = if (habilitado) color else RevScopeColors.TextMuted
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        border = BorderStroke(1.dp, colorVisible.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 48.dp),
    ) {
        Icon(icono, contentDescription = null, tint = colorVisible, modifier = Modifier.size(18.dp))
        Text(
            textoAccionGuia(accion),
            color = colorVisible,
            style = RevScopeType.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun estiloAccion(accion: AccionGuia): Pair<ImageVector, Color> = when (accion) {
    is AccionGuia.Prueba -> Icons.Filled.Science to RevScopeColors.TextPrimary
    is AccionGuia.Multimetro -> Icons.Filled.ElectricalServices to RevScopeColors.TextPrimary
    AccionGuia.BorrarCodigos -> Icons.Filled.DeleteSweep to RevScopeColors.Danger
}
