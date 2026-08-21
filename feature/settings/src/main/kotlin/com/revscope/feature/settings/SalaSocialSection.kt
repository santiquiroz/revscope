package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFF0A0A0F)
private val AccentColor = Color(0xFFE8FF00)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun ServidorColaborativoCard(vm: SettingsViewModel) {
    val serverUrl by vm.serverUrl.collectAsState()
    val riderName by vm.riderName.collectAsState()
    val serverToken by vm.serverToken.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Servidor colaborativo")
        Text(
            "Opcional. Conecta la app a un servidor RevScope para compartir huecos, hacer rodadas " +
                "en grupo y fantasmas. La app funciona igual sin esto. Puedes usar el público, el de " +
                "tu parche, o montar el tuyo (github.com/santiquiroz/revscope-server). Déjalo vacío para no usar ninguno.",
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = vm::updateServerUrl,
            label = { Text("URL del servidor (ej. https://mi-server:8080)", fontSize = 12.sp) },
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = riderName,
            onValueChange = vm::updateRiderName,
            label = { Text("Tu apodo (rodadas y fantasmas)", fontSize = 12.sp) },
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = serverToken,
            onValueChange = vm::updateServerToken,
            label = { Text("Token (solo si el server lo pide — déjalo vacío si no)", fontSize = 12.sp) },
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = vm::saveServerSettings,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text("Guardar servidor", color = BgColor) }
    }
}

@Composable
internal fun CompaneroViajeCard(vm: SettingsViewModel) {
    val zoneBriefEnabled by vm.zoneBriefEnabled.collectAsState()
    val voiceZoneBrief by vm.voiceZoneBrief.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Compañero de viaje")
        Text(
            "Al llegar a un lugar nuevo — en el país o en el extranjero — te da un brief de " +
                "conducción: precio del combustible, peajes, restricciones y, si estás fuera, qué " +
                "necesitas para manejar allí. Pregunta PRIMERO al servidor colaborativo (gratis); si " +
                "nadie ha reportado esa zona y tienes IA con búsqueda web, la genera y la comparte de " +
                "vuelta para el próximo viajero. Apagado por defecto (el respaldo con IA cuesta).",
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        ToggleRow("Activar compañero de viaje", zoneBriefEnabled, vm::updateZoneBriefEnabled)
        ToggleRow(
            "Anunciar por voz al llegar",
            voiceZoneBrief,
            vm::updateVoiceZoneBrief,
            subtitle = "Avisa que el brief está listo; el detalle queda en la notificación",
        )
    }
}
