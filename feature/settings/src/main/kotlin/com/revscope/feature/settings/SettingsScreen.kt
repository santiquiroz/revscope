package com.revscope.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.revscope.core.intelligence.provider.AI_PROVIDER_CUSTOM
import com.revscope.core.intelligence.provider.AI_PROVIDER_NODO
import com.revscope.core.intelligence.provider.NODO_BASE_URL_POR_DEFECTO
import com.revscope.core.obd.mcp.McpServerState
import com.revscope.core.obd.sound.SoundPack
import java.time.LocalDate
import kotlinx.coroutines.delay

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToVehicleProfiles: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val aiProvider by vm.aiProvider.collectAsState()
    val aiApiKey by vm.aiApiKey.collectAsState()
    val aiModel by vm.aiModel.collectAsState()
    val aiCustomBaseUrl by vm.aiCustomBaseUrl.collectAsState()
    val aiTesting by vm.aiTesting.collectAsState()
    val customPidsJson by vm.customPidsJson.collectAsState()
    val customAlertsJson by vm.customAlertsJson.collectAsState()
    val picoPlacaRulesJson by vm.picoPlacaRulesJson.collectAsState()
    val activeVehicleProfile by vm.activeVehicleProfile.collectAsState()
    val askVehicleOnStart by vm.askVehicleOnStart.collectAsState()
    val alertsEnabled by vm.alertsEnabled.collectAsState()
    val ttsEnabled by vm.ttsEnabled.collectAsState()
    val tempMaxC by vm.tempMaxC.collectAsState()
    val voltageMin by vm.voltageMin.collectAsState()
    val redlineRpm by vm.redlineRpm.collectAsState()
    val fuelPriceCorriente by vm.fuelPriceCorriente.collectAsState()
    val fuelPriceExtra by vm.fuelPriceExtra.collectAsState()
    val fuelPriceDiesel by vm.fuelPriceDiesel.collectAsState()
    val voiceTemperature by vm.voiceTemperature.collectAsState()
    val voiceVoltage by vm.voiceVoltage.collectAsState()
    val voiceSpeedCameras by vm.voiceSpeedCameras.collectAsState()
    val voiceSunset by vm.voiceSunset.collectAsState()
    val voicePotholes by vm.voicePotholes.collectAsState()
    val voiceRain by vm.voiceRain.collectAsState()
    val voiceFatigue by vm.voiceFatigue.collectAsState()
    val serverUrl by vm.serverUrl.collectAsState()
    val serverToken by vm.serverToken.collectAsState()
    val riderName by vm.riderName.collectAsState()
    val zoneBriefEnabled by vm.zoneBriefEnabled.collectAsState()
    val voiceZoneBrief by vm.voiceZoneBrief.collectAsState()
    val guardRunning by vm.guardRunning.collectAsState()
    val guardAlarmActive by vm.guardAlarmActive.collectAsState()
    val voiceAnomalies by vm.voiceAnomalies.collectAsState()
    val voiceMil by vm.voiceMil.collectAsState()
    val voiceRedline by vm.voiceRedline.collectAsState()
    val voiceCustomThresholds by vm.voiceCustomThresholds.collectAsState()
    val voiceSport by vm.voiceSport.collectAsState()
    val voicePicoPlaca by vm.voicePicoPlaca.collectAsState()
    val voiceLocalInfo by vm.voiceLocalInfo.collectAsState()
    val aiPicoPlaca by vm.aiPicoPlaca.collectAsState()
    val saveResult by vm.lastSaveResult.collectAsState()
    val backupState by vm.backupState.collectAsState()
    val autoBackupEnabled by vm.autoBackupEnabled.collectAsState()
    val keepScreenOn by vm.keepScreenOn.collectAsState()
    val crashDetectionEnabled by vm.crashDetectionEnabled.collectAsState()
    val emergencyPhone by vm.emergencyPhone.collectAsState()
    val mcpServerEnabled by vm.mcpServerEnabled.collectAsState()
    val mcpToken by vm.mcpToken.collectAsState()
    val mcpServerState by vm.mcpServerState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val crashPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        // NEW-7: re-check actual permission state instead of trusting the grants map alone —
        // when only POST_NOTIFICATIONS was missing (SMS already granted), SEND_SMS is absent
        // from `grants` entirely and would otherwise be misread as denied.
    ) { _ -> if (hasSmsPermission(context)) vm.updateCrashDetectionEnabled(true) }

    LaunchedEffect(saveResult) {
        saveResult?.let {
            snackbarHostState.showSnackbar(it.message)
            vm.dismissSaveResult()
        }
    }

    LaunchedEffect(backupState) {
        if (backupState == SettingsViewModel.BackupState.RESTARTING_AFTER_IMPORT) {
            delay(900)
            restartApp(context)
        }
    }

    pendingImportUri?.let { uri ->
        BackupRestoreConfirmDialog(
            onConfirm = {
                pendingImportUri = null
                vm.importBackup(uri)
            },
            onDismiss = { pendingImportUri = null },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
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
            SectionTitle("Vehículo")
            NavRow(
                "Vehículo activo: ${activeVehicleProfile?.name ?: "Ninguno"}",
                onNavigateToVehicleProfiles,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Preguntar vehículo al inicio",
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = askVehicleOnStart,
                    onCheckedChange = vm::updateAskVehicleOnStart,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgColor,
                        checkedTrackColor = AccentColor,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Herramientas")
            NavRow("Perfiles de vehículo", onNavigateToVehicleProfiles)
            ToggleRow(
                "Mantener pantalla encendida",
                keepScreenOn,
                vm::updateKeepScreenOn,
                subtitle = "Durante la conducción con el dashboard abierto. Apagarlo ahorra batería en soporte de carro.",
            )

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

            Spacer(Modifier.height(8.dp))
            SectionTitle("Sonido de motor")
            Text(
                "Sintetiza el sonido de un motor deportivo (o uno gracioso) siguiendo las RPM " +
                    "reales del OBD2 — suena por los parlantes o el intercomunicador Bluetooth. " +
                    "Estilo SoundRacer, sin comprar el adaptador.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            val engineSoundEnabled by vm.engineSoundEnabled.collectAsState()
            val engineSoundPack by vm.engineSoundPack.collectAsState()
            val engineSoundVolume by vm.engineSoundVolume.collectAsState()
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

            Spacer(Modifier.height(8.dp))
            SectionTitle("Combustible")
            OutlinedTextField(
                value = fuelPriceCorriente,
                onValueChange = vm::updateFuelPriceCorriente,
                label = { Text("Precio galón corriente (COP)", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fuelPriceExtra,
                onValueChange = vm::updateFuelPriceExtra,
                label = { Text("Precio galón extra (COP)", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fuelPriceDiesel,
                onValueChange = vm::updateFuelPriceDiesel,
                label = { Text("Precio galón diésel / ACPM (COP)", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Se usa el precio del tipo de combustible del vehículo activo para estimar el costo de " +
                    "cada viaje. No hay fuente oficial en línea vigente en datos.gov.co para precios de " +
                    "gasolina/ACPM por municipio (ver detalle en el código) — ajusta manualmente según tu estación.",
                color = TextMutedColor,
                fontSize = 11.sp,
            )
            Button(
                onClick = vm::saveFuelPrices,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Guardar precios", color = BgColor) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Radares de velocidad")
            Text(
                "Descarga los radares en 50 km a tu alrededor. " +
                    "Fuentes: OpenStreetMap + registro oficial ANSV · se actualiza cada semana automáticamente. " +
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

            val cameraAlertRadius by vm.cameraAlertRadius.collectAsState()
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

            Spacer(Modifier.height(8.dp))
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
                    fontFamily = FontFamily.Monospace,
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
                fontFamily = FontFamily.Monospace,
            )
            Button(
                onClick = vm::savePicoPlacaRules,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Validar y aplicar", color = BgColor) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Copia de seguridad")
            Text(
                "Incluye viajes, perfiles, informes y ajustes. La API key NO se incluye " +
                    "(queda cifrada en este dispositivo) — guárdala de nuevo después de restaurar.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            if (backupState == SettingsViewModel.BackupState.RESTARTING_AFTER_IMPORT) {
                Text("Copia restaurada — reiniciando…", color = AccentColor, fontSize = 12.sp)
            }
            val exportBackupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip"),
            ) { uri -> uri?.let(vm::exportBackup) }
            val importBackupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let { pendingImportUri = it } }
            val backupBusy = backupState != SettingsViewModel.BackupState.IDLE
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { exportBackupLauncher.launch(defaultBackupFileName()) },
                    enabled = !backupBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                ) {
                    Text(
                        if (backupState == SettingsViewModel.BackupState.EXPORTING) "Exportando…" else "Exportar copia",
                        color = BgColor,
                    )
                }
                Button(
                    onClick = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !backupBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) {
                    Text(
                        if (backupState == SettingsViewModel.BackupState.IMPORTING) "Importando…" else "Importar copia",
                        color = TextPrimaryColor,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            ToggleRow("Copia automática semanal", autoBackupEnabled, vm::updateAutoBackupEnabled)
            Text(
                "Cada semana guarda una copia en Descargas/RevScope (se conservan las últimas 4).",
                color = TextMutedColor,
                fontSize = 11.sp,
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("Detección de caída")
            Text(
                "Si detecta un posible accidente de moto, suena una alarma de pantalla completa con " +
                    "cuenta regresiva de 60 s. Si no respondes \"ESTOY BIEN\", envía un SMS con tu última " +
                    "ubicación al contacto de emergencia. Desactivada por defecto.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = emergencyPhone,
                onValueChange = vm::updateEmergencyPhone,
                label = { Text("Teléfono de emergencia", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::saveEmergencyPhone,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Guardar teléfono", color = BgColor) }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Activar detección de caída",
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = crashDetectionEnabled,
                    onCheckedChange = { checked ->
                        val missing = if (checked) missingCrashDetectionPermissions(context) else emptyArray()
                        if (missing.isNotEmpty()) {
                            crashPermissionLauncher.launch(missing)
                        } else {
                            vm.updateCrashDetectionEnabled(checked)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgColor,
                        checkedTrackColor = AccentColor,
                    ),
                )
            }
            Text(
                "Requiere teléfono de emergencia guardado y permiso de SMS.",
                color = TextMutedColor,
                fontSize = 11.sp,
            )
            Button(
                onClick = vm::testCrashAlert,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) { Text("Probar (sin enviar SMS real)", color = TextPrimaryColor) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Modo guardia antirrobo")
            Text(
                "Con la moto apagada y parqueada cerca, el teléfono vigila el adaptador OBD: " +
                    "si despierta (alguien giró la llave), suena una alarma fuerte. Requiere estar " +
                    "en rango Bluetooth (~10-30 m) — cubre el robo exprés, no vigilancia remota.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            ToggleRow(
                "Modo guardia",
                guardRunning,
                { enabled -> if (enabled) vm.armGuard() else vm.disarmGuard() },
                subtitle = if (guardAlarmActive) "⚠ ¡ALARMA ACTIVA — el adaptador despertó!" else null,
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("Inteligencia artificial")
            Text(
                "Proveedor usado para explicar códigos DTC e información local. Sin API key, " +
                    "esas funciones se muestran sin explicación de IA.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            AiProviderDropdown(selected = aiProvider, onSelected = vm::updateAiProvider)
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = vm::updateAiApiKey,
                label = { Text("API key de ${aiProviderLabel(aiProvider)}", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = aiModel,
                onValueChange = vm::updateAiModel,
                label = { Text("Modelo (vacío = ${aiModelHint(aiProvider)})", fontSize = 12.sp) },
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
                            if (aiProvider == AI_PROVIDER_NODO) "Base URL (vacío = $NODO_BASE_URL_POR_DEFECTO)"
                            else "Base URL (LM Studio, DeepSeek, Groq, OpenRouter…)",
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
                ) { Text("Guardar configuración de IA", color = BgColor) }
                Button(
                    onClick = vm::testAiConnection,
                    enabled = !aiTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                ) { Text(if (aiTesting) "Probando…" else "Probar conexión", color = TextPrimaryColor) }
            }

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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

            Spacer(Modifier.height(8.dp))
            SectionTitle("Alertas personalizadas por PID")
            Text(
                "Umbral por PID en JSON: [{\"pid\":\"0A\",\"min\":200,\"nombre\":\"Presión de combustible\"}]. " +
                    "Para TPMS u otros sensores del fabricante, define primero el PID custom (Modo 22) y luego su alerta aquí.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = customAlertsJson,
                onValueChange = vm::updateCustomAlertsJson,
                label = { Text("JSON de alertas custom", fontSize = 12.sp) },
                minLines = 4,
                maxLines = 10,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::saveCustomAlerts,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Validar y aplicar", color = BgColor) }

            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(8.dp))
            SectionTitle("Servidor MCP (red local)")
            Text(
                "Expone el estado de tu vehículo a asistentes de IA en tu red WiFi (Claude Desktop, " +
                    "LM Studio…): conexión, viajes, chequeos, DTCs, mantenimiento y documentos. " +
                    "Apagado por defecto — actívalo solo en redes de confianza.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Servidor MCP activo",
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = mcpServerEnabled,
                    onCheckedChange = vm::updateMcpServerEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgColor,
                        checkedTrackColor = AccentColor,
                    ),
                )
            }
            McpServerStatusText(mcpServerEnabled, mcpServerState)
            if (mcpServerEnabled) {
                McpConnectionDetails(
                    mcpServerState = mcpServerState,
                    mcpToken = mcpToken,
                    onCopyUrl = { url -> copyToClipboard(context, "URL MCP", url) },
                    onCopyToken = { copyToClipboard(context, "Token MCP", mcpToken) },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle("Acerca de")
            val appVersion = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "?"
            }
            Text("RevScope v$appVersion", color = TextMutedColor, fontSize = 12.sp)
            Button(
                onClick = vm::checkForUpdates,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) { Text("Buscar actualizaciones", color = TextPrimaryColor) }

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

private val AiProviderOptions = listOf(
    "anthropic" to "Claude (Anthropic)",
    "openai" to "OpenAI",
    "gemini" to "Gemini (Google)",
    AI_PROVIDER_NODO to "Nodo (este teléfono)",
    AI_PROVIDER_CUSTOM to "Compatible OpenAI (LM Studio, DeepSeek, Groq, OpenRouter…)",
)

private val AiModelHints = mapOf(
    "anthropic" to "claude-haiku-4-5-20251001",
    "openai" to "gpt-5-mini",
    "gemini" to "gemini-flash-latest",
    AI_PROVIDER_NODO to "el que tengas cargado en Nodo",
    AI_PROVIDER_CUSTOM to "según tu servidor",
)

private fun aiProviderLabel(provider: String): String =
    AiProviderOptions.firstOrNull { it.first == provider }?.second ?: provider

private fun aiModelHint(provider: String): String = AiModelHints[provider].orEmpty()

/** Ni el endpoint genérico ni Nodo traen búsqueda web del lado del servidor. */
private fun aiProviderSupportsWebSearch(provider: String): Boolean =
    provider != AI_PROVIDER_CUSTOM && provider != AI_PROVIDER_NODO

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
            label = { Text("Proveedor de IA", fontSize = 12.sp) },
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
            AiProviderOptions.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun McpServerStatusText(enabled: Boolean, state: McpServerState) {
    if (!enabled) return
    val text = when (state) {
        is McpServerState.Running -> null // shown by McpConnectionDetails instead
        McpServerState.NoWifi -> "Sin WiFi — conecta a una red para activar el servidor"
        McpServerState.Stopped -> "Iniciando servidor…"
    }
    text?.let { Text(it, color = TextMutedColor, fontSize = 12.sp) }
}

@Composable
private fun McpConnectionDetails(
    mcpServerState: McpServerState,
    mcpToken: String,
    onCopyUrl: (String) -> Unit,
    onCopyToken: () -> Unit,
) {
    if (mcpServerState is McpServerState.Running) {
        McpDetailRow(label = "URL", value = mcpServerState.url, onCopy = { onCopyUrl(mcpServerState.url) })
    }
    if (mcpToken.isNotBlank()) {
        McpDetailRow(label = "Token", value = mcpToken, onCopy = onCopyToken)
    }
    Text(
        "En Claude Desktop u otro cliente MCP: agrega un servidor tipo streamable-http con esta " +
            "URL y el header Authorization: Bearer <token>.",
        color = TextMutedColor,
        fontSize = 11.sp,
    )
}

@Composable
private fun McpDetailRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextMutedColor, fontSize = 11.sp)
            Text(value, color = AccentColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        TextButton(onClick = onCopy) { Text("Copiar", color = AccentColor) }
    }
}

private fun copyToClipboard(context: android.content.Context, label: String, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copiado", Toast.LENGTH_SHORT).show()
}

private fun defaultBackupFileName(): String = "revscope-backup-${LocalDate.now()}.zip"

private fun hasSmsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
        PackageManager.PERMISSION_GRANTED

/** M4: SMS is the hard requirement, but notifications must also be requested on API 33+ —
 * without them the alarm can't post while the app is backgrounded. */
private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

/** NEW-7: requests whichever of SEND_SMS/POST_NOTIFICATIONS is actually missing — previously
 * POST_NOTIFICATIONS was only requested alongside SEND_SMS, so a user who already granted SMS
 * but later revoked notifications was never re-prompted. */
private fun missingCrashDetectionPermissions(context: android.content.Context): Array<String> =
    buildList {
        if (!hasSmsPermission(context)) add(Manifest.permission.SEND_SMS)
        if (!hasNotificationPermission(context)) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

@Composable
private fun BackupRestoreConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("Restaurar copia de seguridad", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Reemplaza TODOS los datos actuales (viajes, perfiles, informes y ajustes) por los " +
                    "de la copia elegida. Esta acción no se puede deshacer.",
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Reemplazar", color = AccentColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMutedColor) }
        },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimaryColor, fontSize = 13.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextMutedColor, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgColor,
                checkedTrackColor = AccentColor,
            ),
        )
    }
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
