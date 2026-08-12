package com.revscope.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.revscope.core.data.backup.AutoBackupWorker
import com.revscope.core.obd.cameras.CameraRefreshWorker
import com.revscope.core.obd.legal.DailyStatusSchedule
import com.revscope.core.obd.legal.DailyStatusScheduler
import com.revscope.core.obd.legal.DailyStatusWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val CAMERA_REFRESH_INTERVAL_DAYS = 7L
private const val AUTO_BACKUP_INTERVAL_DAYS = 7L

@HiltAndroidApp
class RevScopeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Alarma puntual (atraviesa Doze) + trabajo periódico como red de seguridad. El aviso
        // se deduplica por día, así que tener los dos disparos no produce avisos repetidos.
        DailyStatusScheduler.scheduleNext(this)
        scheduleDailyStatusWorker()
        scheduleCameraRefreshWorker()
        scheduleAutoBackupWorker()
    }

    private fun scheduleDailyStatusWorker() {
        val request = PeriodicWorkRequestBuilder<DailyStatusWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextFiveThirtyAmMs(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(DailyStatusWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun scheduleCameraRefreshWorker() {
        val request = PeriodicWorkRequestBuilder<CameraRefreshWorker>(CAMERA_REFRESH_INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(CameraRefreshWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun scheduleAutoBackupWorker() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(AUTO_BACKUP_INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(AutoBackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Milisegundos hasta las próximas 5:30am America/Bogota (hoy si aún no pasan, si no mañana). */
private fun delayUntilNextFiveThirtyAmMs(): Long {
    val now = System.currentTimeMillis()
    return DailyStatusSchedule.nextTriggerAtMs(now) - now
}
