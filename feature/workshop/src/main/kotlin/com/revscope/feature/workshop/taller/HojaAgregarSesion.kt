package com.revscope.feature.workshop.taller

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.designsystem.ConfirmarDestructivoDialog
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.feature.workshop.R

data class OpcionAgregar(
    val icono: ImageVector,
    val titulo: String,
    val descripcion: String,
    val requiereAdaptador: Boolean,
    val onElegir: () -> Unit,
)

internal object OpcionesAgregar {
    @Composable
    fun de(a: AccionesSesion): List<OpcionAgregar> = listOfNotNull(
        OpcionAgregar(
            Icons.Filled.BugReport,
            stringResource(R.string.workshop_session_add_read_codes),
            stringResource(R.string.workshop_session_add_read_codes_description),
            true,
            a.onLeerCodigos,
        ),
        a.onPruebaGuiada?.let {
            OpcionAgregar(
                Icons.Filled.Science,
                stringResource(R.string.workshop_session_add_guided_test),
                stringResource(R.string.workshop_session_add_guided_test_description),
                true,
                it,
            )
        },
        a.onMultimetro?.let {
            OpcionAgregar(
                Icons.Filled.ElectricalServices,
                stringResource(R.string.workshop_session_add_multimeter),
                stringResource(R.string.workshop_session_add_multimeter_description),
                false,
                it,
            )
        },
        OpcionAgregar(
            Icons.AutoMirrored.Filled.ShowChart,
            stringResource(R.string.workshop_session_add_quick_capture),
            stringResource(R.string.workshop_session_add_quick_capture_description),
            true,
            a.onCaptura,
        ),
        OpcionAgregar(
            Icons.Filled.MonitorHeart,
            stringResource(R.string.workshop_session_add_health_check),
            stringResource(R.string.workshop_session_add_health_check_description),
            true,
            a.onChequeo,
        ),
        OpcionAgregar(
            Icons.Filled.Sensors,
            stringResource(R.string.workshop_session_add_sensor_snapshot),
            stringResource(R.string.workshop_session_add_sensor_snapshot_description),
            true,
            a.onInstantanea,
        ),
        OpcionAgregar(
            Icons.AutoMirrored.Filled.Notes,
            stringResource(R.string.workshop_session_add_note),
            stringResource(R.string.workshop_session_add_note_description),
            false,
            a.onPedirNota,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaAgregar(conectado: Boolean, acciones: AccionesSesion) {
    ModalBottomSheet(
        onDismissRequest = { acciones.onHojaAgregar(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RevScopeColors.SurfaceHigh,
    ) {
        HojaAgregarContent(OpcionesAgregar.de(acciones), conectado)
    }
}

@Composable
fun HojaAgregarContent(opciones: List<OpcionAgregar>, conectado: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp),
    ) {
        Text(
            stringResource(R.string.workshop_session_add_title),
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.title,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        opciones.forEach { FilaOpcion(it, conectado) }
    }
}

@Composable
private fun FilaOpcion(opcion: OpcionAgregar, conectado: Boolean) {
    val habilitada = conectado || !opcion.requiereAdaptador
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = habilitada, onClick = opcion.onElegir)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Icon(
            opcion.icono,
            contentDescription = null,
            tint = if (habilitada) RevScopeColors.TextPrimary else RevScopeColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(opcion.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            if (habilitada) {
                Text(opcion.descripcion, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            } else {
                RequiereAdaptador()
            }
        }
    }
}

@Composable
private fun RequiereAdaptador() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.LinkOff,
            contentDescription = null,
            tint = RevScopeColors.Warning,
            modifier = Modifier.size(with(LocalDensity.current) { 14.sp.toDp() }),
        )
        Text(stringResource(R.string.workshop_session_requires_adapter), color = RevScopeColors.Warning, style = RevScopeType.bodySmall)
    }
}

@Composable
internal fun DialogosSesion(estado: SesionTallerEstado, acciones: AccionesSesion) {
    when (estado.ui.dialogo) {
        DialogoSesion.CERRAR -> DialogoCerrar(acciones)
        DialogoSesion.ELIMINAR -> DialogoEliminar(estado, acciones)
        DialogoSesion.NOTA -> DialogoNota(estado.ui.nota, acciones)
        null -> Unit
    }
}

@Composable
private fun DialogoCerrar(acciones: AccionesSesion) {
    AlertDialog(
        onDismissRequest = acciones.onCancelarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text(stringResource(R.string.workshop_session_close_title), color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            Text(
                stringResource(R.string.workshop_session_close_message),
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.body,
            )
        },
        confirmButton = { BotonDialogo(stringResource(R.string.workshop_session_close_confirm), acciones.onConfirmarCierre, principal = true) },
        dismissButton = { BotonDialogo(stringResource(R.string.workshop_action_cancel), acciones.onCancelarDialogo) },
    )
}

@Composable
private fun DialogoEliminar(estado: SesionTallerEstado, acciones: AccionesSesion) {
    val context = LocalContext.current
    val resolver = remember(context) { AndroidStringResolver(context) }
    ConfirmarDestructivoDialog(
        titulo = stringResource(R.string.workshop_session_delete_title),
        textoConfirmar = stringResource(R.string.workshop_session_delete_confirm),
        onConfirmar = acciones.onConfirmarEliminacion,
        onCancelar = acciones.onCancelarDialogo,
        mensaje = stringResource(R.string.workshop_session_delete_message, estado.titulo),
        avisos = AvisosEliminacion.de(estado.loQueSeBorra, resolver),
    )
}

internal object AvisosEliminacion {
    fun de(borra: LoQueSeBorra, resolver: com.revscope.core.common.i18n.StringResolver): List<String> = listOfNotNull(
        resolver.get(
            R.string.workshop_session_delete_events_message,
            resolver.plural(R.plurals.workshop_session_delete_event_count, borra.eventos, borra.eventos),
        ),
        borra.adjuntos.takeIf { it > 0 }?.let {
            resolver.plural(R.plurals.workshop_session_delete_capture_files, it, it)
        },
        resolver.get(R.string.workshop_session_delete_preserved_data),
    )
}

@Composable
private fun DialogoNota(nota: String, acciones: AccionesSesion) {
    AlertDialog(
        onDismissRequest = acciones.onCancelarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text(stringResource(R.string.workshop_session_note_title), color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            OutlinedTextField(
                value = nota,
                onValueChange = acciones.onCambiarNota,
                label = { Text(stringResource(R.string.workshop_session_note_label), style = RevScopeType.body) },
                supportingText = { Text(stringResource(R.string.workshop_session_note_hint), style = RevScopeType.bodySmall) },
                minLines = 3,
                textStyle = RevScopeType.body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RevScopeColors.TextPrimary,
                    unfocusedTextColor = RevScopeColors.TextPrimary,
                    focusedBorderColor = RevScopeColors.Accent,
                    unfocusedBorderColor = RevScopeColors.TextMuted,
                    focusedLabelColor = RevScopeColors.Accent,
                    unfocusedLabelColor = RevScopeColors.TextSecondary,
                    cursorColor = RevScopeColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            BotonDialogo(
                stringResource(R.string.workshop_session_note_save),
                acciones.onGuardarNota,
                principal = true,
                habilitado = nota.isNotBlank(),
            )
        },
        dismissButton = { BotonDialogo(stringResource(R.string.workshop_action_cancel), acciones.onCancelarDialogo) },
    )
}

@Composable
private fun BotonDialogo(texto: String, onClick: () -> Unit, principal: Boolean = false, habilitado: Boolean = true) {
    TextButton(onClick = onClick, enabled = habilitado, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(
            texto,
            color = when {
                !habilitado -> RevScopeColors.TextSecondary
                principal -> RevScopeColors.Accent
                else -> RevScopeColors.TextPrimary
            },
            style = RevScopeType.label,
        )
    }
}
