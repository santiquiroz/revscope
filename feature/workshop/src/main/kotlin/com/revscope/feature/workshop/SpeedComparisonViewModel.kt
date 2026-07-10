package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.SpeedDeltaAverager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpeedComparisonViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = sessionManager.connectionState

    val obdSpeedKmh: StateFlow<Double?> = sessionManager.readings
        .map { it["0D"]?.value }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val gpsSpeedKmh: StateFlow<Double?> = sessionManager.readings
        .map { it[ObdSessionManager.GPS_SPEED_PID]?.value }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val averager = SpeedDeltaAverager()

    private val _averageDeltaPercent = MutableStateFlow<Double?>(null)
    val averageDeltaPercent: StateFlow<Double?> = _averageDeltaPercent.asStateFlow()

    init {
        // One sample per GPS fix (~1 Hz), paired with the OBD speed at that same instant —
        // sampling on every 0D tick instead would over-weight the average toward the much
        // faster OBD polling rate.
        viewModelScope.launch {
            gpsSpeedKmh.filterNotNull().distinctUntilChanged().collect(::registerSample)
        }
    }

    fun resetAverage() {
        averager.reset()
        _averageDeltaPercent.value = null
    }

    private fun registerSample(gpsKmh: Double) {
        val obdKmh = obdSpeedKmh.value ?: return
        averager.addSample(obdKmh, gpsKmh)
        _averageDeltaPercent.value = averager.average
    }
}
