package com.revscope.app.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.intelligence.provider.AI_PROVIDER_GEMINI
import com.revscope.feature.settings.SettingsViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)
private val SuccessColor = Color(0xFF4CD964)
private val ErrorColor = Color(0xFFFF4D4D)

private const val GEMINI_API_KEY_URL = "https://aistudio.google.com/apikey"

private val AiValueBullets = listOf(
    "Chat mecánico especializado en TU vehículo",
    "Debrief IA al final de cada viaje",
    "Pico y placa por IA en cualquier ciudad",
    "Explicación de códigos de falla (DTC)",
)

/** compact = true lo embebe el wizard sin botón "Después" (usa el Saltar de WizardBar). */
@Composable
fun AiValueContent(
    onDone: () -> Unit,
    compact: Boolean = false,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val aiApiKey by vm.aiApiKey.collectAsState()
    val aiTesting by vm.aiTesting.collectAsState()
    val testResult by vm.lastSaveResult.collectAsState()

    LaunchedEffect(Unit) { vm.updateAiProvider(AI_PROVIDER_GEMINI) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AiValueBullets.forEach { bullet -> AiValueBulletRow(bullet) }
        }

        OutlinedTextField(
            value = aiApiKey,
            onValueChange = vm::updateAiApiKey,
            label = { Text("API key de Gemini") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = aiValueTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_API_KEY_URL))) }) {
            Text("Obtener key gratis", color = AccentColor)
        }

        testResult?.let { result ->
            Text(
                result.message,
                color = if (result.success) SuccessColor else ErrorColor,
                fontSize = 13.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = vm::testAiConnection,
                enabled = !aiTesting,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceColor),
            ) {
                Text(if (aiTesting) "Probando…" else "Probar", color = TextPrimaryColor)
            }
            Button(
                onClick = {
                    vm.saveAiSettings()
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) {
                Text("Guardar", color = BgColor, fontWeight = FontWeight.SemiBold)
            }
        }

        if (!compact) {
            TextButton(onClick = onDone) {
                Text("Después", color = TextMutedColor)
            }
        }
    }
}

@Composable
private fun AiValueBulletRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null, tint = AccentColor)
        Spacer(Modifier.width(12.dp))
        Text(text, color = TextPrimaryColor, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiValueScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Inteligencia artificial", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextPrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        containerColor = BgColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            AiValueContent(onDone = onBack)
        }
    }
}

@Composable
private fun aiValueTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimaryColor,
    unfocusedTextColor = TextPrimaryColor,
    focusedContainerColor = SurfaceColor,
    unfocusedContainerColor = SurfaceColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = TextMutedColor,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = TextMutedColor,
    cursorColor = AccentColor,
)
