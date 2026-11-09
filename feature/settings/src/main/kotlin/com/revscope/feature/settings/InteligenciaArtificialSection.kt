package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import com.revscope.core.intelligence.provider.AI_PROVIDER_CUSTOM
import com.revscope.core.intelligence.provider.AI_PROVIDER_NODO
import com.revscope.core.intelligence.provider.NODO_BASE_URL_POR_DEFECTO

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InteligenciaArtificialCard(vm: SettingsViewModel, onOpenAiValue: () -> Unit) {
    val aiProvider by vm.aiProvider.collectAsState()
    val aiApiKey by vm.aiApiKey.collectAsState()
    val aiModel by vm.aiModel.collectAsState()
    val aiCustomBaseUrl by vm.aiCustomBaseUrl.collectAsState()
    val aiTesting by vm.aiTesting.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_ai_section_title))
        Text(
            stringResource(R.string.settings_ai_provider_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        if (aiApiKey.isBlank()) {
            TextButton(onClick = onOpenAiValue) {
                Text(stringResource(R.string.settings_ai_show_benefits), color = AccentColor)
            }
        }
        AiProviderDropdown(selected = aiProvider, onSelected = vm::updateAiProvider)
        OutlinedTextField(
            value = aiApiKey,
            onValueChange = vm::updateAiApiKey,
            label = {
                Text(
                    stringResource(R.string.settings_ai_api_key_label, aiProviderLabel(aiProvider)),
                    fontSize = 12.sp,
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = aiModel,
            onValueChange = vm::updateAiModel,
            label = {
                Text(
                    stringResource(R.string.settings_ai_model_label, aiModelHint(aiProvider)),
                    fontSize = 12.sp,
                )
            },
            singleLine = true,
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (aiProvider == AI_PROVIDER_CUSTOM || aiProvider == AI_PROVIDER_NODO) {
            OutlinedTextField(
                value = aiCustomBaseUrl,
                onValueChange = vm::updateAiCustomBaseUrl,
                label = {
                    Text(
                        if (aiProvider == AI_PROVIDER_NODO) {
                            stringResource(R.string.settings_ai_base_url_nodo, NODO_BASE_URL_POR_DEFECTO)
                        } else {
                            stringResource(R.string.settings_ai_base_url_custom)
                        },
                        fontSize = 12.sp,
                    )
                },
                singleLine = true,
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = vm::saveAiSettings,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text(stringResource(R.string.settings_ai_save), color = BgColor) }
            Button(
                onClick = vm::testAiConnection,
                enabled = !aiTesting,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) {
                Text(
                    stringResource(
                        if (aiTesting) R.string.settings_ai_testing else R.string.settings_ai_test_connection,
                    ),
                    color = TextPrimaryColor,
                )
            }
        }
    }
}

private val AiProviderOptions = listOf(
    "anthropic" to R.string.settings_ai_provider_anthropic,
    "openai" to R.string.settings_ai_provider_openai,
    "gemini" to R.string.settings_ai_provider_gemini,
    AI_PROVIDER_NODO to R.string.settings_ai_provider_nodo,
    AI_PROVIDER_CUSTOM to R.string.settings_ai_provider_custom,
)

private val AiModelHints = mapOf(
    "anthropic" to "claude-haiku-4-5-20251001",
    "openai" to "gpt-5-mini",
    "gemini" to "gemini-flash-latest",
)

@Composable
private fun aiProviderLabel(provider: String): String {
    val labelRes = AiProviderOptions.firstOrNull { it.first == provider }?.second
    return if (labelRes == null) provider else stringResource(labelRes)
}

@Composable
private fun aiModelHint(provider: String): String = when (provider) {
    AI_PROVIDER_NODO -> stringResource(R.string.settings_ai_model_hint_nodo)
    AI_PROVIDER_CUSTOM -> stringResource(R.string.settings_ai_model_hint_server)
    else -> AiModelHints[provider].orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProviderDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = aiProviderLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_ai_provider_label), fontSize = 12.sp) },
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
            AiProviderOptions.forEach { (id, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
