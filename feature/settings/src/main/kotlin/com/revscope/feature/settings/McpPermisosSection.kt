package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val TextMutedColor = Color(0xFF6B7089)

/** Permisos del MCP más allá de la lectura: apagados por defecto y fuera del respaldo. */
@Composable
internal fun McpPermisosToggles(viajeVm: ViajeObdSettingsViewModel = hiltViewModel()) {
    val control by viajeVm.mcpControlEnabled.collectAsState()
    val borrado by viajeVm.mcpClearDtcEnabled.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(
            "Permitir control desde MCP (viaje)",
            control,
            viajeVm::updateMcpControlEnabled,
            subtitle = "El PC puede finalizar e iniciar viajes con finalizar_viaje / iniciar_viaje. " +
                "Solo en redes de confianza: el token viaja sin cifrar por la WiFi.",
        )
        if (control) {
            ToggleRow(
                "Permitir borrar códigos desde MCP",
                borrado,
                viajeVm::updateMcpClearDtcEnabled,
                subtitle = "borrar_dtc exige el vehículo detenido y reinicia los monitores de readiness: " +
                    "la revisión técnico-mecánica puede rechazar el vehículo hasta completar ciclos de manejo.",
            )
        }
        Text(
            "Estos permisos no viajan en el respaldo: restaurar nunca los activa.",
            color = TextMutedColor,
            fontSize = 11.sp,
        )
    }
}
