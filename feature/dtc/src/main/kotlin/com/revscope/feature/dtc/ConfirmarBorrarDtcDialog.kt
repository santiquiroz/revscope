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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.designsystem.ConfirmarDestructivoDialog
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.rememberUnitFormatter
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.feature.dtc.R

@Composable
fun ConfirmarBorrarDtcDialog(
    confirmacion: ConfirmacionBorradoDtc,
    onConfirmar: (declaraDetenido: Boolean) -> Unit,
    onCancelar: () -> Unit,
) {
    var declaraDetenido by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resolver = remember(context) { AndroidStringResolver(context) }
    ConfirmarDestructivoDialog(
        titulo = stringResource(R.string.dtc_clear_confirmation_title),
        mensaje = stringResource(R.string.dtc_clear_confirmation_message, confirmacion.codigos.joinToString()),
        avisos = avisosBorradoDtc(confirmacion.freezeFrameEnSesion, resolver),
        textoConfirmar = stringResource(R.string.dtc_confirm_clear),
        confirmarHabilitado = permiteBorrarDesdeUi(confirmacion.rechazo, declaraDetenido),
        onConfirmar = { onConfirmar(declaraDetenido) },
        onCancelar = onCancelar,
        contenidoExtra = {
            CondicionVehiculoDetenido(confirmacion.rechazo, declaraDetenido, resolver) { declaraDetenido = it }
        },
    )
}

@Composable
private fun CondicionVehiculoDetenido(
    rechazo: RechazoBorradoDtc?,
    declaraDetenido: Boolean,
    resolver: AndroidStringResolver,
    onDeclarar: (Boolean) -> Unit,
) {
    rechazo ?: return
    val color = if (rechazo is RechazoBorradoDtc.EnMovimiento) RevScopeColors.Danger else RevScopeColors.TextPrimary
    Text(textoRechazoUi(rechazo, resolver, rememberUnitFormatter()), color = color, style = RevScopeType.body)
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
            stringResource(R.string.dtc_confirm_vehicle_stopped),
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.body,
            modifier = Modifier.weight(1f),
        )
    }
}
