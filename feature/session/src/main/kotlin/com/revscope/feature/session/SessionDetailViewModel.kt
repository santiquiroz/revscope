package com.revscope.feature.session

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.LapEntity
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.obd.telemetry.TripStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

private const val CHART_MAX_POINTS = 240

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
    private val gpsDao: GpsDao,
    private val lapDao: LapDao,
    private val imuDao: ImuDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class TripReport(
        val session: SessionEntity,
        val avgSpeedKmh: Float,
        val maxRpm: Int,
        val avgRpm: Int,
        val maxCoolantTemp: Int,
        val maxThrottlePct: Int,
        val totalPoints: Int,
        val rpmSeries: List<Float>,
        val speedSeries: List<Float>,
        /** lat/lon pairs, downsampled — empty when no GPS was recorded */
        val gpsTrack: List<Pair<Double, Double>>,
        /** speed (km/h) per track point, same indices as gpsTrack — for color grading */
        val gpsTrackSpeeds: List<Float>,
        val gpsDistanceKm: Double,
        val gpsMaxSpeedKmh: Int,
        val laps: List<LapEntity>,
        val maxLateralG: Float?,
        val maxBrakingG: Float?,
        val maxLeanDeg: Float?,
        /** (gLat, gLong) samples for the friction circle, downsampled */
        val frictionPoints: List<Pair<Float, Float>>,
        /** aligned with gpsTrack — true where braking hard */
        val brakingMask: List<Boolean>,
        /** per-lap peaks, same order as [laps] */
        val lapStats: List<LapStat>,
        /** (throttle %, longitudinal G) pairs matched by timestamp — tuning view */
        val throttleGPoints: List<Pair<Float, Float>>,
    )

    data class LapStat(val maxAbsG: Float?, val maxAbsLean: Float?)

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val report: TripReport) : UiState()
        data class NotFound(val message: String) : UiState()
    }

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { loadReport() }
    }

    private suspend fun loadReport() {
        try {
            val session = sessionDao.getById(sessionId) ?: run {
                _state.value = UiState.NotFound("Sesión no encontrada")
                return
            }
            val rpmPoints = telemetryDao.pointsForSessionAndPid(sessionId, "0C")
            val speedPoints = telemetryDao.pointsForSessionAndPid(sessionId, "0D")
            val gpsPoints = gpsDao.pointsForSession(sessionId)
            val sampledGps = downsampleList(gpsPoints, TRACK_MAX_POINTS)
            val gpsTrack = sampledGps.map { it.latitude to it.longitude }
            val gpsTrackSpeeds = sampledGps.map { it.speedKmh }

            val imuPoints = imuDao.pointsForSession(sessionId)
            val frictionPoints = downsampleList(imuPoints, FRICTION_MAX_POINTS)
                .map { it.gLat to it.gLong }
            val imuTimestamps = imuPoints.map { it.timestamp }
            val brakingMask = sampledGps.map { gps ->
                minGLongAround(imuPoints, imuTimestamps, gps.timestamp) < BRAKING_G_THRESHOLD
            }

            val throttlePoints = downsampleList(
                telemetryDao.pointsForSessionAndPid(sessionId, "11"),
                THROTTLE_G_MAX_POINTS,
            )
            val throttleGPoints = throttlePoints.mapNotNull { t ->
                nearestGLong(imuPoints, imuTimestamps, t.timestamp)
                    ?.let { g -> t.value to g }
            }

            val laps = lapDao.lapsForSession(sessionId)
            val lapStats = laps.map { lap ->
                val from = lap.completedAt - lap.timeMs
                LapStat(
                    maxAbsG = imuDao.maxAbsLateralGBetween(sessionId, from, lap.completedAt),
                    maxAbsLean = imuDao.maxAbsLeanBetween(sessionId, from, lap.completedAt),
                )
            }

            _state.value = UiState.Ready(
                TripReport(
                    session = session,
                    avgSpeedKmh = telemetryDao.avgNonZeroValue(sessionId, "0D") ?: 0f,
                    maxRpm = (telemetryDao.maxValue(sessionId, "0C") ?: 0f).roundToInt(),
                    avgRpm = (telemetryDao.avgNonZeroValue(sessionId, "0C") ?: 0f).roundToInt(),
                    maxCoolantTemp = (telemetryDao.maxValue(sessionId, "05") ?: 0f).roundToInt(),
                    maxThrottlePct = (telemetryDao.maxValue(sessionId, "11") ?: 0f).roundToInt(),
                    totalPoints = telemetryDao.countForSession(sessionId),
                    rpmSeries = downsample(rpmPoints),
                    speedSeries = downsample(speedPoints),
                    gpsTrack = gpsTrack,
                    gpsTrackSpeeds = gpsTrackSpeeds,
                    gpsDistanceKm = TripStatsCalculator.gpsDistanceKm(gpsPoints),
                    gpsMaxSpeedKmh = (gpsDao.maxSpeed(sessionId) ?: 0f).roundToInt(),
                    laps = laps,
                    maxLateralG = imuDao.maxAbsLateralG(sessionId),
                    maxBrakingG = imuDao.maxBrakingG(sessionId)?.let { -it },
                    maxLeanDeg = imuDao.maxAbsLean(sessionId),
                    frictionPoints = frictionPoints,
                    brakingMask = brakingMask,
                    lapStats = lapStats,
                    throttleGPoints = throttleGPoints,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SessionDetail: failed to load report for session $sessionId")
            _state.value = UiState.NotFound("Error cargando el reporte")
        }
    }

    /** Charts don't need thousands of points — keep every Nth so Vico stays fluid. */
    private fun downsample(points: List<TelemetryPointEntity>): List<Float> {
        if (points.size <= CHART_MAX_POINTS) return points.map { it.value }
        val step = points.size / CHART_MAX_POINTS
        return points.filterIndexed { index, _ -> index % step == 0 }.map { it.value }
    }

    /**
     * Writes the full session (telemetry + GPS) as CSV to app cache and returns a
     * shareable content:// Uri. Runs on IO — thousands of rows.
     */
    suspend fun exportCsv(): Uri? = withContext(Dispatchers.IO) {
        try {
            val telemetry = telemetryDao.pointsForSession(sessionId)
            val gps = gpsDao.pointsForSession(sessionId)
            val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "revscope_trip_$sessionId.csv")
            file.bufferedWriter().use { out ->
                out.appendLine("section,timestamp,key,value1,value2")
                telemetry.forEach { p ->
                    out.appendLine("obd,${p.timestamp},${p.pid},${p.value},")
                }
                gps.forEach { p ->
                    out.appendLine("gps,${p.timestamp},${p.latitude},${p.longitude},${p.speedKmh}")
                }
                imuDao.pointsForSession(sessionId).forEach { p ->
                    out.appendLine("imu,${p.timestamp},${p.gLat},${p.gLong},${p.leanDeg}")
                }
            }
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Timber.e(e, "SessionDetail: CSV export failed")
            null
        }
    }

    private fun <T> downsampleList(points: List<T>, max: Int): List<T> {
        if (points.size <= max) return points
        val step = points.size / max
        return points.filterIndexed { index, _ -> index % step == 0 }
    }

    /** Lowest longitudinal G within ±600 ms of [targetMs] — binary search over sorted IMU. */
    private fun minGLongAround(
        imu: List<com.revscope.core.data.db.entities.ImuPointEntity>,
        timestamps: List<Long>,
        targetMs: Long,
    ): Float {
        if (imu.isEmpty()) return 0f
        var index = timestamps.binarySearch(targetMs).let { if (it < 0) -it - 1 else it }
        var minG = 0f
        var i = index
        while (i < imu.size && imu[i].timestamp <= targetMs + BRAKING_WINDOW_MS) {
            if (imu[i].gLong < minG) minG = imu[i].gLong
            i++
        }
        i = index - 1
        while (i >= 0 && imu[i].timestamp >= targetMs - BRAKING_WINDOW_MS) {
            if (imu[i].gLong < minG) minG = imu[i].gLong
            i--
        }
        return minG
    }

    /** Longitudinal G of the IMU sample closest to [targetMs], or null if too far. */
    private fun nearestGLong(
        imu: List<com.revscope.core.data.db.entities.ImuPointEntity>,
        timestamps: List<Long>,
        targetMs: Long,
    ): Float? {
        if (imu.isEmpty()) return null
        val index = timestamps.binarySearch(targetMs).let { if (it < 0) -it - 1 else it }
        val candidates = listOfNotNull(imu.getOrNull(index - 1), imu.getOrNull(index))
        return candidates
            .minByOrNull { kotlin.math.abs(it.timestamp - targetMs) }
            ?.takeIf { kotlin.math.abs(it.timestamp - targetMs) <= NEAREST_IMU_WINDOW_MS }
            ?.gLong
    }

    private companion object {
        const val TRACK_MAX_POINTS = 600
        const val FRICTION_MAX_POINTS = 1_500
        const val THROTTLE_G_MAX_POINTS = 800
        const val BRAKING_G_THRESHOLD = -0.25f
        const val BRAKING_WINDOW_MS = 600L
        const val NEAREST_IMU_WINDOW_MS = 300L
    }
}
