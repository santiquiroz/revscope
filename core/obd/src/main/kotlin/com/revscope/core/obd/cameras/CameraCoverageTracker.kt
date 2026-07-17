package com.revscope.core.obd.cameras

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val ATTEMPT_COOLDOWN_MS = 30 * 60_000L
private const val EVALUATE_THROTTLE_MS = 60_000L

/**
 * Watches the live GPS stream and re-downloads the speed-camera database around the
 * current position when the vehicle leaves the stored download coverage (>35 km from
 * the last download center, or no download yet). On success the center moves with the
 * trip, so the weekly [CameraRefreshWorker] keeps refreshing the region the user is
 * actually in. Network failures are silent — retried after the 30 min cooldown.
 */
@Singleton
class CameraCoverageTracker @Inject constructor(
    private val settings: DataStore<Preferences>,
    private val updater: SpeedCameraUpdater,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var lastEvaluatedAt = 0L
    @Volatile private var lastAttemptAt = 0L
    private val refreshing = AtomicBoolean(false)

    fun onGpsFix(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        if (now - lastEvaluatedAt < EVALUATE_THROTTLE_MS) return
        lastEvaluatedAt = now
        if (now - lastAttemptAt < ATTEMPT_COOLDOWN_MS) return
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                evaluate(latitude, longitude, now)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private suspend fun evaluate(latitude: Double, longitude: Double, now: Long) {
        try {
            val prefs = settings.data.first()
            val centerLat = prefs[PreferencesKeys.LAST_CAMERA_LAT]
            val centerLon = prefs[PreferencesKeys.LAST_CAMERA_LON]
            if (!CameraCoverage.needsRefresh(centerLat, centerLon, latitude, longitude)) return
            lastAttemptAt = now
            Timber.i("CameraCoverageTracker: left coverage — downloading around $latitude,$longitude")
            updater.downloadAround(latitude, longitude)
            persistCenter(latitude, longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "CameraCoverageTracker: auto refresh failed — retry after cooldown")
        }
    }

    private suspend fun persistCenter(latitude: Double, longitude: Double) {
        settings.edit {
            it[PreferencesKeys.LAST_CAMERA_LAT] = latitude
            it[PreferencesKeys.LAST_CAMERA_LON] = longitude
        }
    }
}
