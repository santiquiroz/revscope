package com.revscope.feature.dtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DtcUiState {
    object Idle : DtcUiState()
    object Reading : DtcUiState()
    object Clearing : DtcUiState()
    data class HasCodes(val codes: List<DtcCodeUi>) : DtcUiState()
    object Cleared : DtcUiState()
    data class Error(val message: String) : DtcUiState()
}

data class DtcCodeUi(
    val dtc: DtcCode,
    val explanation: String?,
    val isLoadingExplanation: Boolean = false,
)

data class FreezeFrameItem(val label: String, val value: String)

@HiltViewModel
class DtcViewModel @Inject constructor(
    private val orchestrator: IntelligenceOrchestrator,
    private val registry: PidRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow<DtcUiState>(DtcUiState.Idle)
    val state: StateFlow<DtcUiState> = _state.asStateFlow()

    private val _freezeFrame = MutableStateFlow<List<FreezeFrameItem>>(emptyList())
    val freezeFrame: StateFlow<List<FreezeFrameItem>> = _freezeFrame.asStateFlow()

    fun readDtcCodes(connectionVm: ConnectionViewModel) {
        viewModelScope.launch {
            _state.value = DtcUiState.Reading
            _freezeFrame.value = emptyList()
            connectionVm.readActiveDtc()
                .onSuccess { codes ->
                    val items = codes.map { DtcCodeUi(dtc = it, explanation = null, isLoadingExplanation = true) }
                    _state.value = DtcUiState.HasCodes(items)
                    if (codes.isNotEmpty()) readFreezeFrame(connectionVm)
                    fetchExplanations(codes, connectionVm)
                }
                .onFailure { e ->
                    _state.value = DtcUiState.Error(e.message ?: "Error leyendo DTCs")
                }
        }
    }

    /** Mode 02: sensor snapshot the ECU stored at the instant the DTC was set. */
    private suspend fun readFreezeFrame(connectionVm: ConnectionViewModel) {
        val items = mutableListOf<FreezeFrameItem>()
        FREEZE_FRAME_PIDS.forEach { pid ->
            val def = registry.getDefinition(pid) ?: return@forEach
            val raw = connectionVm.rawExchange("02 $pid 00\r", 3_000).getOrNull() ?: return@forEach
            val bytes = ResponseParser.parseFreezeFramePid(raw, pid) ?: return@forEach
            registry.evaluate(pid, bytes)?.let { reading ->
                items += FreezeFrameItem(
                    label = def.nameEs,
                    value = "${if (reading.value % 1.0 == 0.0) reading.value.toInt() else "%.1f".format(reading.value)} ${reading.unit}",
                )
            }
        }
        _freezeFrame.value = items
    }

    fun clearDtcCodes(connectionVm: ConnectionViewModel) {
        viewModelScope.launch {
            _state.value = DtcUiState.Clearing
            connectionVm.clearDtcCodes()
                .onSuccess { _state.value = DtcUiState.Cleared }
                .onFailure { e -> _state.value = DtcUiState.Error(e.message ?: "Error borrando DTCs") }
        }
    }

    fun reset() {
        _state.value = DtcUiState.Idle
        _freezeFrame.value = emptyList()
    }

    private companion object {
        // RPM, speed, coolant, load, throttle — the "what was the engine doing" set
        val FREEZE_FRAME_PIDS = listOf("0C", "0D", "05", "04", "11")
    }

    private suspend fun fetchExplanations(codes: List<DtcCode>, connectionVm: ConnectionViewModel) {
        val context: List<ObdReading> = connectionVm.readings.value.values
            .map { ObdReading(pid = it.pid, value = it.value, unit = it.unit) }

        codes.forEachIndexed { index, dtc ->
            val explanation = try {
                orchestrator.explainDtc(dtc.code, context).explanation
            } catch (_: Exception) {
                null
            }
            val current = (_state.value as? DtcUiState.HasCodes)?.codes ?: return
            val updated = current.mapIndexed { i, item ->
                if (i == index) item.copy(explanation = explanation, isLoadingExplanation = false)
                else item
            }
            _state.value = DtcUiState.HasCodes(updated)
        }
    }
}
