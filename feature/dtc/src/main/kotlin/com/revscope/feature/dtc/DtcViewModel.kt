package com.revscope.feature.dtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.FreezeFrame
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
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

    private val _estadoMil = MutableStateFlow<String?>(null)
    val estadoMil: StateFlow<String?> = _estadoMil.asStateFlow()

    fun readDtcCodes(connectionVm: ConnectionViewModel) {
        viewModelScope.launch {
            _state.value = DtcUiState.Reading
            _freezeFrame.value = emptyList()
            _estadoMil.value = null
            connectionVm.leerDtcCompleto(LEASE_OWNER)
                .onSuccess { scan -> mostrarLectura(scan, connectionVm) }
                .onFailure { e -> _state.value = DtcUiState.Error(mensajeDeError(e)) }
        }
    }

    private suspend fun mostrarLectura(scan: DtcScan, connectionVm: ConnectionViewModel) {
        _freezeFrame.value = freezeFrameItems(scan.freezeFrame)
        _estadoMil.value = textoMil(scan)
        val codes = scan.todos
        _state.value = DtcUiState.HasCodes(
            codes.map { DtcCodeUi(dtc = it, explanation = null, isLoadingExplanation = true) },
        )
        fetchExplanations(codes, connectionVm)
    }

    private fun freezeFrameItems(freezeFrame: FreezeFrame?): List<FreezeFrameItem> {
        if (freezeFrame == null) return emptyList()
        val causante = freezeFrame.dtcCausante?.let { FreezeFrameItem("DTC que lo guardó", it) }
        return listOfNotNull(causante) + freezeFrame.valores.map(::freezeFrameItem)
    }

    private fun freezeFrameItem(reading: ObdReading): FreezeFrameItem {
        val label = registry.getDefinition(reading.pid)?.nameEs ?: reading.pid
        val value = if (reading.value % 1.0 == 0.0) "${reading.value.toInt()}" else "%.1f".format(reading.value)
        return FreezeFrameItem(label = label, value = "$value ${reading.unit}")
    }

    private fun textoMil(scan: DtcScan): String? {
        val mil = scan.milEncendida ?: return null
        val luz = if (mil) "Testigo de falla (MIL) encendido" else "Testigo de falla (MIL) apagado"
        return scan.conteoSegunEcu?.let { "$luz · la ECU reporta $it código(s) confirmados" } ?: luz
    }

    private fun mensajeDeError(e: Throwable): String =
        if (e is IllegalStateException && e.message == "Not connected") "Conecta el adaptador primero"
        else e.message ?: "Error leyendo DTCs"

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
        _estadoMil.value = null
    }

    private companion object {
        const val LEASE_OWNER = "ui:dtc"
    }

    private suspend fun fetchExplanations(codes: List<DtcCode>, connectionVm: ConnectionViewModel) {
        val context: List<ObdReading> = connectionVm.readings.value.values
            .map { ObdReading(pid = it.pid, value = it.value, unit = it.unit) }

        codes.forEachIndexed { index, dtc ->
            val explanation = try {
                orchestrator.explainDtc(
                    dtc.code, context,
                    freezeFrame = _freezeFrame.value.map { it.label to it.value },
                ).explanation
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
