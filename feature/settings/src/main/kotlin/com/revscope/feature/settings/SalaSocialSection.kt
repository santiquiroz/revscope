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
import androidx.compose.ui.res.stringResource

private val BgColor = Color(0xFF0A0A0F)
private val AccentColor = Color(0xFFE8FF00)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun ServidorColaborativoCard(vm: SettingsViewModel) {
    val serverUrl by vm.serverUrl.collectAsState()
    val riderName by vm.riderName.collectAsState()
    val serverToken by vm.serverToken.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_social_server_title))
        Text(
            stringResource(R.string.settings_social_server_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = vm::updateServerUrl,
            label = { Text(stringResource(R.string.settings_social_server_url), fontSize = 12.sp) },
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = riderName,
            onValueChange = vm::updateRiderName,
            label = { Text(stringResource(R.string.settings_social_rider_name), fontSize = 12.sp) },
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = serverToken,
            onValueChange = vm::updateServerToken,
            label = { Text(stringResource(R.string.settings_social_server_token), fontSize = 12.sp) },
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = vm::saveServerSettings,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text(stringResource(R.string.settings_social_save_server), color = BgColor) }
    }
}

@Composable
internal fun CompaneroViajeCard(vm: SettingsViewModel) {
    val zoneBriefEnabled by vm.zoneBriefEnabled.collectAsState()
    val voiceZoneBrief by vm.voiceZoneBrief.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_social_companion_title))
        Text(
            stringResource(R.string.settings_social_companion_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        ToggleRow(
            stringResource(R.string.settings_social_companion_enabled),
            zoneBriefEnabled,
            vm::updateZoneBriefEnabled,
        )
        ToggleRow(
            stringResource(R.string.settings_social_voice_brief),
            voiceZoneBrief,
            vm::updateVoiceZoneBrief,
            subtitle = stringResource(R.string.settings_social_voice_brief_description),
        )
    }
}
