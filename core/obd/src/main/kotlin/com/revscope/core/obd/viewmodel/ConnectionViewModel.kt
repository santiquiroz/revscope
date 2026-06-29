package com.revscope.core.obd.viewmodel

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.connection.ClassicBtTransport
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ProtocolNegotiator
import com.revscope.core.obd.telemetry.DerivedMetricsEngine
import com.revscope.core.obd.telemetry.PidScheduler
import com.revscope.core.obd.telemetry.SessionRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val registry: PidRegistry,
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
) : ViewModel() {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableStateFlow<Map<String, ObdReading>>(emptyMap())
    val readings: StateFlow<Map<String, ObdReading>> = _readings.asStateFlow()

    private var transport: ClassicBtTransport? = null
    private var telemetryJob: Job? = null
    private var currentSessionId: Long? = null
    private val derivedEngine = DerivedMetricsEngine()

    /**
     * Applies an externally calibrated gear ratio table to the derived metrics engine.
     * Call this from the intelligence layer once [AdaptiveGearLearner] has converged.
     */
    fun setGearTable(table: List<Pair<Int, Double>>) = derivedEngine.setGearTable(table)

    fun connectToDevice(deviceAddress: String) {
        viewModelScope.launch {
            stopTelemetry()
            transport?.disconnect()

            val adapter = bluetoothAdapter ?: run {
                _connectionState.value = ConnectionState.Error("Bluetooth not available")
                return@launch
            }

            val bt = ClassicBtTransport(adapter, deviceAddress)
            transport = bt

            bt.observeConnectionState()
                .onEach { state ->
                    _connectionState.value = state
                    if (state is ConnectionState.Connected) {
                        startTelemetry(bt, state.deviceName)
                    }
                }
                .launchIn(viewModelScope)

            bt.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            stopTelemetry()
            transport?.disconnect()
            transport = null
        }
    }

    private suspend fun startTelemetry(bt: ClassicBtTransport, deviceName: String) {
        val negotiationResult = ProtocolNegotiator(bt).initialize().getOrElse { e ->
            Timber.e(e, "ConnectionViewModel: protocol negotiation failed")
            _connectionState.value = ConnectionState.Error("ECU init failed: ${e.message}")
            return
        }
        registry.setSupportedPids(negotiationResult.supportedPids)

        val sessionId = createSession(deviceName)
        currentSessionId = sessionId

        telemetryJob = viewModelScope.launch {
            val rawFlow = PidScheduler(bt, registry)
                .observeReadings()
                .shareIn(this, SharingStarted.Eagerly, replay = 0)

            val derivedFlow = derivedEngine.observeDerived(rawFlow)

            val allFlow = merge(rawFlow, derivedFlow)
                .shareIn(this, SharingStarted.Eagerly, replay = 0)

            launch {
                allFlow.collect { reading ->
                    _readings.value = _readings.value + (reading.pid to reading)
                }
            }

            launch {
                SessionRecorder(telemetryDao).record(sessionId, allFlow)
            }
        }
    }

    private suspend fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
        currentSessionId?.let { id -> updateSessionEnd(id) }
        currentSessionId = null
    }

    private suspend fun createSession(deviceName: String): Long =
        sessionDao.insert(
            SessionEntity(
                vehicleProfileId = 0L,
                startedAt = System.currentTimeMillis(),
                endedAt = null,
                adapterName = deviceName,
                maxRpm = 0,
                maxSpeed = 0,
                distanceKm = 0f,
            )
        )

    private suspend fun updateSessionEnd(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(endedAt = System.currentTimeMillis()))
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            stopTelemetry()
            transport?.disconnect()
        }
    }
}
