package com.revscope.feature.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

private const val RESTART_REQUEST_CODE = 4821
private const val RESTART_DELAY_MS = 150L

/**
 * Reinicia el proceso de la app: programa el relanzamiento vía AlarmManager (sobrevive
 * a la muerte del proceso) y mata el proceso actual. Necesario después de reemplazar
 * revscope.db — la instancia de AppDatabase queda cerrada e inutilizable.
 */
fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    val pendingIntent = PendingIntent.getActivity(
        context,
        RESTART_REQUEST_CODE,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + RESTART_DELAY_MS, pendingIntent)
    Runtime.getRuntime().exit(0)
}
