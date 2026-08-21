package com.revscope.feature.settings

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.data.db.entities.vehicleType
import com.revscope.core.obd.mcp.McpServerState
import java.time.LocalDate

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun DeteccionCaidaCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val activeVehicleProfile by vm.activeVehicleProfile.collectAsState()
    val emergencyPhone by vm.emergencyPhone.collectAsState()
    val crashDetectionEnabled by vm.crashDetectionEnabled.collectAsState()
    val crashPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        // NEW-7: re-check actual permission state instead of trusting the grants map alone —
        // when only POST_NOTIFICATIONS was missing (SMS already granted), SEND_SMS is absent
        // from `grants` entirely and would otherwise be misread as denied.
    ) { _ -> if (hasSmsPermission(context)) vm.updateCrashDetectionEnabled(true) }

    val crashSubject = if (activeVehicleProfile?.vehicleType == VehicleType.CAR) {
        "un posible choque"
    } else {
        "un posible accidente de moto"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Detección de caída")
        Text(
            "Si detecta $crashSubject, suena una alarma de pantalla completa con " +
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
            verticalAlignment = Alignment.CenterVertically,
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
    }
}

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
internal fun ModoGuardiaCard(vm: SettingsViewModel) {
    val guardRunning by vm.guardRunning.collectAsState()
    val guardAlarmActive by vm.guardAlarmActive.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomPidsCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val customPidsJson by vm.customPidsJson.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

@Composable
internal fun CustomPidAlertsCard(vm: SettingsViewModel) {
    val customAlertsJson by vm.customAlertsJson.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CopiaSeguridadCard(vm: SettingsViewModel, onImportRequested: (Uri) -> Unit) {
    val backupState by vm.backupState.collectAsState()
    val autoBackupEnabled by vm.autoBackupEnabled.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        ) { uri -> uri?.let(onImportRequested) }
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
    }
}

private fun defaultBackupFileName(): String = "revscope-backup-${LocalDate.now()}.zip"

@Composable
internal fun ServidorMcpCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val mcpServerEnabled by vm.mcpServerEnabled.collectAsState()
    val mcpToken by vm.mcpToken.collectAsState()
    val mcpServerState by vm.mcpServerState.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            verticalAlignment = Alignment.CenterVertically,
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
        verticalAlignment = Alignment.CenterVertically,
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

@Composable
internal fun AcercaDeCard(vm: SettingsViewModel, onRerunOnboarding: () -> Unit) {
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Acerca de")
        Text("RevScope v$appVersion", color = TextMutedColor, fontSize = 12.sp)
        Button(
            onClick = vm::checkForUpdates,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text("Buscar actualizaciones", color = TextPrimaryColor) }
        Button(
            onClick = onRerunOnboarding,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text("Volver a ver configuración inicial", color = TextPrimaryColor) }
    }
}
