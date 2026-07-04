package com.revscope.feature.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val HISTORY_SIZE = 60

@HiltViewModel
class SensorViewModel @Inject constructor(
    private val registry: PidRegistry,
) : ViewModel() {

    val availablePids: List<PidDefinition> = registry.allDefinitions()
        .sortedBy { it.priority }

    private val _selectedPid = MutableStateFlow(availablePids.firstOrNull()?.pid ?: "0C")
    val selectedPid: StateFlow<String> = _selectedPid.asStateFlow()

    private val _history = MutableStateFlow<List<ObdReading>>(emptyList())
    val history: StateFlow<List<ObdReading>> = _history.asStateFlow()

    fun selectPid(pid: String) {
        _selectedPid.value = pid
        _history.value = emptyList()
    }

    private var readingsJob: Job? = null

    fun observeReadings(connectionVm: ConnectionViewModel) {
        readingsJob?.cancel()
        readingsJob = viewModelScope.launch {
            connectionVm.readings.collect { map ->
                val pid = _selectedPid.value
                val reading = map[pid] ?: return@collect
                val current = _history.value
                _history.value = if (current.size >= HISTORY_SIZE) {
                    current.drop(1) + reading
                } else {
                    current + reading
                }
            }
        }
    }
}
