package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.protocol.Mode06Parser
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.Mode06MidNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val MODE06_TIMEOUT_MS = 3_000L

@HiltViewModel
class Mode06ViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    data class MidGroup(
        val mid: String,
        val name: String,
        val results: List<Mode06Parser.TestResult>,
    )

    sealed interface UiState {
        data object Idle : UiState
        data class Scanning(val current: Int, val total: Int) : UiState

        /** [incomplete] is true when a per-MID read failed mid-scan — [groups] is a partial result. */
        data class Done(val groups: List<MidGroup>, val incomplete: Boolean = false) : UiState
        data class Error(val message: String) : UiState
    }

    /** Mirrors [HealthCheckViewModel]'s DtcScanResult honesty pattern for a partial Mode 06 scan. */
    private data class FetchGroupsResult(val groups: List<MidGroup>, val incomplete: Boolean)

    val connectionState: StateFlow<ConnectionState> = sessionManager.connectionState

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun runScan() {
        if (_state.value is UiState.Scanning) return
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            _state.value = UiState.Error("Conecta el adaptador primero")
            return
        }
        viewModelScope.launch {
            try {
                val mids = fetchSupportedMids()
                if (mids.isEmpty()) {
                    _state.value = UiState.Error("El vehículo no reporta resultados Mode 06 soportados")
                    return@launch
                }
                val result = fetchGroups(mids)
                _state.value = UiState.Done(result.groups, result.incomplete)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Mode06: scan failed")
                _state.value = UiState.Error("Falló la lectura: ${e.message}")
            }
        }
    }

    private suspend fun fetchSupportedMids(): List<String> {
        _state.value = UiState.Scanning(0, 1)
        val response = sessionManager.rawExchange("06 00\r", MODE06_TIMEOUT_MS).getOrNull() ?: return emptyList()
        return Mode06Parser.parseSupportedMids(response).sorted()
    }

    private suspend fun fetchGroups(mids: List<String>): FetchGroupsResult {
        val groups = mutableListOf<MidGroup>()
        var incomplete = false
        mids.forEachIndexed { index, mid ->
            _state.value = UiState.Scanning(index + 1, mids.size)
            val results = fetchResultsForMid(mid)
            if (results == null) {
                incomplete = true
            } else if (results.isNotEmpty()) {
                groups += MidGroup(mid, Mode06MidNames.nameFor(mid), results)
            }
        }
        return FetchGroupsResult(groups, incomplete)
    }

    /** Null means the read itself failed (lost link) — distinct from an empty-but-successful read. */
    private suspend fun fetchResultsForMid(mid: String): List<Mode06Parser.TestResult>? {
        val response = sessionManager.rawExchange("06 $mid\r", MODE06_TIMEOUT_MS).getOrNull() ?: return null
        return Mode06Parser.parseTestResults(response)
    }
}
