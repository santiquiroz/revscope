package com.revscope.core.obd.viewmodel

import android.bluetooth.BluetoothAdapter
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.connection.ClassicBtTransport
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ProtocolNegotiator
import com.revscope.core.obd.telemetry.DerivedMetricsEngine
import com.revscope.core.obd.telemetry.PidScheduler
import com.revscope.core.obd.telemetry.SessionRecorder
import com.revscope.core.obd.telemetry.TripStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val registry: PidRegistry,
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
    private val settings: DataStore<Preferences>,
) : ViewModel() {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableStateFlow<Map<String, ObdReading>>(emptyMap())
    val readings: StateFlow<Map<String, ObdReading>> = _readings.asStateFlow()

    private val _lastAdapterAddress = MutableStateFlow<String?>(null)
    val lastAdapterAddress: StateFlow<String?> = _lastAdapterAddress.asStateFlow()

    private var transport: ClassicBtTransport? = null
    private var telemetryJob: Job? = null
    private var stateJob: Job? = null
    private var reconnectJob: Job? = null
    private var currentDeviceAddress: String? = null
    private var currentSessionId: Long? = null
    private val derivedEngine = DerivedMetricsEngine()

    private enum class ConnectMode {
        /** User-initiated: publish every state transition. */
        NORMAL,

        /** App-start auto-connect: failures collapse to Disconnected, never Error. */
        STARTUP,

        /** Background retry after link loss: publish nothing until actually connected,
         *  so the "Connection lost" screen stays put between attempts. */
        BACKGROUND,
    }

    init {
        viewModelScope.launch {
            loadCustomPids()
            autoConnectToLastAdapter()
        }
    }

    private suspend fun loadCustomPids() {
        runCatching {
            settings.data.first()[PreferencesKeys.CUSTOM_PIDS_JSON]
                ?.takeIf { it.isNotBlank() }
                ?.let { registry.addDefinitions(it) }
        }.onFailure { Timber.w(it, "ConnectionViewModel: failed to load custom PIDs") }
    }

    /**
     * Reconnects to the last successfully used adapter on app start, if it is still
     * bonded. Failures are silent — the user never asked for this attempt.
     */
    private suspend fun autoConnectToLastAdapter() {
        val prefs = runCatching { settings.data.first() }.getOrNull() ?: return
        val address = prefs[PreferencesKeys.ADAPTER_ADDRESS] ?: return
        _lastAdapterAddress.value = address
        if (_connectionState.value != ConnectionState.Disconnected) return
        if (!isBonded(address)) return
        Timber.i("ConnectionViewModel: auto-connecting to last adapter $address")
        connect(address, ConnectMode.STARTUP)
    }

    private fun isBonded(address: String): Boolean = try {
        bluetoothAdapter?.bondedDevices?.any { it.address == address } == true
    } catch (_: SecurityException) {
        false
    }

    /**
     * Applies an externally calibrated gear ratio table to the derived metrics engine.
     * Call this from the intelligence layer once [AdaptiveGearLearner] has converged.
     */
    fun setGearTable(table: List<Pair<Int, Double>>) = derivedEngine.setGearTable(table)

    fun connectToDevice(deviceAddress: String) {
        reconnectJob?.cancel()
        connect(deviceAddress, ConnectMode.NORMAL)
    }

    /** Retries the current (or last persisted) adapter — wired to the error screen's button. */
    fun reconnectToLast() {
        val address = currentDeviceAddress ?: _lastAdapterAddress.value
        if (address != null) connectToDevice(address) else disconnect()
    }

    private fun connect(deviceAddress: String, mode: ConnectMode) {
        viewModelScope.launch {
            stopTelemetry()
            transport?.disconnect()
            currentDeviceAddress = deviceAddress

            val adapter = bluetoothAdapter ?: run {
                if (mode == ConnectMode.NORMAL) {
                    _connectionState.value = ConnectionState.Error("Bluetooth not available")
                }
                return@launch
            }

            val bt = ClassicBtTransport(adapter, deviceAddress)
            transport = bt

            var connectedSeen = false
            stateJob?.cancel()
            stateJob = bt.observeConnectionState()
                .onEach { state ->
                    if (state is ConnectionState.Connected) {
                        connectedSeen = true
                        reconnectJob?.cancel()
                        _connectionState.value = state
                        saveLastAdapter(deviceAddress, state.deviceName)
                        startTelemetry(bt, state.deviceName)
                        return@onEach
                    }
                    when {
                        mode == ConnectMode.BACKGROUND && !connectedSeen -> Unit
                        mode == ConnectMode.STARTUP && state is ConnectionState.Error ->
                            _connectionState.value = ConnectionState.Disconnected
                        else -> _connectionState.value = state
                    }
                }
                .launchIn(viewModelScope)

            bt.connect()
        }
    }

    /**
     * After the circuit breaker trips (bike turned off, adapter out of range), quietly
     * retry the same adapter so telemetry resumes on its own once the bike is back on.
     */
    private fun scheduleAutoReconnect() {
        val address = currentDeviceAddress ?: _lastAdapterAddress.value ?: return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            repeat(AUTO_RECONNECT_MAX_ATTEMPTS) { attempt ->
                delay(AUTO_RECONNECT_INTERVAL_MS)
                if (_connectionState.value is ConnectionState.Connected) return@launch
                Timber.i("ConnectionViewModel: auto-reconnect attempt ${attempt + 1} to $address")
                connect(address, ConnectMode.BACKGROUND)
            }
        }
    }

    private suspend fun saveLastAdapter(address: String, name: String) {
        _lastAdapterAddress.value = address
        runCatching {
            settings.edit { prefs ->
                prefs[PreferencesKeys.ADAPTER_ADDRESS] = address
                prefs[PreferencesKeys.ADAPTER_NAME] = name
            }
        }.onFailure { Timber.w(it, "ConnectionViewModel: failed to persist last adapter") }
    }

    fun disconnect() {
        reconnectJob?.cancel()
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
            try {
                coroutineScope {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // PidScheduler's circuit breaker lands here when the adapter vanishes
                Timber.e(e, "ConnectionViewModel: telemetry link lost")
                currentSessionId?.let { id -> runCatching { updateSessionEnd(id) } }
                currentSessionId = null
                runCatching { transport?.disconnect() }
                transport = null
                _connectionState.value = ConnectionState.Error("Connection lost — adapter not responding")
                scheduleAutoReconnect()
            }
        }
    }

    private suspend fun stopTelemetry() {
        // Join so SessionRecorder's final NonCancellable flush lands in Room
        // before updateSessionEnd computes the trip aggregates.
        telemetryJob?.cancelAndJoin()
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

    /** Closes the session and fills the trip aggregates shown in history/reports. */
    private suspend fun updateSessionEnd(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        val maxRpm = telemetryDao.maxValue(sessionId, "0C") ?: 0f
        val maxSpeed = telemetryDao.maxValue(sessionId, "0D") ?: 0f
        val speedPoints = telemetryDao.pointsForSessionAndPid(sessionId, "0D")
        sessionDao.update(
            session.copy(
                endedAt = System.currentTimeMillis(),
                maxRpm = maxRpm.roundToInt(),
                maxSpeed = maxSpeed.roundToInt(),
                distanceKm = TripStatsCalculator.distanceKm(speedPoints).toFloat(),
            )
        )
    }

    /**
     * Reads active DTC codes (Mode 03) from the ECU.
     * Returns an empty list when "NO DATA" or no codes present.
     * Uses [ClassicBtTransport.exchange] so the request never interleaves with active polling.
     */
    suspend fun readActiveDtc(): Result<List<DtcCode>> {
        val bt = transport ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            Result.success(parseDtcResponse(bt.exchange("03\r", DTC_TIMEOUT_MS), DtcMode.Active))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Raw serialized command exchange for diagnostic tooling (Mode 22 scanner).
     * Goes through the transport mutex so it never interleaves with active polling.
     */
    suspend fun rawExchange(command: String, timeoutMs: Long = DTC_TIMEOUT_MS): Result<String> {
        val bt = transport ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            Result.success(bt.exchange(command, timeoutMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Clears all stored DTCs (Mode 04). */
    suspend fun clearDtcCodes(): Result<Unit> {
        val bt = transport ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            bt.exchange("04\r", DTC_TIMEOUT_MS)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        reconnectJob?.cancel()
        val job = telemetryJob
        telemetryJob = null
        val bt = transport
        transport = null
        val sessionId = currentSessionId
        currentSessionId = null
        // viewModelScope's Job is already cancelled here — NonCancellable is the only
        // way this cleanup actually runs, otherwise the Bluetooth socket leaks.
        viewModelScope.launch(NonCancellable) {
            job?.cancelAndJoin() // let the recorder's final flush finish first
            sessionId?.let { runCatching { updateSessionEnd(it) } }
            bt?.disconnect()
        }
    }

    companion object {
        private const val DTC_TIMEOUT_MS = 5_000L
        // 15 s > 12 s connect watchdog, so attempts never overlap; ~3 min covers a fuel stop
        private const val AUTO_RECONNECT_INTERVAL_MS = 15_000L
        private const val AUTO_RECONNECT_MAX_ATTEMPTS = 12
        private val DTC_PREFIX = mapOf(0 to 'P', 1 to 'C', 2 to 'B', 3 to 'U')

        fun parseDtcResponse(raw: String, mode: DtcMode): List<DtcCode> {
            val hex = raw.filter { it.isLetterOrDigit() }.uppercase()
            // Mode 03 response starts with "43"
            if (!hex.startsWith("43") || hex.length < 4) return emptyList()
            val payload = hex.drop(2)
            return buildList {
                var i = 0
                while (i + 3 < payload.length) {
                    val b1 = payload.substring(i, i + 2).toIntOrNull(16) ?: break
                    val b2 = payload.substring(i + 2, i + 4).toIntOrNull(16) ?: break
                    if (b1 == 0 && b2 == 0) { i += 4; continue }
                    val prefix = DTC_PREFIX[(b1 shr 6) and 0x03] ?: 'P'
                    val d1 = (b1 shr 4) and 0x03
                    val d2 = b1 and 0x0F
                    val d3 = (b2 shr 4) and 0x0F
                    val d4 = b2 and 0x0F
                    add(DtcCode(code = "$prefix$d1$d2$d3$d4", mode = mode))
                    i += 4
                }
            }
        }
    }
}
