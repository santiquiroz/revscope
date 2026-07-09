package com.revscope.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

private const val COMPARE_MAX_POINTS = 300

@HiltViewModel
class SessionCompareViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
    private val gpsDao: GpsDao,
    private val imuDao: ImuDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class RunData(
        val session: SessionEntity,
        val avgSpeedKmh: Int,
        val maxAbsG: Float?,
        val maxLean: Float?,
        val speedSeries: List<Float>,
        val track: List<Pair<Double, Double>>,
    )

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val runA: RunData, val runB: RunData) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val idA: Long = checkNotNull(savedStateHandle["sessionA"])
    private val idB: Long = checkNotNull(savedStateHandle["sessionB"])

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val a = loadRun(idA)
                val b = loadRun(idB)
                _state.value = if (a != null && b != null) {
                    UiState.Ready(a, b)
                } else {
                    UiState.Error("Sesión no encontrada")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SessionCompare: load failed")
                _state.value = UiState.Error("Error cargando la comparación")
            }
        }
    }

    private suspend fun loadRun(id: Long): RunData? {
        val session = sessionDao.getById(id) ?: return null
        val speedPoints = telemetryDao.pointsForSessionAndPid(id, "0D")
        val gps = gpsDao.pointsForSession(id)
        return RunData(
            session = session,
            avgSpeedKmh = (telemetryDao.avgNonZeroValue(id, "0D") ?: 0f).roundToInt(),
            maxAbsG = imuDao.maxAbsLateralG(id),
            maxLean = imuDao.maxAbsLean(id),
            speedSeries = downsample(speedPoints.map { it.value }),
            track = downsample(gps.map { it.latitude to it.longitude }),
        )
    }

    private fun <T> downsample(points: List<T>): List<T> {
        if (points.size <= COMPARE_MAX_POINTS) return points
        val step = points.size / COMPARE_MAX_POINTS
        return points.filterIndexed { index, _ -> index % step == 0 }
    }
}
