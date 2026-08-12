package com.revscope.core.obd.legal

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Red de seguridad del aviso diario "Vehículo al día".
 *
 * WorkManager es trabajo DIFERIBLE: bajo Doze o con la app en un bucket de standby agresivo
 * (Samsung), este trabajo puede quedarse pendiente horas y ejecutarse recién cuando el usuario
 * abre la app — que es justo lo que pasó el 2026-08-11 con un pico y placa vigente. El disparo
 * puntual lo hace la alarma exacta de [DailyStatusReceiver]; esto queda como respaldo por si la
 * alarma se pierde (p. ej. sin permiso de alarmas exactas). [DailyStatusNotifier] deduplica por
 * día calendario, así que ejecutarse tarde no produce un aviso repetido.
 */
@HiltWorker
class DailyStatusWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: DailyStatusNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        notifier.notifyIfNotable(trigger = "worker")
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_status"

        /** Alias del extra del deep link — el intent lo arma [DailyStatusNotifier]. */
        const val EXTRA_OPEN_AL_DIA = DailyStatusNotifier.EXTRA_OPEN_AL_DIA
    }
}
