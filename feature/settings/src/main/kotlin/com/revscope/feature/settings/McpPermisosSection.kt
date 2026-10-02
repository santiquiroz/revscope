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
            "Permitir control desde MCP (viaje, muestreo, captura)",
            control,
            viajeVm::updateMcpControlEnabled,
            subtitle = "El PC puede finalizar e iniciar viajes, cambiar el muestreo y hacer capturas rápidas " +
                "(finalizar_viaje, iniciar_viaje, set_muestreo, iniciar_captura, detener_captura). " +
                "Solo en redes de confianza: el token viaja sin cifrar por la WiFi.",
        )
        if (control) {
            ToggleRow(
                "Permitir borrar códigos desde MCP",
                borrado,
                viajeVm::updateMcpClearDtcEnabled,
                subtitle = "borrar_dtc exige el vehículo detenido y tu toque en la notificación, y reinicia los " +
                    "monitores de readiness: la revisión técnico-mecánica puede rechazar el vehículo hasta " +
                    "completar ciclos de manejo.",
            )
            ToggleRow(
                "Permitir escrituras de taller desde MCP",
                escritura,
                viajeVm::updateMcpWriteEnabled,
                subtitle = "Borrar códigos por módulo (UDS 14), reiniciar la ECU (UDS 11), pruebas a bordo (modo 08), " +
                    "operaciones del catálogo de fabricante y secuencias crudas. Cada escritura pide tu toque en " +
                    "una notificación; con el vehículo en movimiento nunca se ejecuta y el flasheo está bloqueado.",
            )
            if (escritura) BypassToggle(viajeVm)
        }
        Text(
            "Estos permisos no viajan en el respaldo: restaurar nunca los activa.",
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
        "Saltar confirmaciones (bypass)",
        bypass,
        { activar -> if (activar) pidiendoConfirmacion = true else viajeVm.updateBypassEscrituras(false) },
        subtitle = "Las escrituras del MCP se ejecutan sin preguntarte. Se apaga solo al detener el servidor MCP.",
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
        title = { Text("¿Escrituras sin confirmación?", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Cualquier cliente con el token del MCP podrá borrar códigos, reiniciar módulos y mandar comandos " +
                    "de fabricante a la ECU sin que lo apruebes. Un comando equivocado puede dejar un módulo " +
                    "inservible hasta llevarlo al concesionario. Las guardas de vehículo detenido y el bloqueo " +
                    "de flasheo siguen activos.",
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Activar bypass", color = DangerColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextPrimaryColor) } },
    )
}
