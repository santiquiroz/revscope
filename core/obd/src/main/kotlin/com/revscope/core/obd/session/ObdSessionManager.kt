package com.revscope.core.obd.session

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.LapEntity
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.connection.ClassicBtTransport
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ElmCommandBuilder
import com.revscope.core.obd.protocol.ProtocolNegotiator
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.service.ObdForegroundService
import com.revscope.core.obd.service.TripSummaryNotifier
import com.revscope.core.obd.track.TrackModeEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import com.revscope.core.obd.telemetry.DerivedMetricsEngine
import com.revscope.core.obd.telemetry.LaunchTimerEngine
import com.revscope.core.obd.telemetry.PidScheduler
import com.revscope.core.obd.telemetry.SessionRecorder
import com.revscope.core.obd.telemetry.TripStatsCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * App-wide owner of the OBD connection and telemetry pipeline.
 *
 * Lives at application scope (not ViewModel) so the link survives screen changes,
 * Activity death, and powers surfaces without an Activity at all — Android Auto's
 * CarAppService reads the same [readings] flow the phone dashboard uses.
 */
@Singleton
class ObdSessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val registry: PidRegistry,
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
    private val profileDao: VehicleProfileDao,
    private val lapDao: LapDao,
    private val settings: DataStore<Preferences>,
    private val alertsEngine: AlertsEngine,
    private val trackModeEngine: TrackModeEngine,
    private val tripSummaryNotifier: TripSummaryNotifier,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _readings = MutableStateFlow<Map<String, ObdReading>>(emptyMap())
    val readings: StateFlow<Map<String, ObdReading>> = _readings.asStateFlow()

    private val _lastAdapterAddress = MutableStateFlow<String?>(null)
    val lastAdapterAddress: StateFlow<String?> = _lastAdapterAddress.asStateFlow()

    private val _activeProfile = MutableStateFlow<VehicleProfileEntity?>(null)
    val activeProfile: StateFlow<VehicleProfileEntity?> = _activeProfile.asStateFlow()

    private val _lastReadVin = MutableStateFlow<String?>(null)
    val lastReadVin: StateFlow<String?> = _lastReadVin.asStateFlow()

    /** Active recording session — the foreground service keys the GPS track to this. */
    private val _currentSessionIdFlow = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionIdFlow.asStateFlow()

    private var transport: ClassicBtTransport? = null
    private var telemetryJob: Job? = null
    private var stateJob: Job? = null
    private var reconnectJob: Job? = null
    private var voltageJob: Job? = null
    private var currentDeviceAddress: String? = null
    private val derivedEngine = DerivedMetricsEngine()
    private val engineOffDetector = EngineOffDetector()

    private val launchTimer = LaunchTimerEngine()
    val launchResults = launchTimer.results
    private var bestTo60Ms: Long? = null
    private var bestTo100Ms: Long? = null

    private enum class ConnectMode {
        /** User-initiated: publish every state transition. */
        NORMAL,

        /** App-start auto-connect: failures collapse to Disconnected, never Error. */
        STARTUP,

        /** Background retry after link loss: publish nothing until actually connected. */
        BACKGROUND,
    }

    init {
        scope.launch {
            loadCustomPids()
            loadSavedActiveProfile()
            autoConnectToLastAdapter()
        }
        scope.launch {
            launchTimer.results.collect { onLaunchResult(it) }
        }
        scope.launch {
            trackModeEngine.lapEvents.collect { lap ->
                alertsEngine.announceLap(lap.number, lap.timeMs)
                _currentSessionIdFlow.value?.let { sessionId ->
                    runCatching {
                        lapDao.insert(
                            LapEntity(
                                sessionId = sessionId,
                                lapNumber = lap.number,
                                timeMs = lap.timeMs,
                                completedAt = System.currentTimeMillis(),
                            )
                        )
                    }.onFailure { Timber.w(it, "ObdSessionManager: failed to persist lap") }
                }
            }
        }
    }

    private suspend fun onLaunchResult(result: LaunchTimerEngine.LaunchResult) {
        result.to60Ms?.let { if (it < (bestTo60Ms ?: Long.MAX_VALUE)) bestTo60Ms = it }
        result.to100Ms?.let { if (it < (bestTo100Ms ?: Long.MAX_VALUE)) bestTo100Ms = it }
        alertsEngine.announceLaunch(result.to60Ms, result.to100Ms)
        // Persist immediately so a crash mid-trip doesn't lose the run
        val sessionId = _currentSessionIdFlow.value ?: return
        runCatching {
            sessionDao.getById(sessionId)?.let { session ->
                sessionDao.update(session.copy(best0to60Ms = bestTo60Ms, best0to100Ms = bestTo100Ms))
            }
        }.onFailure { Timber.w(it, "ObdSessionManager: failed to persist launch result") }
    }

    /** Manual profile activation from the profiles screen. Persisted across restarts. */
    fun setActiveProfile(profile: VehicleProfileEntity?) {
        _activeProfile.value = profile
        alertsEngine.setRedlineOverride(profile?.redlineRpm)
        scope.launch {
            runCatching {
                settings.edit { prefs ->
                    if (profile != null) prefs[PreferencesKeys.ACTIVE_PROFILE_ID] = profile.id
                    else prefs.remove(PreferencesKeys.ACTIVE_PROFILE_ID)
                }
            }
        }
    }

    private suspend fun loadSavedActiveProfile() {
        runCatching {
            val id = settings.data.first()[PreferencesKeys.ACTIVE_PROFILE_ID] ?: return
            profileDao.getById(id)?.let {
                _activeProfile.value = it
                alertsEngine.setRedlineOverride(it.redlineRpm)
            }
        }.onFailure { Timber.w(it, "ObdSessionManager: failed to load active profile") }
    }

    /**
     * Reads the VIN (Mode 09 02) and auto-activates the matching profile.
     * Motorcycles often don't implement 09 02 — the previously active profile stays.
     */
    private suspend fun resolveProfileByVin(bt: ClassicBtTransport) {
        val vin = runCatching { bt.exchange(ElmCommandBuilder.readVin(), VIN_TIMEOUT_MS) }
            .getOrNull()
            ?.let { ResponseParser.parseVinResponse(it) }
            ?: run {
                Timber.i("ObdSessionManager: ECU did not return a VIN")
                return
            }
        _lastReadVin.value = vin
        Timber.i("ObdSessionManager: VIN = $vin")
        val match = runCatching { profileDao.getByVin(vin) }.getOrNull() ?: return
        Timber.i("ObdSessionManager: auto-activating profile '${match.name}' by VIN")
        setActiveProfile(match)
    }

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

    fun disconnect() {
        reconnectJob?.cancel()
        ObdForegroundService.stop(appContext)
        scope.launch {
            stopTelemetry()
            transport?.disconnect()
            transport = null
        }
    }

    /**
     * Reads active DTC codes (Mode 03). Serialized through the transport mutex so it
     * never interleaves with active polling.
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

    /** Raw serialized command exchange for diagnostic tooling (Mode 22 scanner). */
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

    // ── Connection internals ─────────────────────────────────────────────────

    private suspend fun loadCustomPids() {
        runCatching {
            settings.data.first()[PreferencesKeys.CUSTOM_PIDS_JSON]
                ?.takeIf { it.isNotBlank() }
                ?.let { registry.addDefinitions(it) }
        }.onFailure { Timber.w(it, "ObdSessionManager: failed to load custom PIDs") }
    }

    private suspend fun autoConnectToLastAdapter() {
        val prefs = runCatching { settings.data.first() }.getOrNull() ?: return
        val address = prefs[PreferencesKeys.ADAPTER_ADDRESS] ?: return
        _lastAdapterAddress.value = address
        if (_connectionState.value != ConnectionState.Disconnected) return
        if (!isBonded(address)) return
        Timber.i("ObdSessionManager: auto-connecting to last adapter $address")
        connect(address, ConnectMode.STARTUP)
    }

    private fun isBonded(address: String): Boolean = try {
        bluetoothAdapter?.bondedDevices?.any { it.address == address } == true
    } catch (_: SecurityException) {
        false
    }

    private fun connect(deviceAddress: String, mode: ConnectMode) {
        scope.launch {
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
                        // Keeps recording + GPS alive with the app backgrounded
                        ObdForegroundService.start(appContext)
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
                .launchIn(scope)

            bt.connect()
        }
    }

    private suspend fun saveLastAdapter(address: String, name: String) {
        _lastAdapterAddress.value = address
        runCatching {
            settings.edit { prefs ->
                prefs[PreferencesKeys.ADAPTER_ADDRESS] = address
                prefs[PreferencesKeys.ADAPTER_NAME] = name
            }
        }.onFailure { Timber.w(it, "ObdSessionManager: failed to persist last adapter") }
    }

    private fun scheduleAutoReconnect(pendingSummarySessionId: Long?) {
        val address = currentDeviceAddress ?: _lastAdapterAddress.value ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            AUTO_RECONNECT_BACKOFF_MS.forEachIndexed { attempt, waitMs ->
                delay(waitMs)
                if (_connectionState.value is ConnectionState.Connected) return@launch
                Timber.i("ObdSessionManager: auto-reconnect attempt ${attempt + 1} to $address")
                connect(address, ConnectMode.BACKGROUND)
            }
            // Give the last attempt time to finish its 12 s connect watchdog
            delay(RECONNECT_FINAL_GRACE_MS)
            if (_connectionState.value !is ConnectionState.Connected) {
                Timber.i("ObdSessionManager: reconnect exhausted — clean shutdown")
                finalShutdown(pendingSummarySessionId)
            }
        }
    }

    /** Best-effort probe that still honors coroutine cancellation. */
    private suspend fun probe(bt: ClassicBtTransport, command: String, timeoutMs: Long): String? =
        try {
            bt.exchange(command, timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /**
     * Ignition-off signature: the ELM adapter still answers (battery-powered)
     * but the ECU is silent. A dead socket falls back to the movement heuristic.
     */
    private suspend fun classifyLinkLoss(bt: ClassicBtTransport?): EngineOffDetector.LinkLossCause {
        if (bt != null) {
            val adapterAnswer = probe(bt, "AT RV\r", VOLTAGE_TIMEOUT_MS)
            if (adapterAnswer != null && parseVoltage(adapterAnswer) != null) {
                val ecuAnswer = probe(bt, "010C\r", PROBE_TIMEOUT_MS)
                val ecuSilent = ecuAnswer == null ||
                    ecuAnswer.contains("NO DATA", ignoreCase = true) ||
                    ecuAnswer.contains("UNABLE", ignoreCase = true) ||
                    ecuAnswer.contains("STOPPED", ignoreCase = true)
                return if (ecuSilent) EngineOffDetector.LinkLossCause.ENGINE_OFF
                else EngineOffDetector.LinkLossCause.LINK_FAULT
            }
        }
        return if (engineOffDetector.movedRecently()) EngineOffDetector.LinkLossCause.LINK_FAULT
        else EngineOffDetector.LinkLossCause.ENGINE_OFF
    }

    /** Stops everything battery-hungry and posts the trip summary. Terminal state. */
    private suspend fun finalShutdown(summarySessionId: Long?) {
        voltageJob?.cancel()
        runCatching { transport?.disconnect() }
        transport = null
        ObdForegroundService.stop(appContext)
        _connectionState.value = ConnectionState.Disconnected
        _readings.value = emptyMap()
        summarySessionId?.let { id ->
            runCatching { sessionDao.getById(id) }.getOrNull()?.let { tripSummaryNotifier.post(it) }
        }
    }

    // ── Telemetry pipeline ───────────────────────────────────────────────────

    private suspend fun startTelemetry(bt: ClassicBtTransport, deviceName: String) {
        val negotiationResult = ProtocolNegotiator(bt).initialize().getOrElse { e ->
            Timber.e(e, "ObdSessionManager: protocol negotiation failed")
            // ECU unreachable right after a BT-level connect — never strand the socket/service
            runCatching { bt.disconnect() }
            transport = null
            ObdForegroundService.stop(appContext)
            _connectionState.value = ConnectionState.Error("ECU init failed: ${e.message}")
            return
        }
        registry.setSupportedPids(negotiationResult.supportedPids)
        alertsEngine.reloadThresholds()
        resolveProfileByVin(bt)

        val sessionId = createSession(deviceName)
        _currentSessionIdFlow.value = sessionId
        bestTo60Ms = null
        bestTo100Ms = null
        launchTimer.reset()
        engineOffDetector.reset()

        startVoltagePolling(bt)

        telemetryJob = scope.launch {
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
                            alertsEngine.process(reading)
                            launchTimer.process(reading)
                            if (reading.pid == "0D") engineOffDetector.onSpeed(reading.value)
                        }
                    }

                    launch {
                        SessionRecorder(telemetryDao).record(sessionId, allFlow)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // PidScheduler's circuit breaker lands here when the ECU stops answering
                Timber.e(e, "ObdSessionManager: telemetry link lost")
                voltageJob?.cancel()
                val closedSessionId = _currentSessionIdFlow.value
                closedSessionId?.let { id -> runCatching { updateSessionEnd(id) } }
                _currentSessionIdFlow.value = null
                // Probe BEFORE dropping the transport — the adapter may still answer
                val cause = classifyLinkLoss(transport)
                runCatching { transport?.disconnect() }
                transport = null
                when (cause) {
                    EngineOffDetector.LinkLossCause.ENGINE_OFF -> {
                        Timber.i("ObdSessionManager: engine off — clean shutdown")
                        finalShutdown(closedSessionId)
                    }
                    EngineOffDetector.LinkLossCause.LINK_FAULT -> {
                        _connectionState.value =
                            ConnectionState.Error("Connection lost — adapter not responding")
                        scheduleAutoReconnect(closedSessionId)
                    }
                }
            }
        }
    }

    /**
     * Polls adapter battery voltage via "AT RV" — answered by the ELM itself, no ECU
     * involved. Emitted as pseudo-PID VBAT so gauges and alerts consume it like any PID.
     */
    private fun startVoltagePolling(bt: ClassicBtTransport) {
        voltageJob?.cancel()
        voltageJob = scope.launch {
            while (true) {
                try {
                    val raw = bt.exchange("AT RV\r", VOLTAGE_TIMEOUT_MS)
                    parseVoltage(raw)?.let { volts ->
                        val reading = ObdReading(pid = VBAT_PID, value = volts, unit = "V")
                        _readings.value = _readings.value + (VBAT_PID to reading)
                        alertsEngine.process(reading)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "ObdSessionManager: voltage poll failed")
                }
                delay(VOLTAGE_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun stopTelemetry() {
        voltageJob?.cancel()
        // Join so SessionRecorder's final NonCancellable flush lands in Room
        // before updateSessionEnd computes the trip aggregates.
        telemetryJob?.cancelAndJoin()
        telemetryJob = null
        _currentSessionIdFlow.value?.let { id -> updateSessionEnd(id) }
        _currentSessionIdFlow.value = null
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

    companion object {
        const val VBAT_PID = "VBAT"

        private const val DTC_TIMEOUT_MS = 5_000L
        private const val VIN_TIMEOUT_MS = 4_000L
        private const val VOLTAGE_TIMEOUT_MS = 2_000L
        private const val VOLTAGE_POLL_INTERVAL_MS = 10_000L
        // 15 s > 12 s connect watchdog, so attempts never overlap; total ≈ 3 min
        private val AUTO_RECONNECT_BACKOFF_MS = listOf(15_000L, 30_000L, 60_000L, 60_000L)
        private const val RECONNECT_FINAL_GRACE_MS = 15_000L
        private const val PROBE_TIMEOUT_MS = 3_000L

        private val DTC_PREFIX = mapOf(0 to 'P', 1 to 'C', 2 to 'B', 3 to 'U')
        private val VOLTAGE_REGEX = Regex("""(\d{1,2}(?:\.\d{1,2})?)V""")

        fun parseVoltage(raw: String): Double? =
            VOLTAGE_REGEX.find(raw.uppercase())?.groupValues?.get(1)?.toDoubleOrNull()

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
