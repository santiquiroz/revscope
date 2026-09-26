package com.revscope.feature.settings

import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.RevScopeColors
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.revscope.core.obd.telemetry.captura.ResumenCaptura


@Composable
internal fun MuestreoObdCard(vm: SamplingSettingsViewModel = hiltViewModel()) {
    val preset by vm.preset.collectAsState()
    val maxMin by vm.maxCapturaMin.collectAsState()
    val ultima by vm.ultimaCaptura.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Muestreo OBD")
        Text(
            "Cada cuánto se leen los sensores en el uso normal. Para ver uno o pocos sensores lo más " +
                "rápido posible (pedal, mariposa) usa Taller → Sensores → Captura rápida.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        SamplingPreset.entries.forEach { opcion ->
            PresetRow(opcion, selected = opcion == preset) { vm.updatePreset(opcion) }
        }
        Text("Captura rápida: duración máxima", color = RevScopeColors.TextPrimary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SamplingSettingsViewModel.OPCIONES_MAX_CAPTURA_MIN.forEach { minutos ->
                FilterChip(
                    selected = minutos == maxMin,
                    onClick = { vm.updateMaxCapturaMin(minutos) },
                    label = { Text("$minutos min", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RevScopeColors.Accent,
                        selectedLabelColor = Color(0xFF0A0A0F),
                        labelColor = RevScopeColors.TextSecondary,
                    ),
                )
            }
        }
        Text(
            ultima?.let(::resumenTasa) ?: "Última tasa medida: todavía no hay capturas en esta sesión.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
    }
}

private fun resumenTasa(r: ResumenCaptura): String {
    val porPid = r.porPid.joinToString(" · ") { "${it.pid} ${"%.1f".format(it.hz)} Hz" }
    val latencia = r.latenciaP50Ms?.let { " · latencia p50 ${"%.0f".format(it)} ms" }.orEmpty()
    return "Última tasa medida: $porPid$latencia"
}

@Composable
private fun PresetRow(preset: SamplingPreset, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = RevScopeColors.Accent, unselectedColor = RevScopeColors.TextSecondary),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(preset.etiqueta, color = RevScopeColors.TextPrimary, fontSize = 13.sp)
            notaDe(preset)?.let { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
        }
    }
}

private fun notaDe(preset: SamplingPreset): String? = when (preset) {
    SamplingPreset.ESTANDAR_2S -> "Por defecto: RPM y velocidad cada 100 ms, temperaturas y trims cada 2 s"
    SamplingPreset.CUARTO_SEGUNDO -> "Más batería y calor"
    SamplingPreset.MAXIMO -> "El adaptador fija la tasa real: más batería y calor"
    else -> null
}
