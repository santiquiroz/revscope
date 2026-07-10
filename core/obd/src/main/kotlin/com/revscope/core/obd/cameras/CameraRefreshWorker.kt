package com.revscope.core.obd.cameras

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.revscope.core.data.datastore.PreferencesKeys
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Corre cada 7 días (ver RevScopeApp) y re-descarga radares alrededor del
 * último punto usado en una descarga manual exitosa (SettingsViewModel guarda
 * LAST_CAMERA_LAT/LON tras cada `downloadSpeedCameras()` exitoso). Silencioso:
 * sin ubicación guardada aún → no-op; falla de red → se reintenta la próxima
 * semana, sin notificar al usuario.
 */
@HiltWorker
class CameraRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: DataStore<Preferences>,
    private val updater: SpeedCameraUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val center = readLastCameraCenter() ?: return Result.success()
        refreshSilently(center.first, center.second)
        return Result.success()
    }

    private suspend fun readLastCameraCenter(): Pair<Double, Double>? {
        val prefs = settings.data.first()
        val lat = prefs[PreferencesKeys.LAST_CAMERA_LAT] ?: return null
        val lon = prefs[PreferencesKeys.LAST_CAMERA_LON] ?: return null
        return lat to lon
    }

    private suspend fun refreshSilently(latitude: Double, longitude: Double) {
        val result = runCatching { updater.downloadAround(latitude, longitude) }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        result.onFailure { Timber.w(it, "CameraRefreshWorker: weekly refresh failed") }
    }

    companion object {
        const val WORK_NAME = "camera_refresh"
    }
}
