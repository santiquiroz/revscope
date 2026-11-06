package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel

private val TextMutedColor = Color(0xFF6B7089)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val SurfaceColor = Color(0xFF12121A)
private val DangerColor = Color(0xFFFF4D4D)

/** Permisos del MCP más allá de la lectura: apagados por defecto y fuera del respaldo. */
@Composable
internal fun McpPermisosToggles(viajeVm: ViajeObdSettingsViewModel = hiltViewModel()) {
    val control by viajeVm.mcpControlEnabled.collectAsState()
    val borrado by viajeVm.mcpClearDtcEnabled.collectAsState()
    val escritura by viajeVm.mcpWriteEnabled.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(
            stringResource(R.string.settings_mcp_allow_control),
            control,
            viajeVm::updateMcpControlEnabled,
            subtitle = stringResource(R.string.settings_mcp_control_description),
        )
        if (control) {
            ToggleRow(
                stringResource(R.string.settings_mcp_allow_clear_codes),
                borrado,
                viajeVm::updateMcpClearDtcEnabled,
                subtitle = stringResource(R.string.settings_mcp_clear_codes_description),
            )
            ToggleRow(
                stringResource(R.string.settings_mcp_allow_workshop_writes),
                escritura,
                viajeVm::updateMcpWriteEnabled,
                subtitle = stringResource(R.string.settings_mcp_workshop_writes_description),
            )
            if (escritura) BypassToggle(viajeVm)
        }
        Text(
            stringResource(R.string.settings_mcp_permissions_not_backed_up),
            color = TextMutedColor,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun BypassToggle(viajeVm: ViajeObdSettingsViewModel) {
    val bypass by viajeVm.bypassEscrituras.collectAsState()
    var pidiendoConfirmacion by remember { mutableStateOf(false) }

    ToggleRow(
        stringResource(R.string.settings_mcp_bypass_label),
        bypass,
        { activar -> if (activar) pidiendoConfirmacion = true else viajeVm.updateBypassEscrituras(false) },
        subtitle = stringResource(R.string.settings_mcp_bypass_description),
    )
    if (pidiendoConfirmacion) {
        BypassConfirmDialog(
            onConfirm = {
                pidiendoConfirmacion = false
                viajeVm.updateBypassEscrituras(true)
            },
            onDismiss = { pidiendoConfirmacion = false },
        )
    }
}

@Composable
private fun BypassConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Text(
                stringResource(R.string.settings_mcp_bypass_confirm_title),
                color = TextPrimaryColor,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                stringResource(R.string.settings_mcp_bypass_warning),
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_mcp_enable_bypass), color = DangerColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_common_cancel), color = TextPrimaryColor)
            }
        },
    )
}
