package com.revscope.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.revscope.core.data.backup.AutoBackupWorker
import com.revscope.core.obd.cameras.CameraRefreshWorker
import com.revscope.core.obd.legal.DailyStatusWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val DAILY_STATUS_HOUR = 5
private const val DAILY_STATUS_MINUTE = 30
private const val BOGOTA_ZONE_ID = "America/Bogota"
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
        scheduleDailyStatusWorker()
        scheduleCameraRefreshWorker()
        scheduleAutoBackupWorker()
    }

    private fun scheduleDailyStatusWorker() {
        val request = PeriodicWorkRequestBuilder<DailyStatusWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextFiveThirtyAmMs(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(DailyStatusWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun scheduleCameraRefreshWorker() {
        val request = PeriodicWorkRequestBuilder<CameraRefreshWorker>(CAMERA_REFRESH_INTERVAL_DAYS, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(CameraRefreshWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun scheduleAutoBackupWorker() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(AUTO_BACKUP_INTERVAL_DAYS, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(AutoBackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Milisegundos hasta las próximas 5:30am America/Bogota (hoy si aún no pasan, si no mañana). */
private fun delayUntilNextFiveThirtyAmMs(): Long {
    val now = ZonedDateTime.now(ZoneId.of(BOGOTA_ZONE_ID))
    val todayTarget = now.withHour(DAILY_STATUS_HOUR).withMinute(DAILY_STATUS_MINUTE).withSecond(0).withNano(0)
    val nextTarget = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
    return Duration.between(now, nextTarget).toMillis()
}
