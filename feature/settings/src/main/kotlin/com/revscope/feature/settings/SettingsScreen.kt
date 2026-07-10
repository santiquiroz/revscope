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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    val apiKey by vm.apiKey.collectAsState()
    val customPidsJson by vm.customPidsJson.collectAsState()
    val customAlertsJson by vm.customAlertsJson.collectAsState()
    val activeVehicleProfile by vm.activeVehicleProfile.collectAsState()
    val askVehicleOnStart by vm.askVehicleOnStart.collectAsState()
    val alertsEnabled by vm.alertsEnabled.collectAsState()
    val ttsEnabled by vm.ttsEnabled.collectAsState()
    val tempMaxC by vm.tempMaxC.collectAsState()
    val voltageMin by vm.voltageMin.collectAsState()
    val redlineRpm by vm.redlineRpm.collectAsState()
    val fuelPriceCop by vm.fuelPriceCop.collectAsState()
    val voiceTemperature by vm.voiceTemperature.collectAsState()
    val voiceVoltage by vm.voiceVoltage.collectAsState()
    val voiceSpeedCameras by vm.voiceSpeedCameras.collectAsState()
    val voiceAnomalies by vm.voiceAnomalies.collectAsState()
    val voiceMil by vm.voiceMil.collectAsState()
    val voiceRedline by vm.voiceRedline.collectAsState()
    val voiceCustomThresholds by vm.voiceCustomThresholds.collectAsState()
    val voiceSport by vm.voiceSport.collectAsState()
    val voicePicoPlaca by vm.voicePicoPlaca.collectAsState()
    val saveResult by vm.lastSaveResult.collectAsState()
    val backupState by vm.backupState.collectAsState()
    val autoBackupEnabled by vm.autoBackupEnabled.collectAsState()
    val crashDetectionEnabled by vm.crashDetectionEnabled.collectAsState()
    val emergencyPhone by vm.emergencyPhone.collectAsState()
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
            SectionTitle("Combustible")
            OutlinedTextField(
                value = fuelPriceCop,
                onValueChange = vm::updateFuelPriceCop,
                label = { Text("Precio galón corriente (COP)", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Se usa para estimar el costo de cada viaje en el reporte.",
                color = TextMutedColor,
                fontSize = 11.sp,
            )
            Button(
                onClick = vm::saveFuelPriceCop,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Guardar precio", color = BgColor) }

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
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimaryColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
