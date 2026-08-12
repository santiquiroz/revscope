package com.revscope.core.obd.legal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Disparo puntual del aviso diario y re-agendado.
 *
 * Postea el aviso EN EL PROPIO receiver (goAsync) en vez de encolar trabajo en WorkManager:
 * durante Doze la alarma nos da una ventana de ejecución, pero JobScheduler sigue restringido,
 * así que encolar un job ahí devolvería el problema que este receiver existe para resolver.
 *
 * Resuelve la dependencia con [EntryPointAccessors] en vez de `@AndroidEntryPoint`: en Kotlin,
 * el plugin de Hilt reescribe la superclase en bytecode, así que el `super.onReceive()` que la
 * inyección de campos exige ni siquiera compila ("Abstract member cannot be accessed directly").
 *
 * Las alarmas no sobreviven ni al reinicio ni a la actualización de la app, por eso también
 * escucha BOOT_COMPLETED y MY_PACKAGE_REPLACED.
 */
class DailyStatusReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotifierEntryPoint {
        fun dailyStatusNotifier(): DailyStatusNotifier
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        // Reagendar siempre y primero: una alarma exacta es de un solo uso, y un fallo posterior
        // no puede dejar la cadena rota para siempre.
        DailyStatusScheduler.scheduleNext(context)

        if (intent.action != DailyStatusScheduler.ACTION_DAILY_STATUS) {
            Timber.i("DailyStatusReceiver: reagendado tras ${intent.action}")
            return
        }

        val notifier = runCatching {
            EntryPointAccessors
                .fromApplication(context.applicationContext, NotifierEntryPoint::class.java)
                .dailyStatusNotifier()
        }.onFailure { Timber.e(it, "DailyStatusReceiver: no se pudo resolver el notificador") }
            .getOrNull() ?: return

        val force = intent.getBooleanExtra(DailyStatusScheduler.EXTRA_FORCE, false)
        val pendingResult = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(WORK_TIMEOUT_MS) {
                    notifier.notifyIfNotable(trigger = if (force) "prueba" else "alarma", force = force)
                } ?: Timber.w("DailyStatusReceiver: se agotó el tiempo del aviso")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        // goAsync da ~10s antes de que el sistema mate el proceso; el corte queda por debajo
        // para que el finish() alcance a ejecutarse.
        const val WORK_TIMEOUT_MS = 8_000L
    }
}
