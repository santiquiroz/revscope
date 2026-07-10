package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.O2SwitchCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val READINGS_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Sliding window: last 60 s of samples, hard-capped so a stalled clock can't grow it unbounded. */
private const val WINDOW_MS = 60_000L
private const val MAX_SAMPLES = 240

internal const val DEFAULT_O2_PID = "14"

/** B1S1, B1S2, B2S1, B2S2 — PidScheduler gates 15/18/19 behind workshop mode; 14 always polls. */
internal val O2_SENSOR_PIDS = listOf("14", "15", "18", "19")

@HiltViewModel
class O2WaveViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    private val _selectedPid = MutableStateFlow(DEFAULT_O2_PID)
    val selectedPid: StateFlow<String> = _selectedPid.asStateFlow()

    private val _samples = MutableStateFlow<List<ObdReading>>(emptyList())
    val samples: StateFlow<List<ObdReading>> = _samples.asStateFlow()

    val availableSensors: StateFlow<List<String>> = sessionManager.readings
        .map { readings -> O2_SENSOR_PIDS.filter { readings.containsKey(it) } }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(READINGS_SUBSCRIPTION_TIMEOUT_MS),
            listOf(DEFAULT_O2_PID),
        )

    init {
        viewModelScope.launch {
            sessionManager.readings
                .map { it[_selectedPid.value] }
                .distinctUntilChanged()
                .collect { reading -> reading?.let(::appendSample) }
        }
    }

    fun setWorkshopMode(enabled: Boolean) = sessionManager.setWorkshopMode(enabled)

    fun selectSensor(pid: String) {
        if (pid == _selectedPid.value) return
        _selectedPid.value = pid
        _samples.value = emptyList()
    }

    fun crossingsPerMinute(): Double =
        O2SwitchCounter.perMinute(_samples.value.map { it.timestamp to it.value })

    private fun appendSample(reading: ObdReading) {
        val current = _samples.value
        if (current.lastOrNull()?.timestamp == reading.timestamp) return
        _samples.value = trimWindow(current + reading)
    }

    private fun trimWindow(samples: List<ObdReading>): List<ObdReading> {
        val newestTimestamp = samples.last().timestamp
        val withinWindow = samples.filter { newestTimestamp - it.timestamp <= WINDOW_MS }
        return if (withinWindow.size > MAX_SAMPLES) withinWindow.takeLast(MAX_SAMPLES) else withinWindow
    }
}
