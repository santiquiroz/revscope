package com.revscope.core.obd.session

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.common.export.CsvShare
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
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.data.db.entities.vehicleType
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.connection.AdapterType
import com.revscope.core.obd.connection.BleTransport
import com.revscope.core.obd.connection.ClassicBtTransport
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcLectura
import com.revscope.core.obd.diagnostics.DtcReader
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.mcp.McpActivityTracker
import com.revscope.core.obd.protocol.DtcResponseParser
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.protocol.ElmCommandBuilder
import com.revscope.core.obd.protocol.ProtocolInfo
import com.revscope.core.obd.protocol.ProtocolNegotiator
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.service.ObdForegroundService
import com.revscope.core.obd.service.TripSummaryNotifier
import com.revscope.core.obd.track.TrackModeEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import com.revscope.core.obd.telemetry.DerivedMetricsEngine
import com.revscope.core.obd.telemetry.GatedTransport
import com.revscope.core.obd.telemetry.LaunchTimerEngine
import com.revscope.core.obd.telemetry.PidScheduler
import com.revscope.core.obd.telemetry.PollingGate
import com.revscope.core.obd.telemetry.SamplingPreset
import com.revscope.core.obd.telemetry.captura.AndroidLectorDispositivo
import com.revscope.core.obd.telemetry.captura.ArchivoCaptura
import com.revscope.core.obd.telemetry.captura.CapturaRapida
import com.revscope.core.obd.telemetry.captura.EnlaceCaptura
import com.revscope.core.obd.telemetry.captura.InfoAdaptador
import com.revscope.core.obd.telemetry.SessionRecorder
import com.revscope.core.obd.trip.MaintenanceCalculator
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.OdometerAutoSync
import com.revscope.core.obd.workshop.OdometerChecker
import com.revscope.core.obd.workshop.OdometerHistoryStore
import com.revscope.core.obd.workshop.OdometerVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
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
    private val mcpActivity: McpActivityTracker,
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

    private var transport: Transport? = null
    private var telemetryJob: Job? = null
    private var stateJob: Job? = null
    private var reconnectJob: Job? = null
    private var gpsInactivityJob: Job? = null
    private var gpsSessionStartedAt: Long = 0L
    private var currentDeviceAddress: String? = null
    private var currentAdapterType: AdapterType = AdapterType.CLASSIC_BT
    @Volatile private var lastAdapterType: AdapterType = AdapterType.CLASSIC_BT
    private val derivedEngine = DerivedMetricsEngine()
    private val engineOffDetector = EngineOffDetector()
    private val voltagePoller = VoltagePoller()
    private val milWatcher = MilWatcher(alertsEngine)
    private val pollingGate = PollingGate()
    private val diagnosticLease = DiagnosticLease(pollingGate)
    private val dtcReader = DtcReader(registry)
    @Volatile private var protocoloEsCan: Boolean? = null
    @Volatile private var protocoloDpn: String? = null
    @Volatile private var elmVersion: String? = null

    // Muestras de la captura rápida: entran al mismo flujo que el sondeo (gauges, alertas y grabación).
    private val capturaLecturas = MutableSharedFlow<ObdReading>(
        extraBufferCapacity = CAPTURE_READINGS_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val enlaceCaptura = object : EnlaceCaptura {
        override fun transporte(): Transport? = transport
        override fun scopeEnlace(): CoroutineScope? = linkPipeline?.scope
        override fun pausarSondeo(pausado: Boolean) {
            activeScheduler?.setPaused(pausado)
        }
        override fun publicar(reading: ObdReading) {
            capturaLecturas.tryEmit(reading)
        }
        override fun info() = InfoAdaptador(currentDeviceName, elmVersion, protocoloDpn, protocoloEsCan)
    }

    /** Captura rápida (Taller → Sensores y MCP): una a la vez, sobre el enlace vivo. */
    val captura = CapturaRapida(
        enlace = enlaceCaptura,
        gate = pollingGate,
        registry = registry,
        nuevoSumidero = { ArchivoCaptura(CsvShare.exportsDir(appContext)) },
        dispositivo = AndroidLectorDispositivo(appContext),
    )
    private val sessionAggregator = SessionAggregator(sessionDao, telemetryDao, imuDao, settings, gpsDao)
    private val odometerHistoryStore = OdometerHistoryStore(settings)
    private val odometerChecker = OdometerChecker(registry, odometerHistoryStore, sessionDao)
    val odometerCheck: StateFlow<OdometerChecker.Result?> = odometerChecker.lastResult
    private var activeScheduler: PidScheduler? = null
    @Volatile private var linkPipeline: LinkPipeline? = null
    @Volatile private var currentDeviceName: String? = null
    @Volatile private var autoTripEnabled = true
    private val autoTripTrigger = AutoTripTrigger()
    private val tripController = TripController(
        store = { adapterName -> abrirSesionObd(adapterName) },
        closer = { sessionId -> cerrarSesionObd(sessionId) },
        launcher = { sessionId -> lanzarGrabacion(sessionId) },
        publicarSesion = { sessionId -> _currentSessionIdFlow.value = sessionId },
    )

    /** Enlace y viaje por separado: se puede finalizar el viaje sin soltar el adaptador. */
    val estadoViaje: StateFlow<EstadoViaje> = tripController.estado

    /** Por qué se cerró el último viaje OBD — el servicio lo lee al ver el null de [currentSessionId]. */
    val ultimoMotivoFin: StateFlow<MotivoFin?> = tripController.ultimoMotivoFin

    private val _eventosViaje = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val eventosViaje: SharedFlow<String> = _eventosViaje.asSharedFlow()

    /** El scope y el flujo del pipeline vivo: la grabación de cada viaje cuelga de ellos. */
    private class LinkPipeline(val scope: CoroutineScope, val readings: SharedFlow<ObdReading>)
    private val workshopClients = AtomicInteger(0)
    @Volatile private var idleModeEnabled = false
    @Volatile private var remoteViewerActive = false

    private val _muestreoPreset = MutableStateFlow(SamplingPreset.DEFAULT)
    val muestreoPreset: StateFlow<SamplingPreset> = _muestreoPreset.asStateFlow()

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
        scope.launch { observeAutoTripSetting() }
        scope.launch { observeRemoteViewer() }
        scope.launch { observeSamplingPreset() }
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
        alertsEngine.setRedlineOverride(resolvedProfile?.redlineRpm, resolvedProfile?.vehicleType ?: VehicleType.CAR)
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
            alertsEngine.setRedlineOverride(profile.redlineRpm, profile.vehicleType)
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
                alertsEngine.setRedlineOverride(it.redlineRpm, it.vehicleType)
            }
        }.onFailure { Timber.w(it, "ObdSessionManager: failed to load active profile") }
    }

    /**
     * Reads the VIN (Mode 09 02) and auto-activates the matching profile.
     * Motorcycles often don't implement 09 02 — the previously active profile stays.
     */
    private suspend fun resolveProfileByVin(bt: Transport) {
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
    private suspend fun checkOdometerOnce(bt: Transport) {
        _odometerSupported.value = odometerChecker.isSupported()
        val profileId = _activeProfile.value?.id ?: return
        runCatching { odometerChecker.check(bt, profileId) }
            .onSuccess { result -> result?.let { syncOdometerBaseFromEcu(it) } }
            .onFailure { Timber.w(it, "ObdSessionManager: odometer check failed") }
    }

    /**
     * Feeds a trusted ECU odometer reading into the app odometer (odometerBaseKm) so
     * Mantenimiento/Al día/Mecánico IA track the real dashboard value without manual entry.
     * Runs only at connect time (before the session starts recording distance) — syncing
     * mid-trip would double-count the open session's km once it closes.
     */
    private suspend fun syncOdometerBaseFromEcu(result: OdometerChecker.Result) {
        val active = _activeProfile.value ?: return
        try {
            // Re-read the row so a concurrent writer (adapter link, UI edit) isn't clobbered.
            val profile = profileDao.getById(active.id) ?: return
            val sumaSesiones = sessionDao.observeSumDistanceKmForProfile(profile.id).first()
            val odometroApp = MaintenanceCalculator.odometroActual(profile.odometerBaseKm, sumaSesiones)
            val esFalla = result.diagnosis.nivel == DiagnosticRules.Nivel.FALLA
            if (!OdometerAutoSync.debeSincronizar(result.reading.km, odometroApp, sumaSesiones, esFalla)) return
            val actualizado = profile.copy(
                odometerBaseKm = OdometerAutoSync.nuevaBase(result.reading.km, sumaSesiones),
            )
            profileDao.update(actualizado)
            notifyProfileUpdated(actualizado)
            Timber.i("ObdSessionManager: odometer auto-synced from ECU (${result.reading.km} km)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "ObdSessionManager: odometer auto-sync failed")
        }
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

    /** Pantalla apagada: el scheduler estira sus intervalos sin cortar la telemetría. */
    fun setIdleMode(enabled: Boolean) {
        idleModeEnabled = enabled
        activeScheduler?.setIdleMode(enabled)
    }

    fun connectToDevice(deviceAddress: String, type: AdapterType = AdapterType.CLASSIC_BT) {
        reconnectJob?.cancel()
        // A live GPS trip must not keep "owning" currentSessionId once an adapter shows up —
        // close its bookkeeping here; connect()'s own stopTelemetry() below is then a no-op
        // for it (currentSessionId already null) and starts the OBD session cleanly.
        if (_gpsSessionActive.value) stopGpsSessionInternal(alsoStopService = false)
        connect(deviceAddress, ConnectMode.NORMAL, type)
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
        _readings.update { it - GPS_SPEED_PID }
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
        publishReading(reading)
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
        if (address != null) connectToDevice(address, lastAdapterType) else disconnect()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        ObdForegroundService.stop(appContext)
        scope.launch {
            stopTelemetry(MotivoFin.USUARIO)
            transport?.disconnect()
            transport = null
        }
    }

    /**
     * Cierra el viaje OBD en curso y deja el adaptador conectado, el sondeo y las alertas
     * vivos. Devuelve el id cerrado, o null si no había viaje OBD grabando.
     */
    suspend fun finalizarViajeManteniendoEnlace(motivo: MotivoFin = MotivoFin.USUARIO): Long? {
        val sessionId = tripController.finalizar(motivo) ?: return null
        autoTripTrigger.exigirParada()
        sesion(sessionId)?.let { tripSummaryNotifier.post(it) }
        return sessionId
    }

    /** Abre un viaje OBD nuevo sobre el enlace ya conectado. */
    suspend fun iniciarViajeSobreEnlace(): Result<Long> =
        tripController.iniciar(currentDeviceName ?: DEFAULT_ADAPTER_NAME)

    /** Para la UI y la notificación: la operación corre en el scope del manager y sobrevive a la pantalla. */
    fun pedirFinDeViaje(motivo: MotivoFin = MotivoFin.USUARIO) {
        scope.launch { finalizarViajeManteniendoEnlace(motivo) }
    }

    fun pedirInicioDeViaje() {
        scope.launch { iniciarViajeSobreEnlace() }
    }

    fun hasLiveLink(): Boolean = tripController.estado.value != EstadoViaje.SinEnlace

    suspend fun sesion(sessionId: Long): SessionEntity? =
        runCatching { sessionDao.getById(sessionId) }
            .onFailure { Timber.w(it, "ObdSessionManager: failed to read session $sessionId") }
            .getOrNull()

    /**
     * Secuencia de diagnóstico con el sondeo detenido y el adaptador conectado — funciona
     * durante o después de un viaje. Ver [DiagnosticLease].
     */
    suspend fun <T> withDiagnosticLease(
        owner: String,
        timeoutMs: Long = DiagnosticLease.DEFAULT_TIMEOUT_MS,
        block: suspend (Transport) -> T,
    ): Result<T> = diagnosticLease.run(transport, owner, timeoutMs, block)

    /** Reads active DTC codes (Mode 03) under a diagnostic lease. */
    suspend fun readActiveDtc(): Result<List<DtcCode>> =
        withDiagnosticLease("readActiveDtc") { bt ->
            parseDtcResponse(bt.exchange("03\r", DTC_TIMEOUT_MS), DtcMode.Active)
        }

    /** 01 01, 03, 07, 0A y freeze frame con su DTC causante, sin soltar el adaptador ni el viaje. */
    suspend fun leerDtcCompleto(owner: String, opciones: DtcLectura = DtcLectura()): Result<DtcScan> =
        withDiagnosticLease(owner, DTC_FULL_READ_TIMEOUT_MS) { bt -> dtcReader.leer(bt, opciones, protocoloEsCan) }

    /** Modo 04 con relectura de los activos antes y después. */
    suspend fun borrarDtcConRelectura(owner: String): Result<BorradoDtc> =
        withDiagnosticLease(owner, DTC_FULL_READ_TIMEOUT_MS) { bt -> dtcReader.borrar(bt, protocoloEsCan) }

    /** Clears all stored DTCs (Mode 04) under a diagnostic lease. */
    suspend fun clearDtcCodes(): Result<Unit> =
        withDiagnosticLease("clearDtcCodes") { bt ->
            bt.exchange("04\r", DTC_TIMEOUT_MS)
            Unit
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
        val type = AdapterType.from(prefs[PreferencesKeys.ADAPTER_TYPE])
        lastAdapterType = type
        _lastAdapterAddress.value = address
        if (_connectionState.value != ConnectionState.Disconnected) return
        // Los adaptadores BLE normalmente no se emparejan — el check de bonded solo aplica a Classic.
        if (type == AdapterType.CLASSIC_BT && !isBonded(address)) return
        Timber.i("ObdSessionManager: auto-connecting to last adapter $address ($type)")
        connect(address, ConnectMode.STARTUP, type)
    }

    private fun isBonded(address: String): Boolean = try {
        bluetoothAdapter?.bondedDevices?.any { it.address == address } == true
    } catch (_: SecurityException) {
        false
    }

    private fun connect(deviceAddress: String, mode: ConnectMode, type: AdapterType = AdapterType.CLASSIC_BT) {
        scope.launch {
            stopTelemetry(MotivoFin.RECONEXION)
            transport?.disconnect()
            currentDeviceAddress = deviceAddress
            currentAdapterType = type

            val adapter = bluetoothAdapter ?: run {
                if (mode == ConnectMode.NORMAL) {
                    _connectionState.value = ConnectionState.Error("Bluetooth not available")
                }
                return@launch
            }

            val bt: Transport = when (type) {
                AdapterType.CLASSIC_BT -> ClassicBtTransport(adapter, deviceAddress)
                AdapterType.BLE -> BleTransport(appContext, deviceAddress)
            }
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
        lastAdapterType = currentAdapterType
        runCatching {
            settings.edit { prefs ->
                prefs[PreferencesKeys.ADAPTER_ADDRESS] = address
                prefs[PreferencesKeys.ADAPTER_NAME] = name
                prefs[PreferencesKeys.ADAPTER_TYPE] = currentAdapterType.name
                // Configurar un adaptador saca al usuario del modo GPS-only del onboarding.
                prefs[PreferencesKeys.GPS_ONLY_MODE] = false
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
                connect(address, ConnectMode.BACKGROUND, currentAdapterType)
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
    private suspend fun probe(bt: Transport, command: String, timeoutMs: Long): String? =
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
    private suspend fun classifyLinkLoss(bt: Transport?): EngineOffDetector.LinkLossCause {
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

    private suspend fun startTelemetry(bt: Transport, deviceName: String) {
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
        elmVersion = negotiationResult.elmVersion
        protocoloDpn = probe(bt, "AT DPN\r", DPN_TIMEOUT_MS)?.let(ResponseParser::cleanResponse)
        protocoloEsCan = ProtocolInfo.esCan(protocoloDpn)
        alertsEngine.reloadThresholds()
        resolveProfileByVin(bt)
        if (_activeProfile.value == null) activateProfileByAdapter()
        checkOdometerOnce(bt)

        currentDeviceName = deviceName

        val polled = GatedTransport(bt, pollingGate)
        voltagePoller.start(scope, polled) { reading ->
            publishReading(reading)
            alertsEngine.process(reading)
        }
        milWatcher.start(scope, polled) { reading -> publishReading(reading) }

        telemetryJob = scope.launch {
            try {
                coroutineScope {
                    val scheduler = PidScheduler(polled, registry).also { activeScheduler = it }
                    scheduler.setWorkshopMode(workshopClients.get() > 0)
                    scheduler.setIdleMode(idleModeEnabled)
                    scheduler.setRemoteViewerActive(remoteViewerActive)
                    scheduler.setPreset(_muestreoPreset.value)
                    val rawFlow = merge(scheduler.observeReadings(), capturaLecturas)
                        .shareIn(this, SharingStarted.Eagerly, replay = 0)

                    val derivedFlow = derivedEngine.observeDerived(rawFlow)

                    val allFlow = merge(rawFlow, derivedFlow)
                        .shareIn(this, SharingStarted.Eagerly, replay = 0)

                    launch { allFlow.collect { reading -> onLinkReading(reading) } }

                    linkPipeline = LinkPipeline(this, allFlow)
                    tripController.onEnlaceListo(deviceName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // PidScheduler's circuit breaker lands here when the ECU stops answering
                Timber.e(e, "ObdSessionManager: telemetry link lost")
                voltagePoller.stop()
                milWatcher.stop()
                linkPipeline = null
                val closedSessionId = tripController.onEnlacePerdido(MotivoFin.ENLACE_PERDIDO)
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

    private suspend fun stopTelemetry(motivo: MotivoFin) {
        voltagePoller.stop()
        milWatcher.stop()
        // Join so SessionRecorder's final NonCancellable flush lands in Room
        // before updateSessionEnd computes the trip aggregates.
        telemetryJob?.cancelAndJoin()
        telemetryJob = null
        activeScheduler = null
        linkPipeline = null
        tripController.onEnlacePerdido(motivo)
    }

    private fun publishReading(reading: ObdReading) {
        _readings.update { it + (reading.pid to reading) }
    }

    private fun onLinkReading(reading: ObdReading) {
        publishReading(reading)
        alertsEngine.process(reading)
        // Sin viaje no hay dónde guardar un 0-100: no se anuncia.
        if (tripController.estado.value is EstadoViaje.Grabando) launchTimer.process(reading)
        if (reading.pid == "0D") onObdSpeed(reading.value)
    }

    private fun onObdSpeed(kmh: Double) {
        engineOffDetector.onSpeed(kmh)
        if (!autoTripEnabled || tripController.estado.value != EstadoViaje.EnlaceSinViaje) return
        if (autoTripTrigger.onVelocidad(kmh, System.currentTimeMillis())) scope.launch { iniciarViajeAutomatico() }
    }

    private suspend fun iniciarViajeAutomatico() {
        iniciarViajeSobreEnlace().onSuccess { _eventosViaje.tryEmit(AUTO_TRIP_MESSAGE) }
    }

    private suspend fun observeAutoTripSetting() {
        runCatching {
            settings.data
                .map { it[PreferencesKeys.AUTO_TRIP_ON_MOVE] ?: true }
                .distinctUntilChanged()
                .collect { autoTripEnabled = it }
        }.onFailure { Timber.w(it, "ObdSessionManager: failed to observe auto-trip setting") }
    }

    private suspend fun observeRemoteViewer() {
        mcpActivity.espectadorActivo.collect { activo ->
            remoteViewerActive = activo
            activeScheduler?.setRemoteViewerActive(activo)
        }
    }

    private suspend fun observeSamplingPreset() {
        runCatching {
            settings.data
                .map { SamplingPreset.desdeClave(it[PreferencesKeys.SAMPLING_PRESET]) }
                .distinctUntilChanged()
                .collect { preset ->
                    _muestreoPreset.value = preset
                    activeScheduler?.setPreset(preset)
                }
        }.onFailure { Timber.w(it, "ObdSessionManager: failed to observe sampling preset") }
    }

    /** Cambia la frecuencia del sondeo normal; el scheduler vivo la toma sin reconectar. */
    suspend fun cambiarPresetMuestreo(preset: SamplingPreset) {
        settings.edit { it[PreferencesKeys.SAMPLING_PRESET] = preset.name }
    }

    private suspend fun abrirSesionObd(adapterName: String): Long {
        val sessionId = createSession(adapterName)
        bestTo60Ms = null
        bestTo100Ms = null
        launchTimer.reset()
        engineOffDetector.reset()
        autoTripTrigger.reset()
        alertsEngine.resetSessionFlags()
        return sessionId
    }

    private suspend fun cerrarSesionObd(sessionId: Long) {
        runCatching { updateSessionEnd(sessionId) }
            .onFailure { Timber.w(it, "ObdSessionManager: failed to close session $sessionId") }
    }

    /** Pipeline ya caído (carrera con la pérdida de enlace): Job vacío; el cierre llega enseguida. */
    private fun lanzarGrabacion(sessionId: Long): Job {
        val pipeline = linkPipeline ?: return Job().apply { complete() }
        return pipeline.scope.launch { SessionRecorder(telemetryDao).record(sessionId, pipeline.readings) }
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
        const val AUTO_TRIP_MESSAGE = "Viaje iniciado automáticamente"
        private const val DEFAULT_ADAPTER_NAME = "OBD"

        private const val DTC_TIMEOUT_MS = 5_000L
        private const val CAPTURE_READINGS_BUFFER = 512
        private const val DTC_FULL_READ_TIMEOUT_MS = 30_000L
        private const val DPN_TIMEOUT_MS = 1_500L
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

        fun parseVoltage(raw: String): Double? = VoltagePoller.parseVoltage(raw)

        fun parseDtcResponse(raw: String, mode: DtcMode): List<DtcCode> =
            DtcResponseParser.parse(raw, servicioDe(mode), esCan = null).map { DtcCode(code = it, mode = mode) }

        private fun servicioDe(mode: DtcMode): DtcServicio = when (mode) {
            DtcMode.Active -> DtcServicio.ACTIVOS
            DtcMode.Pending -> DtcServicio.PENDIENTES
            DtcMode.Permanent -> DtcServicio.PERMANENTES
        }
    }
}
