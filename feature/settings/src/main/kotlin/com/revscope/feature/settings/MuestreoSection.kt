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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.obd.telemetry.SamplingPreset
import com.revscope.core.obd.telemetry.captura.ResumenCaptura


@Composable
internal fun MuestreoObdCard(vm: SamplingSettingsViewModel = hiltViewModel()) {
    val preset by vm.preset.collectAsState()
    val maxMin by vm.maxCapturaMin.collectAsState()
    val ultima by vm.ultimaCaptura.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.settings_sampling_section_title))
        Text(
            stringResource(R.string.settings_sampling_description),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        SamplingPreset.entries.forEach { opcion ->
            PresetRow(opcion, selected = opcion == preset) { vm.updatePreset(opcion) }
        }
        Text(
            stringResource(R.string.settings_sampling_capture_duration),
            color = RevScopeColors.TextPrimary,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SamplingSettingsViewModel.OPCIONES_MAX_CAPTURA_MIN.forEach { minutos ->
                FilterChip(
                    selected = minutos == maxMin,
                    onClick = { vm.updateMaxCapturaMin(minutos) },
                    label = { Text(stringResource(R.string.settings_sampling_minutes, minutos), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RevScopeColors.Accent,
                        selectedLabelColor = Color(0xFF0A0A0F),
                        labelColor = RevScopeColors.TextSecondary,
                    ),
                )
            }
        }
        val lastRate = when (val capture = ultima) {
            null -> stringResource(R.string.settings_sampling_no_capture)
            else -> resumenTasa(capture)
        }
        Text(
            lastRate,
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
    }
}

@Composable
private fun resumenTasa(r: ResumenCaptura): String {
    val locale = LocalConfiguration.current.locales[0]
    val porPidItems = mutableListOf<String>()
    r.porPid.forEach {
        porPidItems += stringResource(R.string.settings_sampling_rate_item, it.pid, LocaleNumbers.decimal(it.hz, 1, locale))
    }
    val porPid = porPidItems.joinToString(" · ")
    val latencyMs = r.latenciaP50Ms
    val latencia = if (latencyMs == null) "" else {
        stringResource(R.string.settings_sampling_latency, LocaleNumbers.decimal(latencyMs, 0, locale))
    }
    return stringResource(R.string.settings_sampling_last_rate, porPid, latencia)
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
            Text(stringResource(preset.etiquetaRes), color = RevScopeColors.TextPrimary, fontSize = 13.sp)
            notaDe(preset)?.let { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
        }
    }
}

@Composable
private fun notaDe(preset: SamplingPreset): String? = when (preset) {
    SamplingPreset.ESTANDAR_2S -> stringResource(R.string.settings_sampling_note_standard)
    SamplingPreset.CUARTO_SEGUNDO -> stringResource(R.string.settings_sampling_note_battery)
    SamplingPreset.MAXIMO -> stringResource(R.string.settings_sampling_note_maximum)
    else -> null
}
