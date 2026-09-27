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

// Ventana por tiempo, no por cantidad: a 10 Hz 60 muestras eran 6 s y a 0,5 Hz dos minutos.
private const val HISTORY_WINDOW_MS = 30_000L

@HiltViewModel
class SensorViewModel @Inject constructor(
    private val registry: PidRegistry,
) : ViewModel() {

    /** Solo lo que el vehículo soporta (todo antes de conectar); los modos 21/22 no pasan por el bitmap. */
    val availablePids: List<PidDefinition>
        get() = registry.allDefinitions()
            .filter { it.mode != "01" || registry.isSupported(it.pid) }
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
    private var capabilitiesJob: Job? = null

    fun observeReadings(connectionVm: ConnectionViewModel) {
        readingsJob?.cancel()
        readingsJob = viewModelScope.launch {
            connectionVm.readings.collect { map ->
                val pid = _selectedPid.value
                val reading = map[pid] ?: return@collect
                _history.value = appendToWindow(_history.value, reading)
            }
        }
    }

    fun observeCapabilities(connectionVm: ConnectionViewModel) {
        capabilitiesJob?.cancel()
        capabilitiesJob = viewModelScope.launch {
            connectionVm.capacidadesEcu.collect { validarSeleccion() }
        }
    }

    private fun validarSeleccion() {
        val disponible = pidSeleccionadoValido(_selectedPid.value, availablePids) ?: return
        if (disponible == _selectedPid.value) return
        selectPid(disponible)
    }

    private fun appendToWindow(current: List<ObdReading>, reading: ObdReading): List<ObdReading> {
        // El mapa emite con cada PID que cambia: la misma lectura del PID elegido llega repetida.
        if (current.lastOrNull()?.timestamp == reading.timestamp) return current
        val cutoff = reading.timestamp - HISTORY_WINDOW_MS
        return current.dropWhile { it.timestamp < cutoff } + reading
    }
}

internal fun pidSeleccionadoValido(actual: String, disponibles: List<PidDefinition>): String? =
    actual.takeIf { pid -> disponibles.any { it.pid == pid } } ?: disponibles.firstOrNull()?.pid
