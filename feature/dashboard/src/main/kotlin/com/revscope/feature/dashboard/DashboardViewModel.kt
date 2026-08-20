package com.revscope.feature.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.data.db.entities.vehicleType
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.legal.RestrictionRulesSource
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MINUTE_MS = 60_000L

@HiltViewModel
class DashboardViewModel @Inject constructor(
    val orchestrator: IntelligenceOrchestrator,
    private val alertsEngine: AlertsEngine,
    private val sessionManager: ObdSessionManager,
    private val settings: DataStore<Preferences>,
    private val aiRulesSource: RestrictionRulesSource,
    private val updateChecker: com.revscope.core.obd.update.UpdateChecker,
) : ViewModel() {

    /** Nueva versión disponible en GitHub — null si estás al día o sin red. */
    val updateAvailable: StateFlow<com.revscope.core.obd.update.UpdateChecker.Update?> = updateChecker.available

    init {
        // Chequeo silencioso al abrir (throttle interno 12 h) — sin red no pasa nada.
        viewModelScope.launch { updateChecker.check() }
    }

    fun dismissUpdate() {
        viewModelScope.launch { updateChecker.dismiss() }
    }

    /** Redline used by the shift light — cached in AlertsEngine from DataStore. */
    val redlineRpm: Int get() = alertsEngine.currentRedlineRpm

    /**
     * Big speedometer source while connected via OBD: true = GPS_SPEED, false = PID 0D.
     * No effect during a GPS-only trip, which always shows GPS regardless of this flag.
     */
    val speedSourceGps: StateFlow<Boolean> = settings.data
        .map { it[PreferencesKeys.SPEED_SOURCE_GPS] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setSpeedSourceGps(useGps: Boolean) {
        viewModelScope.launch {
            settings.edit { prefs -> prefs[PreferencesKeys.SPEED_SOURCE_GPS] = useGps }
        }
    }

    /** True mientras el usuario nunca configuró un adaptador — el dashboard prioriza viaje GPS y mapa. */
    val gpsOnlyMode: StateFlow<Boolean> = settings.data
        .map { it[PreferencesKeys.GPS_ONLY_MODE] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** D2: sin adaptador vinculado nunca — debe disparar el mismo hero mode que la elección explícita del wizard. */
    private val adapterNotConfigured: StateFlow<Boolean> = settings.data
        .map { it[PreferencesKeys.ADAPTER_ADDRESS].isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Sin conexión OBD activa ni en curso — evita que el hero mode parpadee mientras conecta. */
    private val obdConnectionIdle: StateFlow<Boolean> = sessionManager.connectionState
        .map { it is ConnectionState.Disconnected || it is ConnectionState.Error }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * D2: hero mode del dashboard (viaje GPS + mapa por delante de los gauges) cuando el
     * usuario eligió "sin adaptador" en el wizard O nunca vinculó uno — inferencia por
     * ausencia de [PreferencesKeys.ADAPTER_ADDRESS] — y no hay conexión OBD activa.
     */
    val gpsHeroMode: StateFlow<Boolean> = combine(
        gpsOnlyMode, adapterNotConfigured, obdConnectionIdle,
    ) { onlyMode, noAdapter, idle -> (onlyMode || noAdapter) && idle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Default true: en moto la pantalla apagándose a mitad de ruta es peor que la batería. */
    val keepScreenOn: StateFlow<Boolean> = settings.data
        .map { it[PreferencesKeys.KEEP_SCREEN_ON] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val anomalyAlerts = orchestrator.anomalyAlerts
    val tripScore = orchestrator.tripScore

    val gearCalibrated: StateFlow<Boolean> = orchestrator.gearLearner.gearTable
        .map { table -> table.all { it.observationCount >= com.revscope.core.intelligence.gear.AdaptiveGearLearner.MIN_OBSERVATIONS_PER_GEAR } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Re-emits every minute so the banner re-evaluates across day/pico-y-placa boundaries. */
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MS)
        }
    }

    /** Thin contextual banner ("⚠ SOAT vencido · Pico y placa hasta las 20:00"); null when all clear. */
    val alDiaBanner: StateFlow<String?> = combine(
        sessionManager.activeProfile,
        settings.data.map { it[PreferencesKeys.LICENSE_EXPIRES_AT] },
        settings.data.map { prefs -> prefs[PreferencesKeys.PICO_PLACA_RULES_JSON]?.let(PicoYPlacaEngine::parseRulesJson) },
        minuteTicker,
    ) { profile, licenseExpiresAt, overrideRules, _ ->
        computeAlDiaBanner(profile, licenseExpiresAt, overrideRules)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var gearTableJob: Job? = null
    private var profileReconfigureJob: Job? = null

    fun stopIntelligence() {
        orchestrator.stop()
        gearTableJob?.cancel()
        gearTableJob = null
        profileReconfigureJob?.cancel()
        profileReconfigureJob = null
    }

    /**
     * Wires [readings] into the intelligence orchestrator and feeds gear ratios back into
     * [connectionVm] on every [AdaptiveGearLearner] table update.
     *
     * Safe to call multiple times (e.g. on reconnect) — subsequent calls reset the
     * orchestrator session and replace the previous gear-table and profile collectors.
     */
    fun startIntelligence(readings: Flow<ObdReading>, connectionVm: ConnectionViewModel) {
        orchestrator.resetTrip()
        val profile = sessionManager.activeProfile.value
        orchestrator.start(
            readings = readings,
            scope = viewModelScope,
            gearCount = profile?.gearCount ?: 6,
            vehicleType = profile?.vehicleType ?: VehicleType.CAR,
        )

        // I1: activeProfile can flip 1-3s after Connected (VIN auto-resolution resolves after
        // the profile read above), so keep the learner locked to whichever profile ends up
        // active for the rest of the session instead of a one-shot read. reconfigure()'s own
        // guard makes repeat/no-op emissions (reconnects, same-profile updates) free.
        profileReconfigureJob?.cancel()
        profileReconfigureJob = viewModelScope.launch {
            sessionManager.activeProfile.collect { activeProfile ->
                orchestrator.gearLearner.reconfigure(
                    activeProfile?.gearCount ?: 6,
                    activeProfile?.vehicleType ?: VehicleType.CAR,
                )
            }
        }

        gearTableJob?.cancel()
        gearTableJob = viewModelScope.launch {
            orchestrator.gearLearner.gearTable.collect {
                // I2: push unconditionally — pre-calibration this feeds per-type defaults,
                // which also fixes motos reading gear "1" against CAR defaults after a switch.
                connectionVm.setGearTable(orchestrator.gearLearner.toRatioTable())
            }
        }
    }

    private suspend fun computeAlDiaBanner(
        profile: VehicleProfileEntity?,
        licenseExpiresAt: Long?,
        overrideRules: PicoYPlacaEngine.CityRules?,
    ): String? {
        if (profile == null) return null
        val documents = DocumentStatusCalculator.fromProfile(profile, licenseExpiresAt)
        val now = System.currentTimeMillis()
        val aiFallback = aiFallbackFor(profile.picoPlacaCity, overrideRules, now)
        val statuses = DocumentStatusCalculator.calculate(documents, overrideRules, now, aiFallbackRules = aiFallback)
        return DocumentStatusCalculator.bannerText(statuses)
    }

    private suspend fun aiFallbackFor(
        cityId: String?,
        overrideRules: PicoYPlacaEngine.CityRules?,
        nowMs: Long,
    ): PicoYPlacaEngine.CityRules? =
        cityId
            ?.takeIf { DocumentStatusCalculator.needsAiFallback(it, overrideRules, nowMs) }
            ?.let { runCatching { aiRulesSource.rulesForCity(it) }.getOrNull() }
}
