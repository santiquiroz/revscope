package com.revscope.app.safety

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.revscope.core.obd.safety.CrashResponder

private val AlarmBgColor = Color(0xFF1A0000)
private val AlarmAccentColor = Color(0xFFFF4D4D)
private val ConfirmColor = Color(0xFF4CD964)
private val TextMutedColor = Color(0xFFD0A0A0)

/** Full-screen blocking dialog shown while a crash alarm is counting down. */
@Composable
fun CrashAlertDialog(state: CrashResponder.AlarmState, onEstoyBien: () -> Unit) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.let(::showOverLockScreen)
        onDispose { activity?.let(::clearShowOverLockScreen) }
    }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AlarmBgColor)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    "¿Estás bien?",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Se detectó una posible caída de ${state.vehicleName}.",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${state.remainingMs / 1000}s",
                    color = AlarmAccentColor,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Si no respondes, enviaremos un SMS de emergencia con tu ubicación.",
                    color = TextMutedColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onEstoyBien,
                    colors = ButtonDefaults.buttonColors(containerColor = ConfirmColor),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                ) {
                    Text("ESTOY BIEN", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** M3/M4: the alarm must be visible even if the phone is locked or the screen is off. */
private fun showOverLockScreen(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
    } else {
        @Suppress("DEPRECATION")
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
    }
}

private fun clearShowOverLockScreen(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        activity.setShowWhenLocked(false)
        activity.setTurnScreenOn(false)
    } else {
        @Suppress("DEPRECATION")
        activity.window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
    }
}
