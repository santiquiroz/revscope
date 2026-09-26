package com.revscope.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.telemetry.SamplingPreset

private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun MuestreoObdCard(vm: SamplingSettingsViewModel = hiltViewModel()) {
    val preset by vm.preset.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Muestreo OBD")
        Text(
            "Cada cuánto se leen los sensores en el uso normal. Para ver uno o pocos sensores lo más " +
                "rápido posible (pedal, mariposa) usa Taller → Sensores → Captura rápida.",
            color = TextMutedColor,
            fontSize = 11.sp,
        )
        SamplingPreset.entries.forEach { opcion ->
            PresetRow(opcion, selected = opcion == preset) { vm.updatePreset(opcion) }
        }
    }
}

@Composable
private fun PresetRow(preset: SamplingPreset, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = AccentColor, unselectedColor = TextMutedColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(preset.etiqueta, color = TextPrimaryColor, fontSize = 13.sp)
            notaDe(preset)?.let { Text(it, color = TextMutedColor, fontSize = 11.sp) }
        }
    }
}

private fun notaDe(preset: SamplingPreset): String? = when (preset) {
    SamplingPreset.ESTANDAR_2S -> "Por defecto: RPM y velocidad cada 100 ms, temperaturas y trims cada 2 s"
    SamplingPreset.CUARTO_SEGUNDO -> "Más batería y calor"
    SamplingPreset.MAXIMO -> "El adaptador fija la tasa real: más batería y calor"
    else -> null
}
