package com.revscope.feature.dtc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.ConfirmarDestructivoDialog
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc

@Composable
fun ConfirmarBorrarDtcDialog(
    confirmacion: ConfirmacionBorradoDtc,
    onConfirmar: (declaraDetenido: Boolean) -> Unit,
    onCancelar: () -> Unit,
) {
    var declaraDetenido by remember { mutableStateOf(false) }
    ConfirmarDestructivoDialog(
        titulo = "¿Borrar los códigos de falla?",
        mensaje = "Se enviará el borrado (modo 04) de: ${confirmacion.codigos.joinToString()}. " +
            "Después se relee la ECU para ver si vuelven.",
        avisos = AVISOS_BORRADO_DTC,
        textoConfirmar = "Borrar códigos",
        confirmarHabilitado = permiteBorrarDesdeUi(confirmacion.rechazo, declaraDetenido),
        onConfirmar = { onConfirmar(declaraDetenido) },
        onCancelar = onCancelar,
        contenidoExtra = {
            CondicionVehiculoDetenido(confirmacion.rechazo, declaraDetenido) { declaraDetenido = it }
        },
    )
}

@Composable
private fun CondicionVehiculoDetenido(
    rechazo: RechazoBorradoDtc?,
    declaraDetenido: Boolean,
    onDeclarar: (Boolean) -> Unit,
) {
    rechazo ?: return
    val color = if (rechazo is RechazoBorradoDtc.EnMovimiento) RevScopeColors.Danger else RevScopeColors.TextPrimary
    Text(textoRechazoUi(rechazo), color = color, style = RevScopeType.body)
    if (rechazo == RechazoBorradoDtc.SinVelocidadReciente) {
        DeclaracionDetenido(declaraDetenido, onDeclarar)
    }
}

@Composable
private fun DeclaracionDetenido(marcada: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = marcada, role = Role.Checkbox, onValueChange = onCambio),
    ) {
        Checkbox(
            checked = marcada,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = RevScopeColors.Accent,
                checkmarkColor = RevScopeColors.Background,
                uncheckedColor = RevScopeColors.TextSecondary,
            ),
        )
        Text(
            "Confirmo que el vehículo está detenido",
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.body,
            modifier = Modifier.weight(1f),
        )
    }
}
