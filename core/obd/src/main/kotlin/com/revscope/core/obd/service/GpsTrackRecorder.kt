package com.revscope.core.obd.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.navigation.LocationFix
import com.revscope.core.navigation.NavigationController
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.obd.cameras.CameraCoverageTracker
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.legal.CityEnforcementAlerter
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
    private val onBearing: ((Float) -> Unit)? = null,
    private val cameraAlerter: SpeedCameraAlerter? = null,
    private val coverageTracker: CameraCoverageTracker? = null,
    private val cityAlerter: CityEnforcementAlerter? = null,
    private val localInfoSink: GpsInfoSink? = null,
    private val routeHolder: LiveRouteHolder? = null,
    private val sunsetAlerter: com.revscope.core.obd.alerts.SunsetAlerter? = null,
    private val potholeAlerter: com.revscope.core.obd.road.PotholeAlerter? = null,
    private val rainWatcher: com.revscope.core.obd.weather.RainWatcher? = null,
    private val potholeSync: com.revscope.core.obd.social.PotholeSync? = null,
    /** Tick genérico 1 Hz para consumidores que se auto-throttlean (coach de fatiga). */
    private val onFixTick: (() -> Unit)? = null,
    /** Wired by the service ONLY in GPS-only trip mode, to drive the GPS_SPEED pseudo-reading. */
    private val onSpeed: ((Float) -> Unit)? = null,
    /** La navegación paso a paso: recibe la lectura completa, no solo el punto. */
    private val navigation: NavigationController? = null,
) {

    private val buffer = mutableListOf<GpsPointEntity>()
    private var listener: LocationListener? = null
    private var flushJob: Job? = null
    private var recorderScope: CoroutineScope? = null

    // NEW-1: lets the foreground service silence DB writes during crash-detection grace
    // (closed session already has its aggregates computed) while GPS keeps running and
    // still feeds trackModeEngine/cameraAlerter/routeHolder (and thus CrashResponder).
    @Volatile private var persistenceEnabled = true

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
        recorderScope = scope
        persistenceEnabled = true
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
        recorderScope = null
        persistenceEnabled = true
    }

    /**
     * NEW-1: GPS keeps registering fixes and keeps feeding trackModeEngine/cameraAlerter/
     * routeHolder either way — only the buffer append + DB flush path is gated. Flushes
     * whatever is already buffered once before turning persistence off so no in-flight
     * points are silently dropped.
     */
    fun setPersistenceEnabled(enabled: Boolean) {
        if (persistenceEnabled == enabled) return
        persistenceEnabled = enabled
        if (!enabled) recorderScope?.launch { flush() }
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
        if (persistenceEnabled) {
            synchronized(buffer) { buffer += point }
        }
        trackModeEngine?.onGpsFix(location.latitude, location.longitude, timestamp)
        cameraAlerter?.onGpsFix(
            location.latitude,
            location.longitude,
            if (location.hasBearing()) location.bearing else null,
        )
        coverageTracker?.onGpsFix(location.latitude, location.longitude)
        cityAlerter?.onGpsFix(location.latitude, location.longitude)
        localInfoSink?.onGpsFix(location.latitude, location.longitude)
        sunsetAlerter?.onGpsFix(location.latitude, location.longitude)
        potholeAlerter?.onGpsFix(
            location.latitude,
            location.longitude,
            if (location.hasBearing()) location.bearing else null,
        )
        rainWatcher?.onGpsFix(location.latitude, location.longitude)
        potholeSync?.onGpsFix(location.latitude, location.longitude)
        onFixTick?.invoke()
        routeHolder?.append(location.latitude, location.longitude)
        routeHolder?.updateSpeed(point.speedKmh)
        onSpeed?.invoke(point.speedKmh)
        if (location.hasBearing()) onBearing?.invoke(location.bearing)
        navigation?.onFix(location.toNavigationFix())
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

private fun Location.toNavigationFix() = LocationFix(
    lat = latitude,
    lon = longitude,
    accuracyM = if (hasAccuracy()) accuracy.toDouble() else UNKNOWN_ACCURACY_M,
    bearingDeg = if (hasBearing()) bearing.toInt() else null,
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    timestampMs = time,
)

/** Sin exactitud declarada hay que asumir lo peor, o el detector de desvío se dispara solo. */
private const val UNKNOWN_ACCURACY_M = 50.0
