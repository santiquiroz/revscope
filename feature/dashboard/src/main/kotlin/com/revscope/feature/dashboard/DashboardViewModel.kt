package com.revscope.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    val orchestrator: IntelligenceOrchestrator,
) : ViewModel() {

    val anomalyAlerts = orchestrator.anomalyAlerts
    val tripScore = orchestrator.tripScore

    val gearCalibrated: StateFlow<Boolean> = orchestrator.gearLearner.gearTable
        .map { table -> table.all { it.observationCount >= 30 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var gearTableJob: Job? = null

    /**
     * Wires [readings] into the intelligence orchestrator and feeds calibrated gear
     * ratios back into [connectionVm] once [AdaptiveGearLearner] converges.
     *
     * Safe to call multiple times (e.g. on reconnect) — subsequent calls reset the
     * orchestrator session and replace the previous gear-table collector.
     */
    fun startIntelligence(readings: Flow<ObdReading>, connectionVm: ConnectionViewModel) {
        orchestrator.resetTrip()
        orchestrator.start(readings, viewModelScope)

        gearTableJob?.cancel()
        gearTableJob = viewModelScope.launch {
            orchestrator.gearLearner.gearTable.collect { table ->
                val calibrated = table.all { it.observationCount >= 30 }
                if (calibrated) {
                    connectionVm.setGearTable(orchestrator.gearLearner.toRatioTable())
                }
            }
        }
    }
}
