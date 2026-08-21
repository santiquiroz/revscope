package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.sound.SoundPack

private val BgColor = Color(0xFF0A0A0F)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun AlertasAudioCard(vm: SettingsViewModel) {
    val aiProvider by vm.aiProvider.collectAsState()
    val alertsEnabled by vm.alertsEnabled.collectAsState()
    val ttsEnabled by vm.ttsEnabled.collectAsState()
    val voiceTemperature by vm.voiceTemperature.collectAsState()
    val voiceVoltage by vm.voiceVoltage.collectAsState()
    val voiceSpeedCameras by vm.voiceSpeedCameras.collectAsState()
    val voiceAnomalies by vm.voiceAnomalies.collectAsState()
    val voiceMil by vm.voiceMil.collectAsState()
    val voiceRedline by vm.voiceRedline.collectAsState()
    val voiceCustomThresholds by vm.voiceCustomThresholds.collectAsState()
    val voiceSport by vm.voiceSport.collectAsState()
    val voicePicoPlaca by vm.voicePicoPlaca.collectAsState()
    val voiceSunset by vm.voiceSunset.collectAsState()
    val voicePotholes by vm.voicePotholes.collectAsState()
    val voiceRain by vm.voiceRain.collectAsState()
    val voiceFatigue by vm.voiceFatigue.collectAsState()
    val voiceLocalInfo by vm.voiceLocalInfo.collectAsState()
    val aiPicoPlaca by vm.aiPicoPlaca.collectAsState()
    val tempMaxC by vm.tempMaxC.collectAsState()
    val voltageMin by vm.voltageMin.collectAsState()
    val redlineRpm by vm.redlineRpm.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Alertas de audio y vibración")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Alertas activas (suenan por el intercom/parlante)",
                color = TextPrimaryColor,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = alertsEnabled,
                onCheckedChange = vm::updateAlertsEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgColor,
                    checkedTrackColor = AccentColor,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Voz (TTS): alertas y tiempos 0-100 hablados",
                color = TextPrimaryColor,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = ttsEnabled,
                onCheckedChange = vm::updateTtsEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgColor,
                    checkedTrackColor = AccentColor,
                ),
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Alertas de voz por categoría",
            color = TextMutedColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        ToggleRow("Temperatura", voiceTemperature, vm::updateVoiceTemperature)
        ToggleRow("Batería y carga", voiceVoltage, vm::updateVoiceVoltage)
        ToggleRow("Radares de velocidad", voiceSpeedCameras, vm::updateVoiceSpeedCameras)
        ToggleRow("Anomalías inteligentes", voiceAnomalies, vm::updateVoiceAnomalies)
        ToggleRow("Testigo del motor (MIL)", voiceMil, vm::updateVoiceMil)
        ToggleRow("Zona roja", voiceRedline, vm::updateVoiceRedline)
        ToggleRow("Umbrales personalizados", voiceCustomThresholds, vm::updateVoiceCustomThresholds)
        ToggleRow("Tiempos 0-100 y vueltas", voiceSport, vm::updateVoiceSport)
        ToggleRow("Pico y placa al entrar a otra ciudad", voicePicoPlaca, vm::updateVoicePicoPlaca)
        ToggleRow(
            "Aviso de atardecer",
            voiceSunset,
            vm::updateVoiceSunset,
            subtitle = "Un aviso diario ~25 min antes del ocaso: enciende luces y hazte visible",
        )
        ToggleRow(
            "Huecos y resaltos",
            voicePotholes,
            vm::updateVoicePotholes,
            subtitle = "Avisa al acercarte a un hueco que tu propio IMU ya detectó antes",
        )
        ToggleRow(
            "Lluvia e inclinación en mojado",
            voiceRain,
            vm::updateVoiceRain,
            subtitle = "Lluvia inminente en tu zona + guardián de lean con piso mojado",
        )
        ToggleRow(
            "Fatiga e hidratación",
            voiceFatigue,
            vm::updateVoiceFatigue,
            subtitle = "Pausa sugerida cada 2 h; hidratación cuando hace calor",
        )
        ToggleRow(
            "Información local al cambiar de ciudad",
            voiceLocalInfo,
            vm::updateVoiceLocalInfo,
            subtitle = if (aiProviderSupportsWebSearch(aiProvider)) {
                "Usa tu proveedor de IA con búsqueda web (~$0.02 por ciudad)"
            } else {
                "Requiere Claude, OpenAI o Gemini (no disponible con Nodo ni Compatible OpenAI)"
            },
        )
        ToggleRow(
            "Pico y placa por IA en cualquier ciudad",
            aiPicoPlaca,
            vm::updateAiPicoPlaca,
            subtitle = if (aiProviderSupportsWebSearch(aiProvider)) {
                "Investiga la restricción de la ciudad donde estés (pico y placa, hoy no circula, rodízio…) y la guarda hasta que venza"
            } else {
                "Configura primero un proveedor de IA con búsqueda web — recomendamos Gemini por su capa gratuita"
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = tempMaxC,
                onValueChange = vm::updateTempMaxC,
                label = { Text("Temp máx °C", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = voltageMin,
                onValueChange = vm::updateVoltageMin,
                label = { Text("Volt mín", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = settingsFieldColors(),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = redlineRpm,
                onValueChange = vm::updateRedlineRpm,
                label = { Text("Zona roja RPM", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "La zona roja también controla el shift light del dashboard.",
            color = TextMutedColor,
            fontSize = 11.sp,
        )
        Button(
            onClick = vm::saveAlertSettings,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text("Guardar alertas", color = BgColor) }
    }
}

@Composable
internal fun SonidoMotorCard(vm: SettingsViewModel) {
    val engineSoundEnabled by vm.engineSoundEnabled.collectAsState()
    val engineSoundPack by vm.engineSoundPack.collectAsState()
    val engineSoundVolume by vm.engineSoundVolume.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Sonido de motor")
        Text(
            "Sintetiza el sonido de un motor deportivo (o uno gracioso) siguiendo las RPM " +
                "reales del OBD2 — suena por los parlantes o el intercomunicador Bluetooth. " +
                "Estilo SoundRacer, sin comprar el adaptador.",
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        ToggleRow(
            "Sonido de motor al conducir",
            engineSoundEnabled,
            vm::updateEngineSoundEnabled,
            subtitle = "Se activa solo con telemetría OBD conectada",
        )
        if (engineSoundEnabled) {
            EngineSoundPackDropdown(selected = engineSoundPack, onSelected = vm::updateEngineSoundPack)
            OutlinedTextField(
                value = engineSoundVolume,
                onValueChange = vm::updateEngineSoundVolume,
                label = { Text("Volumen (0-100)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::saveEngineSoundVolume,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Guardar volumen", color = BgColor) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineSoundPackDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = SoundPack.fromId(selected).displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Pack de sonido", fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            colors = settingsFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SoundPack.entries.forEach { pack ->
                DropdownMenuItem(
                    text = { Text(pack.displayName + if (pack.funny) " 😜" else "") },
                    onClick = {
                        onSelected(pack.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun RadaresVelocidadCard(vm: SettingsViewModel) {
    val cameraStatus by vm.cameraStatus.collectAsState()
    val cameraAlertRadius by vm.cameraAlertRadius.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Radares de velocidad")
        Text(
            "Descarga los radares en 50 km a tu alrededor. " +
                "Fuentes: OpenStreetMap + registro oficial ANSV · se actualiza cada semana automáticamente. " +
                "Al conducir, la app avisa por voz al acercarte (funciona offline tras descargar).",
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        cameraStatus?.let { Text(it, color = AccentColor, fontSize = 12.sp) }
        Button(
            onClick = vm::downloadSpeedCameras,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text("Descargar radares de mi zona", color = BgColor) }

        OutlinedTextField(
            value = cameraAlertRadius,
            onValueChange = vm::updateCameraAlertRadius,
            label = { Text("Radio de aviso (m)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Distancia a la que suena el aviso por voz (100-1000 m). Menos metros = menos avisos anticipados.",
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        Button(
            onClick = vm::saveCameraAlertRadius,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text("Guardar radio", color = BgColor) }
    }
}

@Composable
internal fun PicoPlacaPersonalizadoCard(vm: SettingsViewModel) {
    val picoPlacaRulesJson by vm.picoPlacaRulesJson.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Pico y placa personalizado")
        Text(
            "Reglas propias para tu ciudad o rotaciones nuevas — sobreescriben las integradas " +
                "cuando el cityId coincide.",
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = picoPlacaRulesJson,
            onValueChange = vm::updatePicoPlacaRulesJson,
            label = { Text("JSON de reglas de pico y placa", fontSize = 12.sp) },
            minLines = 4,
            maxLines = 10,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Ejemplo (Medellín, rotación por día de semana):\n" +
                "{\"cityId\":\"medellin\",\"displayName\":\"Medellín\"," +
                "\"rotation\":{\"2\":[1,7],\"3\":[0,3]}," +
                "\"startHour\":5,\"endHour\":20," +
                "\"carDigit\":\"LAST\",\"motoDigit\":\"FIRST\"," +
                "\"validFromMs\":1770008400000,\"validUntilMs\":1785560399000}",
            color = TextMutedColor,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Button(
            onClick = vm::savePicoPlacaRules,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text("Validar y aplicar", color = BgColor) }
    }
}
