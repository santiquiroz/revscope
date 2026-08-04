package com.revscope.feature.workshop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.export.CsvShare
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.O2SwitchCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val READINGS_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Sliding window: last 60 s of samples, hard-capped so a stalled clock can't grow it unbounded. */
private const val WINDOW_MS = 60_000L

// A healthy narrowband O2 sensor switches ~1 Hz — the shared scheduler's priority-3 slot
// (PID 14, every 2000 ms) aliases that into a false flatline. This screen runs its own
// fast sampler instead; the transport mutex serializes it with the normal scheduler so
// the two never interleave on the wire.
private const val FAST_SAMPLE_INTERVAL_MS = 180L
private const val FAST_SAMPLE_TIMEOUT_MS = 1_500L

// 60 s window / 180 ms cadence = ~334 samples, rounded up so normal operation never trims
// below the advertised 60 s — this is purely the stalled-clock safety cap, not the steady-state size.
private const val MAX_SAMPLES = 334

internal const val DEFAULT_O2_PID = "14"

/** B1S1, B1S2, B2S1, B2S2 — PidScheduler gates 15/18/19 behind workshop mode; 14 always polls. */
internal val O2_SENSOR_PIDS = listOf("14", "15", "18", "19")

@HiltViewModel
class O2WaveViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val registry: PidRegistry,
) : ViewModel() {

    private val _selectedPid = MutableStateFlow(DEFAULT_O2_PID)
    val selectedPid: StateFlow<String> = _selectedPid.asStateFlow()

    private val _samples = MutableStateFlow<List<ObdReading>>(emptyList())
    val samples: StateFlow<List<ObdReading>> = _samples.asStateFlow()

    // Sensor discovery for the selector still rides the shared scheduler: PID 15/18/19
    // only ever populate `readings` while workshop mode is on (see setWorkshopMode below).
    val availableSensors: StateFlow<List<String>> = sessionManager.readings
        .map { readings -> O2_SENSOR_PIDS.filter { readings.containsKey(it) } }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(READINGS_SUBSCRIPTION_TIMEOUT_MS),
            listOf(DEFAULT_O2_PID),
        )

    private var fastSamplerJob: Job? = null

    // El sampler extra de 5.5 Hz vive atado a la visibilidad de la pantalla (DisposableEffect
    // llama setWorkshopMode) — antes arrancaba en init y seguía martillando el bus con la
    // pantalla en el backstack hasta que el ViewModel muriera.
    fun setWorkshopMode(enabled: Boolean) {
        sessionManager.setWorkshopMode(enabled)
        if (enabled) {
            restartFastSampler(_selectedPid.value)
        } else {
            fastSamplerJob?.cancel()
            fastSamplerJob = null
        }
    }

    fun selectSensor(pid: String) {
        if (pid == _selectedPid.value) return
        _selectedPid.value = pid
        _samples.value = emptyList()
        restartFastSampler(pid)
    }

    fun crossingsPerMinute(): Double =
        O2SwitchCounter.perMinute(_samples.value.map { it.timestamp to it.value })

    fun exportWindow(context: Context) {
        val window = _samples.value
        if (window.isEmpty()) return
        val startMs = window.first().timestamp
        val crossings = crossingsPerMinute()
        viewModelScope.launch {
            CsvShare.shareCsv(
                context = context,
                tipo = "o2wave-${_selectedPid.value}",
                header = listOf("epoch_ms", "segundos_relativos", "voltios"),
                rows = window.asSequence().map { sample ->
                    listOf(sample.timestamp, (sample.timestamp - startMs) / 1000.0, sample.value)
                },
                comment = "cruces_por_minuto=%.0f".format(crossings),
            )
        }
    }

    override fun onCleared() {
        fastSamplerJob?.cancel()
        super.onCleared()
    }

    private fun restartFastSampler(pid: String) {
        fastSamplerJob?.cancel()
        fastSamplerJob = viewModelScope.launch { runFastSampler(pid) }
    }

    private suspend fun runFastSampler(pid: String) {
        while (true) {
            sampleOnce(pid)
            delay(FAST_SAMPLE_INTERVAL_MS)
        }
    }

    private suspend fun sampleOnce(pid: String) {
        try {
            val raw = sessionManager.rawExchange("01 $pid\r", FAST_SAMPLE_TIMEOUT_MS).getOrNull() ?: return
            registry.parseAndEvaluate(pid, raw)?.let(::appendSample)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "O2WaveViewModel: fast sample failed for PID $pid")
        }
    }

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
