package com.revscope.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
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
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
    private val gpsDao: GpsDao,
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
        val gpsDistanceKm: Double,
        val gpsMaxSpeedKmh: Int,
    )

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
            val gpsTrack = downsampleTrack(gpsPoints.map { it.latitude to it.longitude })

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
                    gpsDistanceKm = TripStatsCalculator.gpsDistanceKm(gpsPoints),
                    gpsMaxSpeedKmh = (gpsDao.maxSpeed(sessionId) ?: 0f).roundToInt(),
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

    private fun downsampleTrack(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.size <= TRACK_MAX_POINTS) return points
        val step = points.size / TRACK_MAX_POINTS
        return points.filterIndexed { index, _ -> index % step == 0 }
    }

    private companion object {
        const val TRACK_MAX_POINTS = 600
    }
}
