package com.revscope.feature.settings

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.legal.DailyStatusScheduler

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun AvisosSegundoPlanoCard() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_background_section_title))
        BackgroundDeliverySettingsBody()
    }
}

/**
 * El aviso diario "Vehículo al día" (pico y placa, documentos por vencer) se dispara a las
 * 5:30am con una alarma que atraviesa Doze. Samsung igual puede "dormir" la app y retrasarlo:
 * estos dos atajos llevan a los ajustes del sistema que lo evitan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundDeliverySettingsBody() {
    val context = LocalContext.current
    val exactAllowed = remember { DailyStatusScheduler.canScheduleExact(context) }
    val batteryUnrestricted = remember { isIgnoringBatteryOptimizations(context) }
    val testToast = stringResource(R.string.settings_background_test_toast)

    Text(
        stringResource(R.string.settings_background_description),
        color = TextMutedColor,
        fontSize = 12.sp,
    )
    Text(
        stringResource(
            if (exactAllowed) R.string.settings_background_exact_allowed
            else R.string.settings_background_exact_blocked,
        ),
        color = if (exactAllowed) AccentColor else TextMutedColor,
        fontSize = 12.sp,
    )
    Text(
        stringResource(
            if (batteryUnrestricted) R.string.settings_background_battery_unrestricted
            else R.string.settings_background_battery_optimized,
        ),
        color = if (batteryUnrestricted) AccentColor else TextMutedColor,
        fontSize = 12.sp,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Button(
                onClick = { openSystemSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text(stringResource(R.string.settings_background_allow_exact_alarms), color = BgColor) }
        }
        Button(
            onClick = { openSystemSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text(stringResource(R.string.settings_background_battery_optimization), color = TextPrimaryColor) }
        Button(
            onClick = {
                DailyStatusScheduler.scheduleTest(context)
                Toast.makeText(context, testToast, Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
        ) { Text(stringResource(R.string.settings_background_test_button), color = TextPrimaryColor) }
    }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean =
    runCatching {
        (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

private fun openSystemSettings(context: android.content.Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
