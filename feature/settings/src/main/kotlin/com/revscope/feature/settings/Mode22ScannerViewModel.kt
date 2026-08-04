package com.revscope.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val SCAN_TIMEOUT_MS = 1_200L
private const val WATCH_POLL_INTERVAL_MS = 400L
// El watch es para accionar un control y ver qué DID cambia — 10 min sobran; sin tope,
// olvidarlo en el backstack dejaba un poll de 2.5 Hz sobre el bus indefinidamente.
private const val WATCH_MAX_DURATION_MS = 10 * 60_000L
private const val PROBE_REQUEST = "22 F190"

/**
 * Discovers manufacturer-specific Mode 22 (UDS ReadDataByIdentifier) parameters.
 *
 * Workflow to find e.g. the TVS ride-mode DID:
 *  1. Scan a DID range with the bike on — collects every DID the ECU answers.
 *  2. Watch mode: re-polls all hits continuously. Switch the ride mode on the
 *     handlebar — whichever DID changes value at that moment is the candidate.
 */
@HiltViewModel
class Mode22ScannerViewModel @Inject constructor() : ViewModel() {

    data class ScanHit(
        val did: String,
        val value: String,
        val previousValue: String? = null,
        val changedDuringWatch: Boolean = false,
    )

    sealed class ScannerState {
        object Idle : ScannerState()
        data class Scanning(val current: Int, val total: Int) : ScannerState()
        object Watching : ScannerState()
    }

    data class DidRange(val label: String, val start: Int, val end: Int)

    val presetRanges = listOf(
        DidRange("0100–01FF", 0x0100, 0x01FF),
        DidRange("0200–02FF", 0x0200, 0x02FF),
        DidRange("F000–F0FF", 0xF000, 0xF0FF),
        DidRange("F180–F1FF (info ECU)", 0xF180, 0xF1FF),
        DidRange("F400–F4FF (espejo modo 01)", 0xF400, 0xF4FF),
    )

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val _hits = MutableStateFlow<List<ScanHit>>(emptyList())
    val hits: StateFlow<List<ScanHit>> = _hits.asStateFlow()

    private var scanJob: Job? = null

    // Descubrimiento de módulos
    enum class ProtocolSupport { UNKNOWN, SUPPORTED, UNSUPPORTED }

    private val _protocolSupport = MutableStateFlow(ProtocolSupport.UNKNOWN)
    val protocolSupport: StateFlow<ProtocolSupport> = _protocolSupport.asStateFlow()

    private val _modules = MutableStateFlow<List<ModuleDiscovery.ProbeResult>>(emptyList())
    val modules: StateFlow<List<ModuleDiscovery.ProbeResult>> = _modules.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    // Objetivo actual: null = ECU por defecto (comportamiento de hoy)
    private val _targetHeader = MutableStateFlow<String?>(null)
    val targetHeader: StateFlow<String?> = _targetHeader.asStateFlow()

    fun selectTarget(header: String?) { _targetHeader.value = header }

    fun discoverModules(connectionVm: ConnectionViewModel) {
        scanJob?.cancel()
        _modules.value = emptyList()
        scanJob = viewModelScope.launch {
            _discovering.value = true
            try {
                if (!ModuleDiscovery.isCan11Bit(connectionVm.protocolNumber())) {
                    _protocolSupport.value = ProtocolSupport.UNSUPPORTED
                    return@launch
                }
                _protocolSupport.value = ProtocolSupport.SUPPORTED
                for (candidate in ModuleDiscovery.candidateHeaders()) {
                    val raw = connectionVm.probeModule(candidate.header, PROBE_REQUEST).getOrNull()
                    val result = raw?.let { ModuleDiscovery.interpretProbe(candidate.header, it) }
                    if (result?.present == true) {
                        _modules.value = _modules.value.filterNot { it.requestHeader == candidate.header } + result
                    }
                }
            } finally {
                _discovering.value = false
            }
        }
    }

    fun startScan(connectionVm: ConnectionViewModel, range: DidRange) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val total = range.end - range.start + 1
            for ((index, did) in (range.start..range.end).withIndex()) {
                _state.value = ScannerState.Scanning(index + 1, total)
                val didHex = "%04X".format(did)
                val response = readDid(connectionVm, didHex) ?: continue
                extractDidData(response, didHex)?.let { data ->
                    Timber.i("Mode22Scanner: hit $didHex = $data")
                    _hits.value = _hits.value.filterNot { it.did == didHex } +
                        ScanHit(did = didHex, value = data)
                }
            }
            _state.value = ScannerState.Idle
        }
    }

    fun startWatch(connectionVm: ConnectionViewModel) {
        if (_hits.value.isEmpty()) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.value = ScannerState.Watching
            withTimeoutOrNull(WATCH_MAX_DURATION_MS) {
                while (true) {
                    _hits.value = _hits.value.map { hit ->
                        val data = readDid(connectionVm, hit.did)
                            ?.let { extractDidData(it, hit.did) }
                            ?: return@map hit
                        if (data != hit.value) {
                            Timber.i("Mode22Scanner: DID ${hit.did} changed ${hit.value} → $data")
                            hit.copy(value = data, previousValue = hit.value, changedDuringWatch = true)
                        } else {
                            hit
                        }
                    }
                    delay(WATCH_POLL_INTERVAL_MS)
                }
            }
            _state.value = ScannerState.Idle
        }
    }

    // Con objetivo → probeModule (header custom, atómico); sin objetivo → rawExchange (ECU por defecto, como hoy).
    private suspend fun readDid(connectionVm: ConnectionViewModel, didHex: String): String? {
        val target = _targetHeader.value
        return if (target == null) {
            connectionVm.rawExchange("22 $didHex\r", SCAN_TIMEOUT_MS).getOrNull()
        } else {
            connectionVm.probeModule(target, "22 $didHex", SCAN_TIMEOUT_MS).getOrNull()
        }
    }

    fun stop() {
        scanJob?.cancel()
        _state.value = ScannerState.Idle
    }

    fun clearHits() {
        stop()
        _hits.value = emptyList()
    }

    /** Positive UDS reply is "62" + DID + data; anything else (7F 22 xx, NO DATA) is a miss. */
    private fun extractDidData(raw: String, didHex: String): String? {
        val clean = ResponseParser.cleanResponse(raw)
        val header = "62$didHex"
        val index = clean.indexOf(header)
        if (index == -1) return null
        return clean.substring(index + header.length).takeIf { it.isNotEmpty() }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
