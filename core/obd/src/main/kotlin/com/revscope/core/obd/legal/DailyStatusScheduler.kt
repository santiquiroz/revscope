package com.revscope.core.obd.legal

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber

/**
 * Agenda el aviso diario con AlarmManager en vez de WorkManager.
 *
 * WorkManager solo promete "en algún momento": con el celular quieto de noche (Doze) y la app
 * en un bucket de standby agresivo, el trabajo periódico se posterga hasta que algo despierta
 * el proceso — típicamente abrir la app. Para un aviso con hora útil (saber del pico y placa
 * ANTES de salir) hace falta una alarma que atraviese Doze.
 */
object DailyStatusScheduler {

    private const val REQUEST_CODE = 3101
    private const val TEST_REQUEST_CODE = 3102
    const val ACTION_DAILY_STATUS = "com.revscope.action.DAILY_STATUS"
    const val EXTRA_FORCE = "force"

    /**
     * Dispara el aviso dentro de [delayMs] usando la MISMA cadena alarma → receiver → aviso que
     * el disparo diario. Es la única forma de comprobar en el teléfono que la cadena completa
     * funciona: el receiver no está exportado, así que un `am broadcast` no lo alcanza.
     */
    fun scheduleTest(context: Context, delayMs: Long = 15_000L) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, DailyStatusReceiver::class.java)
            .setAction(ACTION_DAILY_STATUS)
            .putExtra(EXTRA_FORCE, true)
        val pendingIntent = PendingIntent.getBroadcast(
            context, TEST_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pendingIntent,
            )
            Timber.i("DailyStatusScheduler: aviso de prueba en ${delayMs / 1000}s")
        }.onFailure { Timber.w(it, "DailyStatusScheduler: no se pudo agendar la prueba") }
    }

    /** Agenda el próximo disparo. Idempotente: reemplaza cualquier alarma previa. */
    fun scheduleNext(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMs = DailyStatusSchedule.nextTriggerAtMs(nowMs)
        val pendingIntent = pendingIntent(context)
        runCatching {
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                // Sin permiso de alarmas exactas: inexacta pero igual permitida en Doze. Puede
                // correrse unos minutos, que para un aviso de las 5:30am es irrelevante.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
            Timber.i("DailyStatusScheduler: próximo aviso en ${(triggerAtMs - nowMs) / 60_000} min")
        }.onFailure { Timber.w(it, "DailyStatusScheduler: no se pudo agendar la alarma") }
    }

    /** API 31+ puede negar las alarmas exactas; el llamador no debe asumir que están concedidas. */
    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.let(::canScheduleExact) ?: false

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyStatusReceiver::class.java).setAction(ACTION_DAILY_STATUS)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
