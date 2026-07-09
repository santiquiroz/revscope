package com.revscope.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToGearAnalyzer: () -> Unit = {},
    onNavigateToVehicleProfiles: () -> Unit = {},
    onNavigateToTrackMode: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val apiKey by vm.apiKey.collectAsState()
    val customPidsJson by vm.customPidsJson.collectAsState()
    val alertsEnabled by vm.alertsEnabled.collectAsState()
    val ttsEnabled by vm.ttsEnabled.collectAsState()
    val tempMaxC by vm.tempMaxC.collectAsState()
    val voltageMin by vm.voltageMin.collectAsState()
    val redlineRpm by vm.redlineRpm.collectAsState()
    val saveResult by vm.lastSaveResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(saveResult) {
        saveResult?.let {
            snackbarHostState.showSnackbar(it.message)
            vm.dismissSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Herramientas")
            NavRow("🏁 Modo Pista (lap timer GPS)", onNavigateToTrackMode)
            NavRow("Escáner Modo 22 (descubrir PIDs del fabricante)", onNavigateToScanner)
            NavRow("Analizador de marchas", onNavigateToGearAnalyzer)
            NavRow("Perfiles de vehículo", onNavigateToVehicleProfiles)

            Spacer(Modifier.height(8.dp))
            SectionTitle("Alertas de audio y vibración")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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

            Spacer(Modifier.height(8.dp))
            SectionTitle("Radares de velocidad (OpenStreetMap)")
            Text(
                "Descarga los radares fijos mapeados en 50 km a tu alrededor. " +
                    "Al conducir, la app avisa por voz al acercarte (funciona offline tras descargar).",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            val cameraStatus by vm.cameraStatus.collectAsState()
            cameraStatus?.let { Text(it, color = AccentColor, fontSize = 12.sp) }
            Button(
                onClick = vm::downloadSpeedCameras,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Descargar radares de mi zona", color = BgColor) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("IA — Explicación de códigos DTC")
            Text(
                "API key de Anthropic (opcional). Sin ella, los DTC se muestran sin explicación de IA.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = vm::updateApiKey,
                label = { Text("Claude API key", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::saveApiKey,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Guardar API key", color = BgColor) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("PIDs personalizados")
            Text(
                "Definiciones extra en JSON (mismo esquema que pids_mode01.json). " +
                    "Para parámetros del fabricante, p. ej. modo de manejo vía Modo 22.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = customPidsJson,
                onValueChange = vm::updateCustomPidsJson,
                label = { Text("JSON de PIDs custom", fontSize = 12.sp) },
                minLines = 4,
                maxLines = 10,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::saveCustomPids,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                ) { Text("Validar y aplicar", color = BgColor) }
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "RevScope PID pack")
                            putExtra(Intent.EXTRA_TEXT, customPidsJson)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir PID pack"))
                    },
                    enabled = customPidsJson.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) { Text("Compartir pack", color = TextPrimaryColor) }
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        runCatching {
                            context.contentResolver.openInputStream(it)
                                ?.bufferedReader()?.use { r -> r.readText() }
                        }.getOrNull()?.let(vm::updateCustomPidsJson)
                    }
                }
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) { Text("Importar", color = TextPrimaryColor) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = AccentColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, color = TextPrimaryColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("›", color = TextMutedColor, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimaryColor,
    unfocusedTextColor = TextPrimaryColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = SurfaceHighColor,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = TextMutedColor,
    cursorColor = AccentColor,
)
