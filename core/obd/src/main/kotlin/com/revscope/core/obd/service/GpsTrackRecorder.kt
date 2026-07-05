package com.revscope.core.obd.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.obd.track.TrackModeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val GPS_INTERVAL_MS = 1_000L
private const val GPS_MIN_DISTANCE_M = 3f
private const val FLUSH_INTERVAL_MS = 5_000L
private const val MPS_TO_KMH = 3.6f

/**
 * Captures the GPS track for a telemetry session into Room.
 * Uses the framework LocationManager (no Play Services dependency).
 * Silently does nothing when location permission is missing — GPS is optional.
 */
class GpsTrackRecorder(
    private val context: Context,
    private val gpsDao: GpsDao,
    private val trackModeEngine: TrackModeEngine? = null,
) {

    private val buffer = mutableListOf<GpsPointEntity>()
    private var listener: LocationListener? = null
    private var flushJob: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(scope: CoroutineScope, sessionId: Long) {
        if (listener != null) return
        if (!hasPermission()) {
            Timber.i("GpsTrackRecorder: no location permission — track disabled")
            return
        }
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        val newListener = LocationListener { location -> onLocation(sessionId, location) }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_INTERVAL_MS,
                GPS_MIN_DISTANCE_M,
                newListener,
                context.mainLooper,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "GpsTrackRecorder: permission revoked mid-flight")
            return
        } catch (e: Exception) {
            Timber.w(e, "GpsTrackRecorder: GPS provider unavailable")
            return
        }
        listener = newListener
        Timber.i("GpsTrackRecorder: recording track for session $sessionId")

        flushJob = scope.launch {
            try {
                while (true) {
                    delay(FLUSH_INTERVAL_MS)
                    flush()
                }
            } finally {
                withContext(NonCancellable) { flush() }
            }
        }
    }

    fun stop() {
        listener?.let { active ->
            runCatching {
                (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
                    ?.removeUpdates(active)
            }
        }
        listener = null
        flushJob?.cancel()
        flushJob = null
    }

    private fun onLocation(sessionId: Long, location: Location) {
        val timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        val point = GpsPointEntity(
            sessionId = sessionId,
            timestamp = timestamp,
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = if (location.hasSpeed()) location.speed * MPS_TO_KMH else 0f,
        )
        synchronized(buffer) { buffer += point }
        trackModeEngine?.onGpsFix(location.latitude, location.longitude, timestamp)
    }

    private suspend fun flush() {
        val snapshot = synchronized(buffer) {
            if (buffer.isEmpty()) return
            buffer.toList().also { buffer.clear() }
        }
        runCatching { gpsDao.insertAll(snapshot) }
            .onFailure { Timber.e(it, "GpsTrackRecorder: flush failed (${snapshot.size} points)") }
    }
}
