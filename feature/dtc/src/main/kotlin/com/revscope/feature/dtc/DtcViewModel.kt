package com.revscope.feature.dtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.ObdReading
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

@HiltViewModel
class DtcViewModel @Inject constructor(
    private val orchestrator: IntelligenceOrchestrator,
) : ViewModel() {

    private val _state = MutableStateFlow<DtcUiState>(DtcUiState.Idle)
    val state: StateFlow<DtcUiState> = _state.asStateFlow()

    fun readDtcCodes(connectionVm: ConnectionViewModel) {
        viewModelScope.launch {
            _state.value = DtcUiState.Reading
            connectionVm.readActiveDtc()
                .onSuccess { codes ->
                    val items = codes.map { DtcCodeUi(dtc = it, explanation = null, isLoadingExplanation = true) }
                    _state.value = DtcUiState.HasCodes(items)
                    fetchExplanations(codes, connectionVm)
                }
                .onFailure { e ->
                    _state.value = DtcUiState.Error(e.message ?: "Error leyendo DTCs")
                }
        }
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
