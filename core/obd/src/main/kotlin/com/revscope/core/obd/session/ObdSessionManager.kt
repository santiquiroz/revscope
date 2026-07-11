package com.revscope.core.obd.session

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.ImuDao
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
import com.revscope.core.obd.workshop.OdometerChecker
import com.revscope.core.obd.workshop.OdometerHistoryStore
import com.revscope.core.obd.workshop.OdometerVerifier
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
import java.util.concurrent.atomic.AtomicInteger

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
    private val imuDao: ImuDao,
    private val gpsDao: GpsDao,
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

    /** True while a GPS-only trip (no adapter) is recording — see [startGpsSession]. */
    private val _gpsSessionActive = MutableStateFlow(false)
    val isGpsSessionActive: StateFlow<Boolean> = _gpsSessionActive.asStateFlow()

    /**
     * Whether the connected ECU supports PID 01 A6 (odometer) — null while unknown
     * (not connected yet, or negotiation hasn't finished the one-shot check). See
     * [checkOdometerOnce].
     */
    private val _odometerSupported = MutableStateFlow<Boolean?>(null)
    val odometerSupported: StateFlow<Boolean?> = _odometerSupported.asStateFlow()

    private var transport: ClassicBtTransport? = null
    private var telemetryJob: Job? = null
    private var stateJob: Job? = null
    private var reconnectJob: Job? = null
    private var gpsInactivityJob: Job? = null
    private var gpsSessionStartedAt: Long = 0L
    private var currentDeviceAddress: String? = null
    private val derivedEngine = DerivedMetricsEngine()
    private val engineOffDetector = EngineOffDetector()
    private val voltagePoller = VoltagePoller()
    private val milWatcher = MilWatcher(alertsEngine)
    private val sessionAggregator = SessionAggregator(sessionDao, telemetryDao, imuDao, settings, gpsDao)
    private val odometerHistoryStore = OdometerHistoryStore(settings)
    private val odometerChecker = OdometerChecker(registry, odometerHistoryStore, sessionDao)
    val odometerCheck: StateFlow<OdometerChecker.Result?> = odometerChecker.lastResult
    private var activeScheduler: PidScheduler? = null
    private val workshopClients = AtomicInteger(0)

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
        val resolvedProfile = withCurrentAdapterLinked(profile)
        _activeProfile.value = resolvedProfile
        alertsEngine.setRedlineOverride(resolvedProfile?.redlineRpm)
        scope.launch {
            persistAdapterLinkIfChanged(profile, resolvedProfile)
            runCatching {
                settings.edit { prefs ->
                    if (resolvedProfile != null) prefs[PreferencesKeys.ACTIVE_PROFILE_ID] = resolvedProfile.id
                    else prefs.remove(PreferencesKeys.ACTIVE_PROFILE_ID)
                }
            }
        }
    }

    /** Refreshes the in-memory active profile after an external DB update. */
    fun notifyProfileUpdated(profile: VehicleProfileEntity) {
        if (_activeProfile.value?.id == profile.id) {
            _activeProfile.value = profile
            alertsEngine.setRedlineOverride(profile.redlineRpm)
        }
    }

    /** Stamps the profile with the currently connected adapter, if one is live. */
    private fun withCurrentAdapterLinked(profile: VehicleProfileEntity?): VehicleProfileEntity? {
        if (profile == null || !hasActiveConnection()) return profile
        if (profile.adapterAddress == currentDeviceAddress) return profile
        return profile.copy(adapterAddress = currentDeviceAddress)
    }

    private fun hasActiveConnection(): Boolean =
        currentDeviceAddress != null && _connectionState.value is ConnectionState.Connected

    private suspend fun persistAdapterLinkIfChanged(
        original: VehicleProfileEntity?,
        resolved: VehicleProfileEntity?,
    ) {
        if (resolved == null || resolved.adapterAddress == original?.adapterAddress) return
        runCatching {
            resolved.adapterAddress?.let {
                profileDao.clearAdapterLinkExcept(it, resolved.id)
            }
            profileDao.update(resolved)
        }
            .onFailure { Timber.w(it, "ObdSessionManager: failed to persist profile adapter link") }
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

    /**
     * One-shot odometer read+compare (PID 01 A6) right after negotiation — see
     * [OdometerChecker]. A no-op (near-instant) when the ECU doesn't support the PID;
     * a real ~200ms-5s roundtrip only on vehicles that do.
     */
    private suspend fun checkOdometerOnce(bt: ClassicBtTransport) {
        _odometerSupported.value = odometerChecker.isSupported()
        val profileId = _activeProfile.value?.id ?: return
        runCatching { odometerChecker.check(bt, profileId) }
            .onFailure { Timber.w(it, "ObdSessionManager: odometer check failed") }
    }

    /** Falls back to the adapter's last-linked profile when VIN resolution found none. */
    private suspend fun activateProfileByAdapter() {
        val address = currentDeviceAddress ?: return
        runCatching { profileDao.getByAdapter(address) }
            .onSuccess { match ->
                if (match != null) {
                    Timber.i("ObdSessionManager: auto-activating profile '${match.name}' by adapter")
                    setActiveProfile(match)
                }
            }
            .onFailure { Timber.w(it, "ObdSessionManager: failed to auto-activate profile by adapter") }
    }

    fun setGearTable(table: List<Pair<Int, Double>>) = derivedEngine.setGearTable(table)

    fun setWorkshopMode(enabled: Boolean) {
        val clients = if (enabled) workshopClients.incrementAndGet()
        else workshopClients.updateAndGet { (it - 1).coerceAtLeast(0) }
        activeScheduler?.setWorkshopMode(clients > 0)
    }

    fun connectToDevice(deviceAddress: String) {
        reconnectJob?.cancel()
        // A live GPS trip must not keep "owning" currentSessionId once an adapter shows up —
        // close its bookkeeping here; connect()'s own stopTelemetry() below is then a no-op
        // for it (currentSessionId already null) and starts the OBD session cleanly.
        if (_gpsSessionActive.value) stopGpsSessionInternal(alsoStopService = false)
        connect(deviceAddress, ConnectMode.NORMAL)
    }

    /**
     * Starts a GPS-only recording session (no adapter) — a path parallel to [connect]/
     * [startTelemetry] that never touches them. Setting [_currentSessionIdFlow] is what
     * already makes [ObdForegroundService] start GPS+IMU recording, road alerters and
     * crash detection for any session, OBD or GPS alike.
     */
    fun startGpsSession() {
        if (_currentSessionIdFlow.value != null) return
        if (_gpsSessionActive.value) return
        val state = _connectionState.value
        if (state is ConnectionState.Connecting || state is ConnectionState.Connected) return
        // User pressing "Iniciar viaje GPS" while Error+reconnectJob is pending means
        // they chose GPS over the dead OBD link — cancel the background reconnect loop
        // immediately (non-suspend) so it can't schedule another attempt, then tear down
        // the stale transport before this GPS session starts (see connect()'s Connected
        // branch for the defense-in-depth backstop if an attempt was already in flight).
        val hadPendingReconnect = state is ConnectionState.Error
        if (hadPendingReconnect) {
            reconnectJob?.cancel()
            reconnectJob = null
        }
        _gpsSessionActive.value = true
        engineOffDetector.reset()
        gpsSessionStartedAt = System.currentTimeMillis()
        scope.launch {
            if (hadPendingReconnect) {
                runCatching { transport?.disconnect() }
                transport = null
                _connectionState.value = ConnectionState.Disconnected
            }
            val sessionId = createSession(GPS_ADAPTER_NAME)
            // The flag can flip back to false while createSession() was suspended
            // (e.g. connectToDevice() racing in) — don't resurrect a cancelled trip.
            if (!_gpsSessionActive.value) {
                runCatching {
                    sessionDao.getById(sessionId)?.let {
                        sessionDao.update(it.copy(endedAt = System.currentTimeMillis()))
                    }
                }.onFailure { Timber.w(it, "ObdSessionManager: failed to close orphaned GPS session") }
                return@launch
            }
            _currentSessionIdFlow.value = sessionId
            ObdForegroundService.start(appContext)
            startGpsInactivityWatcher()
        }
    }

    /** Ends the active GPS trip — user-initiated close, wired to the Dashboard button. */
    fun stopGpsSession() = stopGpsSessionInternal(alsoStopService = true)

    /**
     * [alsoStopService] is false when called from [connectToDevice]: the service must stay
     * up because [connect] is about to restart it for the incoming OBD session — issuing a
     * shutdown request here would race with that restart.
     */
    private fun stopGpsSessionInternal(alsoStopService: Boolean) {
        if (!_gpsSessionActive.value) return
        _gpsSessionActive.value = false
        stopGpsInactivityWatcher()
        val sessionId = _currentSessionIdFlow.value
        _readings.value = _readings.value - GPS_SPEED_PID
        _currentSessionIdFlow.value = null
        if (alsoStopService) ObdForegroundService.requestShutdown(appContext)
        scope.launch {
            sessionId?.let { id ->
                // GpsTrackRecorder's final flush runs off this same currentSessionId
                // transition inside the service — give that Room write a moment to land
                // before aggregating (the manager has no join handle into it).
                delay(GPS_STOP_FLUSH_GRACE_MS)
                updateSessionEnd(id)
                runCatching { sessionDao.getById(id) }.getOrNull()?.let { tripSummaryNotifier.post(it) }
            }
        }
    }

    /**
     * Feeds the live GPS_SPEED pseudo-reading — wired by the service unconditionally, in
     * both OBD and GPS-only trip modes, so the dashboard's speed-source toggle and the
     * speedometer comparison screen can read it alongside PID 0D. Deliberately does NOT
     * feed [launchTimer] or [alertsEngine]: 1 Hz GPS fixes are too coarse for a 0-100
     * timer, and custom-PID alert rules are scoped to real OBD PIDs — launch timing stays
     * OBD-only in v1. Only feeds [engineOffDetector] during a GPS-only trip — in OBD mode
     * PID 0D already does that (see startTelemetry's allFlow.collect), so feeding both
     * here too would double-count every tick.
     */
    fun publishGpsSpeed(kmh: Float) {
        val reading = ObdReading(GPS_SPEED_PID, kmh.toDouble(), "km/h")
        _readings.value = _readings.value + (GPS_SPEED_PID to reading)
        if (_gpsSessionActive.value) engineOffDetector.onSpeed(kmh.toDouble())
    }

    private fun startGpsInactivityWatcher() {
        gpsInactivityJob?.cancel()
        gpsInactivityJob = scope.launch {
            while (true) {
                delay(GPS_INACTIVITY_CHECK_INTERVAL_MS)
                if (isGpsTripIdle()) {
                    Timber.i("ObdSessionManager: no GPS movement for ${GPS_INACTIVITY_WINDOW_MS}ms — auto-closing trip")
                    stopGpsSession()
                    break
                }
            }
        }
    }

    private fun stopGpsInactivityWatcher() {
        gpsInactivityJob?.cancel()
        gpsInactivityJob = null
    }

    private fun isGpsTripIdle(): Boolean {
        val referenceTs = engineOffDetector.lastMovementTimestamp() ?: gpsSessionStartedAt
        return System.currentTimeMillis() - referenceTs >= GPS_INACTIVITY_WINDOW_MS
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

    /** Sondeo de solo lectura a un módulo por su header 11-bit. Result.failure = sin respuesta. */
    suspend fun probeModule(
        requestHeader: String,
        request: String,
        timeoutMs: Long = MODULE_PROBE_TIMEOUT_MS,
    ): Result<String> {
        val bt = transport ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            Result.success(bt.targetedExchange(requestHeader, request, timeoutMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Número de protocolo OBD actual (AT DPN). "6"/"8" = CAN 11-bit; "A6" = auto→6. Null si falla. */
    suspend fun currentProtocolNumber(): String? {
        val bt = transport ?: return null
        return try {
            ResponseParser.cleanResponse(bt.exchange("AT DPN\r", 1_500L)).takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Manual "Leer ahora" — same read+compare+persist pipeline as the automatic one-shot check. */
    suspend fun checkOdometerNow(): OdometerChecker.Result? {
        val bt = transport ?: return null
        val profileId = _activeProfile.value?.id ?: return null
        return odometerChecker.check(bt, profileId)
    }

    /** Stored odometer reading history for the given profile — feeds the history list/CSV export. */
    suspend fun odometerHistoryFor(profileId: Long): List<OdometerVerifier.Reading> =
        odometerHistoryStore.historialPara(profileId)

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
                        // Defense in depth: a background reconnect can still win the race
                        // against a user starting a GPS trip during the Error window
                        // (see startGpsSession) — never let an OBD session start on top
                        // of a live GPS one.
                        if (_gpsSessionActive.value) stopGpsSessionInternal(alsoStopService = false)
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
        voltagePoller.stop()
        milWatcher.stop()
        runCatching { transport?.disconnect() }
        transport = null
        activeScheduler = null
        // Not an unconditional stop: the service may still be honoring an active
        // crash-detection grace period (C1) — it decides whether to defer or stop now.
        ObdForegroundService.requestShutdown(appContext)
        _connectionState.value = ConnectionState.Disconnected
        _readings.value = emptyMap()
        _odometerSupported.value = null
        summarySessionId?.let { id ->
            runCatching { sessionDao.getById(id) }.getOrNull()?.let { tripSummaryNotifier.post(it) }
        }
    }

    // ── Telemetry pipeline ───────────────────────────────────────────────────

    private suspend fun startTelemetry(bt: ClassicBtTransport, deviceName: String) {
        _odometerSupported.value = null
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
        alertsEngine.resetSessionFlags()
        resolveProfileByVin(bt)
        if (_activeProfile.value == null) activateProfileByAdapter()
        checkOdometerOnce(bt)

        val sessionId = createSession(deviceName)
        _currentSessionIdFlow.value = sessionId
        bestTo60Ms = null
        bestTo100Ms = null
        launchTimer.reset()
        engineOffDetector.reset()

        voltagePoller.start(scope, bt) { reading ->
            _readings.value = _readings.value + (reading.pid to reading)
            alertsEngine.process(reading)
        }
        milWatcher.start(scope, bt) { reading ->
            _readings.value = _readings.value + (reading.pid to reading)
        }

        telemetryJob = scope.launch {
            try {
                coroutineScope {
                    val scheduler = PidScheduler(bt, registry).also { activeScheduler = it }
                    scheduler.setWorkshopMode(workshopClients.get() > 0)
                    val rawFlow = scheduler
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
                voltagePoller.stop()
                milWatcher.stop()
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

    private suspend fun stopTelemetry() {
        voltagePoller.stop()
        milWatcher.stop()
        // Join so SessionRecorder's final NonCancellable flush lands in Room
        // before updateSessionEnd computes the trip aggregates.
        telemetryJob?.cancelAndJoin()
        telemetryJob = null
        activeScheduler = null
        _currentSessionIdFlow.value?.let { id -> updateSessionEnd(id) }
        _currentSessionIdFlow.value = null
    }

    private suspend fun createSession(deviceName: String): Long =
        sessionDao.insert(
            SessionEntity(
                vehicleProfileId = _activeProfile.value?.id ?: 0L,
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
        sessionAggregator.close(sessionId) { _activeProfile.value }
    }

    companion object {
        const val VBAT_PID = "VBAT"
        const val MIL_PID = "MIL"

        /** Pseudo-PID for the GPS-only trip mode's live speed reading. */
        const val GPS_SPEED_PID = "GPS_SPEED"
        const val GPS_ADAPTER_NAME = "GPS"

        private const val DTC_TIMEOUT_MS = 5_000L
        private const val VIN_TIMEOUT_MS = 4_000L
        private const val VOLTAGE_TIMEOUT_MS = 2_000L
        // 15 s > 12 s connect watchdog, so attempts never overlap; total ≈ 3 min
        private val AUTO_RECONNECT_BACKOFF_MS = listOf(15_000L, 30_000L, 60_000L, 60_000L)
        private const val RECONNECT_FINAL_GRACE_MS = 15_000L
        private const val GPS_INACTIVITY_WINDOW_MS = 4 * 60_000L
        private const val GPS_INACTIVITY_CHECK_INTERVAL_MS = 30_000L
        private const val GPS_STOP_FLUSH_GRACE_MS = 600L
        private const val PROBE_TIMEOUT_MS = 3_000L
        // Timeout de sondeo de módulo — corto: un módulo ausente debe fallar rápido.
        private const val MODULE_PROBE_TIMEOUT_MS = 1_500L

        private val DTC_PREFIX = mapOf(0 to 'P', 1 to 'C', 2 to 'B', 3 to 'U')

        fun parseVoltage(raw: String): Double? = VoltagePoller.parseVoltage(raw)

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
