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
import androidx.compose.ui.res.stringResource
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
        stringResource(R.string.settings_diagnostico_crash_subject_car)
    } else {
        stringResource(R.string.settings_diagnostico_crash_subject_motorcycle)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_diagnostico_detect_title))
        Text(
            stringResource(R.string.settings_diagnostico_detect_description, crashSubject),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = emergencyPhone,
            onValueChange = vm::updateEmergencyPhone,
            label = { Text(stringResource(R.string.settings_diagnostico_emergency_phone_label), fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = vm::saveEmergencyPhone,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text(stringResource(R.string.settings_diagnostico_save_phone), color = BgColor) }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_diagnostico_enable_detection),
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
            stringResource(R.string.settings_diagnostico_detection_requires),
            color = TextMutedColor,
            fontSize = 11.sp,
        )
        Button(
            onClick = vm::testCrashAlert,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text(stringResource(R.string.settings_diagnostico_test_alert), color = TextPrimaryColor) }
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
        SectionTitle(stringResource(R.string.settings_diagnostico_guard_title))
        Text(
            stringResource(R.string.settings_diagnostico_guard_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        ToggleRow(
            stringResource(R.string.settings_diagnostico_guard_toggle),
            guardRunning,
            { enabled -> if (enabled) vm.armGuard() else vm.disarmGuard() },
            subtitle = if (guardAlarmActive) stringResource(R.string.settings_diagnostico_guard_alarm_active) else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomPidsCard(vm: SettingsViewModel) {
    val context = LocalContext.current
    val customPidsJson by vm.customPidsJson.collectAsState()
    val sharePackSubject = stringResource(R.string.settings_diagnostico_share_pack_subject)
    val sharePackLabel = stringResource(R.string.settings_diagnostico_share_pack)
    val sharePackChooser = stringResource(R.string.settings_diagnostico_share_pack_chooser)
    val importLabel = stringResource(R.string.settings_diagnostico_import)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_diagnostico_pids_title))
        Text(
            stringResource(R.string.settings_diagnostico_pids_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = customPidsJson,
            onValueChange = vm::updateCustomPidsJson,
            label = { Text(stringResource(R.string.settings_diagnostico_custom_pids_label), fontSize = 12.sp) },
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
            ) { Text(stringResource(R.string.settings_diagnostico_validate_apply), color = BgColor) }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, sharePackSubject)
                        putExtra(Intent.EXTRA_TEXT, customPidsJson)
                    }
                    context.startActivity(Intent.createChooser(intent, sharePackChooser))
                },
                enabled = customPidsJson.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) { Text(sharePackLabel, color = TextPrimaryColor) }
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
            ) { Text(importLabel, color = TextPrimaryColor) }
        }
    }
}

@Composable
internal fun CustomPidAlertsCard(vm: SettingsViewModel) {
    val customAlertsJson by vm.customAlertsJson.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_diagnostico_alerts_title))
        Text(
            stringResource(R.string.settings_diagnostico_alerts_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = customAlertsJson,
            onValueChange = vm::updateCustomAlertsJson,
            label = { Text(stringResource(R.string.settings_diagnostico_alerts_json_label), fontSize = 12.sp) },
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
        ) { Text(stringResource(R.string.settings_diagnostico_validate_apply), color = BgColor) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CopiaSeguridadCard(vm: SettingsViewModel, onImportRequested: (Uri) -> Unit) {
    val backupState by vm.backupState.collectAsState()
    val autoBackupEnabled by vm.autoBackupEnabled.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_diagnostico_backup_title))
        Text(
            stringResource(R.string.settings_diagnostico_backup_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        if (backupState == SettingsViewModel.BackupState.RESTARTING_AFTER_IMPORT) {
            Text(stringResource(R.string.settings_diagnostico_backup_restored), color = AccentColor, fontSize = 12.sp)
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
                    if (backupState == SettingsViewModel.BackupState.EXPORTING) stringResource(R.string.settings_diagnostico_exporting) else stringResource(R.string.settings_diagnostico_export_copy),
                    color = BgColor,
                )
            }
            Button(
                onClick = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !backupBusy,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
            ) {
                Text(
                    if (backupState == SettingsViewModel.BackupState.IMPORTING) stringResource(R.string.settings_diagnostico_importing) else stringResource(R.string.settings_diagnostico_import_copy),
                    color = TextPrimaryColor,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        ToggleRow(stringResource(R.string.settings_diagnostico_auto_backup_weekly), autoBackupEnabled, vm::updateAutoBackupEnabled)
        Text(
            stringResource(R.string.settings_diagnostico_auto_backup_description),
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
    val mcpUrlLabel = stringResource(R.string.settings_diagnostico_mcp_url_clip)
    val mcpTokenLabel = stringResource(R.string.settings_diagnostico_mcp_token_clip)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_diagnostico_mcp_title))
        Text(
            stringResource(R.string.settings_diagnostico_mcp_description),
            color = TextMutedColor,
            fontSize = 12.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_diagnostico_mcp_active),
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
                onCopyUrl = { url -> copyToClipboard(context, mcpUrlLabel, url) },
                onCopyToken = { copyToClipboard(context, mcpTokenLabel, mcpToken) },
            )
            McpPermisosToggles()
        }
    }
}

@Composable
private fun McpServerStatusText(enabled: Boolean, state: McpServerState) {
    if (!enabled) return
    val text = when (state) {
        is McpServerState.Running -> null // shown by McpConnectionDetails instead
        McpServerState.NoWifi -> stringResource(R.string.settings_diagnostico_mcp_no_wifi)
        McpServerState.Stopped -> stringResource(R.string.settings_diagnostico_mcp_starting)
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
        McpDetailRow(label = stringResource(R.string.settings_diagnostico_mcp_url_label), value = mcpServerState.url, onCopy = { onCopyUrl(mcpServerState.url) })
    }
    if (mcpToken.isNotBlank()) {
        McpDetailRow(label = stringResource(R.string.settings_diagnostico_mcp_token_label), value = mcpToken, onCopy = onCopyToken)
    }
    Text(
        stringResource(R.string.settings_diagnostico_mcp_help),
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
        TextButton(onClick = onCopy) { Text(stringResource(R.string.settings_diagnostico_copy), color = AccentColor) }
    }
}

private fun copyToClipboard(context: android.content.Context, label: String, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.settings_diagnostico_copied, label), Toast.LENGTH_SHORT).show()
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
        SectionTitle(stringResource(R.string.settings_diagnostico_about_title))
        Text(stringResource(R.string.settings_diagnostico_about_version, appVersion), color = TextMutedColor, fontSize = 12.sp)
        Button(
            onClick = vm::checkForUpdates,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text(stringResource(R.string.settings_diagnostico_check_updates), color = TextPrimaryColor) }
        Button(
            onClick = onRerunOnboarding,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text(stringResource(R.string.settings_diagnostico_rerun_onboarding), color = TextPrimaryColor) }
    }
}
