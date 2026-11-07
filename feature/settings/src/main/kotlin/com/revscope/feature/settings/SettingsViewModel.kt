package com.revscope.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.FuelPricePrefs
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.units.DistanceUnit
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.revscope.core.data.backup.BackupManager
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.data.secure.SecureKeyStore
import com.revscope.core.intelligence.provider.AI_PROVIDER_ANTHROPIC
import com.revscope.core.intelligence.provider.AI_PROVIDER_CUSTOM
import com.revscope.core.intelligence.provider.AI_PROVIDER_NODO
import com.revscope.core.intelligence.provider.AI_PROVIDER_GEMINI
import com.revscope.core.intelligence.provider.AI_PROVIDER_OPENAI
import com.revscope.core.intelligence.provider.AiProviderFactory
import com.revscope.core.intelligence.provider.AiProviderSelection
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.maps.MapDownloadService
import com.revscope.core.maps.MapDownloadState
import com.revscope.core.maps.isOnWifi
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.guard.GuardService
import com.revscope.core.obd.alerts.CustomAlertRules
import com.revscope.core.obd.cameras.CameraAlertRadius
import com.revscope.core.obd.cameras.CameraDownloadResult
import com.revscope.core.obd.cameras.SpeedCameraUpdater
import com.revscope.core.obd.mcp.McpServerController
import com.revscope.core.obd.mcp.McpServerService
import com.revscope.core.obd.mcp.McpServerState
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.mcp.McpTokenStore
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.safety.CrashResponder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.sound.SoundPack
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: DataStore<Preferences>,
    private val registry: PidRegistry,
    private val alertsEngine: AlertsEngine,
    private val secureKeyStore: SecureKeyStore,
    private val cameraUpdater: SpeedCameraUpdater,
    private val sessionManager: ObdSessionManager,
    private val backupManager: BackupManager,
    private val crashResponder: CrashResponder,
    private val mcpTokenStore: McpTokenStore,
    private val mcpServerController: McpServerController,
    private val updateChecker: com.revscope.core.obd.update.UpdateChecker,
    private val mapDownloadService: MapDownloadService,
    private val stringResolver: StringResolver,
    private val units: UnitFormatter,
) : ViewModel() {

    data class SaveResult(val success: Boolean, val message: String)

    enum class BackupState { IDLE, EXPORTING, IMPORTING, RESTARTING_AFTER_IMPORT }

    private val _aiProvider = MutableStateFlow(AI_PROVIDER_ANTHROPIC)
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _aiApiKey = MutableStateFlow("")
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()

    private val _aiModel = MutableStateFlow("")
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _aiCustomBaseUrl = MutableStateFlow("")
    val aiCustomBaseUrl: StateFlow<String> = _aiCustomBaseUrl.asStateFlow()

    private val _aiTesting = MutableStateFlow(false)
    val aiTesting: StateFlow<Boolean> = _aiTesting.asStateFlow()

    private val _aiFieldsLoaded = MutableStateFlow(false)
    val aiFieldsLoaded: StateFlow<Boolean> = _aiFieldsLoaded.asStateFlow()

    private val _customPidsJson = MutableStateFlow("")
    val customPidsJson: StateFlow<String> = _customPidsJson.asStateFlow()

    private val _customAlertsJson = MutableStateFlow("")
    val customAlertsJson: StateFlow<String> = _customAlertsJson.asStateFlow()

    private val _picoPlacaRulesJson = MutableStateFlow("")
    val picoPlacaRulesJson: StateFlow<String> = _picoPlacaRulesJson.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(true)
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _tempMaxC = MutableStateFlow(temperatureFieldValue(DEFAULT_TEMP_MAX_C, units).toString())
    val tempMaxC: StateFlow<String> = _tempMaxC.asStateFlow()

    private val _voltageMin = MutableStateFlow("11.8")
    val voltageMin: StateFlow<String> = _voltageMin.asStateFlow()

    private val _redlineRpm = MutableStateFlow("10500")
    val redlineRpm: StateFlow<String> = _redlineRpm.asStateFlow()

    private val _fuelPriceCorriente = MutableStateFlow(FuelPricePrefs.DEFAULT_CORRIENTE.toLong().toString())
    val fuelPriceCorriente: StateFlow<String> = _fuelPriceCorriente.asStateFlow()

    private val _fuelPriceExtra = MutableStateFlow(FuelPricePrefs.DEFAULT_EXTRA.toLong().toString())
    val fuelPriceExtra: StateFlow<String> = _fuelPriceExtra.asStateFlow()

    private val _fuelPriceDiesel = MutableStateFlow(FuelPricePrefs.DEFAULT_DIESEL.toLong().toString())
    val fuelPriceDiesel: StateFlow<String> = _fuelPriceDiesel.asStateFlow()

    private val _cameraAlertRadius = MutableStateFlow(radiusFieldValue(CameraAlertRadius.DEFAULT_M, units).toString())
    val cameraAlertRadius: StateFlow<String> = _cameraAlertRadius.asStateFlow()

    private val _engineSoundEnabled = MutableStateFlow(false)
    val engineSoundEnabled: StateFlow<Boolean> = _engineSoundEnabled.asStateFlow()

    private val _engineSoundPack = MutableStateFlow(SoundPack.DEFAULT.id)
    val engineSoundPack: StateFlow<String> = _engineSoundPack.asStateFlow()

    private val _engineSoundVolume = MutableStateFlow("70")
    val engineSoundVolume: StateFlow<String> = _engineSoundVolume.asStateFlow()

    private val _lastSaveResult = MutableStateFlow<SaveResult?>(null)
    val lastSaveResult: StateFlow<SaveResult?> = _lastSaveResult.asStateFlow()

    private val _askVehicleOnStart = MutableStateFlow(true)
    val askVehicleOnStart: StateFlow<Boolean> = _askVehicleOnStart.asStateFlow()

    private val _voiceTemperature = MutableStateFlow(true)
    val voiceTemperature: StateFlow<Boolean> = _voiceTemperature.asStateFlow()

    private val _voiceVoltage = MutableStateFlow(true)
    val voiceVoltage: StateFlow<Boolean> = _voiceVoltage.asStateFlow()

    private val _voiceSpeedCameras = MutableStateFlow(true)
    val voiceSpeedCameras: StateFlow<Boolean> = _voiceSpeedCameras.asStateFlow()

    private val _voiceAnomalies = MutableStateFlow(false)
    val voiceAnomalies: StateFlow<Boolean> = _voiceAnomalies.asStateFlow()

    private val _voiceMil = MutableStateFlow(false)
    val voiceMil: StateFlow<Boolean> = _voiceMil.asStateFlow()

    private val _voiceRedline = MutableStateFlow(false)
    val voiceRedline: StateFlow<Boolean> = _voiceRedline.asStateFlow()

    private val _voiceCustomThresholds = MutableStateFlow(true)
    val voiceCustomThresholds: StateFlow<Boolean> = _voiceCustomThresholds.asStateFlow()

    private val _voiceSport = MutableStateFlow(true)
    val voiceSport: StateFlow<Boolean> = _voiceSport.asStateFlow()

    private val _voicePicoPlaca = MutableStateFlow(true)
    val voicePicoPlaca: StateFlow<Boolean> = _voicePicoPlaca.asStateFlow()

    private val _voiceLocalInfo = MutableStateFlow(false)
    val voiceLocalInfo: StateFlow<Boolean> = _voiceLocalInfo.asStateFlow()

    private val _voiceSunset = MutableStateFlow(true)
    val voiceSunset: StateFlow<Boolean> = _voiceSunset.asStateFlow()

    private val _voicePotholes = MutableStateFlow(true)
    val voicePotholes: StateFlow<Boolean> = _voicePotholes.asStateFlow()

    private val _voiceRain = MutableStateFlow(true)
    val voiceRain: StateFlow<Boolean> = _voiceRain.asStateFlow()

    private val _voiceFatigue = MutableStateFlow(true)
    val voiceFatigue: StateFlow<Boolean> = _voiceFatigue.asStateFlow()

    private val _zoneBriefEnabled = MutableStateFlow(false)
    val zoneBriefEnabled: StateFlow<Boolean> = _zoneBriefEnabled.asStateFlow()

    private val _voiceZoneBrief = MutableStateFlow(true)
    val voiceZoneBrief: StateFlow<Boolean> = _voiceZoneBrief.asStateFlow()

    // ── Servidor colaborativo ─────────────────────────────────────────────────

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _serverToken = MutableStateFlow("")
    val serverToken: StateFlow<String> = _serverToken.asStateFlow()

    private val _riderName = MutableStateFlow("")
    val riderName: StateFlow<String> = _riderName.asStateFlow()

    fun updateServerUrl(value: String) { _serverUrl.value = value }
    fun updateServerToken(value: String) { _serverToken.value = value }
    fun updateRiderName(value: String) { _riderName.value = value }

    // ── Buscar actualizaciones (manual) ──────────────────────────────────────

    fun checkForUpdates() {
        viewModelScope.launch {
            _lastSaveResult.value = SaveResult(true, stringResolver.get(R.string.settings_updates_checking))
            updateChecker.check(force = true)
            _lastSaveResult.value = updateChecker.available.value?.let {
                SaveResult(true, stringResolver.get(R.string.settings_updates_available, it.version))
            } ?: SaveResult(true, stringResolver.get(R.string.settings_updates_latest))
        }
    }

    fun saveServerSettings() {
        viewModelScope.launch {
            runCatching {
                settings.edit {
                    it[PreferencesKeys.SERVER_BASE_URL] = _serverUrl.value.trim().trimEnd('/')
                    it[PreferencesKeys.SERVER_AUTH_TOKEN] = _serverToken.value.trim()
                    it[PreferencesKeys.SERVER_RIDER_NAME] = _riderName.value.trim()
                }
            }
            _lastSaveResult.value = SaveResult(true, stringResolver.get(R.string.settings_server_saved))
        }
    }

    private val _aiPicoPlaca = MutableStateFlow(false)
    val aiPicoPlaca: StateFlow<Boolean> = _aiPicoPlaca.asStateFlow()

    val activeVehicleProfile: StateFlow<VehicleProfileEntity?> = sessionManager.activeProfile

    // ── Servidor MCP (red local) ─────────────────────────────────────────────

    private val _mcpServerEnabled = MutableStateFlow(false)
    val mcpServerEnabled: StateFlow<Boolean> = _mcpServerEnabled.asStateFlow()

    private val _mcpToken = MutableStateFlow("")
    val mcpToken: StateFlow<String> = _mcpToken.asStateFlow()

    /** Source of truth for whether the server is actually bound right now — see [McpServerController]. */
    val mcpServerState: StateFlow<McpServerState> = mcpServerController.state

    init {
        viewModelScope.launch {
            runCatching {
                _mcpToken.value = mcpTokenStore.tokenOrGenerate()
                _mcpServerEnabled.value = reconciledMcpServerEnabled(mcpTokenStore.enabled.first())
            }.onFailure { Timber.w(it, "SettingsViewModel: failed to load MCP server settings") }
        }
    }

    /**
     * The MCP server never auto-starts after a process restart, so a persisted `enabled = true`
     * with the controller still [McpServerState.Stopped] means the last session left the toggle
     * ON without the server actually running. Un-persist it here so the switch never shows ON
     * for a server that isn't there.
     */
    private suspend fun reconciledMcpServerEnabled(persistedEnabled: Boolean): Boolean {
        if (!persistedEnabled || mcpServerController.state.value != McpServerState.Stopped) return persistedEnabled
        runCatching { mcpTokenStore.setEnabled(false) }
            .onFailure { Timber.w(it, "SettingsViewModel: failed to reconcile stale MCP toggle") }
        return false
    }

    /** Persists the toggle and starts/stops the foreground MCP server immediately (session-scoped only). */
    fun updateMcpServerEnabled(value: Boolean) {
        _mcpServerEnabled.value = value
        viewModelScope.launch {
            runCatching { mcpTokenStore.setEnabled(value) }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist MCP server toggle") }
        }
        if (value) McpServerService.start(appContext) else McpServerService.stop(appContext)
    }

    init {
        viewModelScope.launch {
            runCatching {
                val prefs = settings.data.first()
                val provider = prefs[PreferencesKeys.AI_PROVIDER]?.takeIf { it.isNotBlank() } ?: AI_PROVIDER_ANTHROPIC
                _aiProvider.value = provider
                withContext(Dispatchers.IO) { loadAiProviderFields(provider, prefs) }
                _customPidsJson.value = prefs[PreferencesKeys.CUSTOM_PIDS_JSON].orEmpty()
                _customAlertsJson.value = prefs[PreferencesKeys.CUSTOM_ALERTS_JSON].orEmpty()
                _picoPlacaRulesJson.value = prefs[PreferencesKeys.PICO_PLACA_RULES_JSON].orEmpty()
                _alertsEnabled.value = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true
                _ttsEnabled.value = prefs[PreferencesKeys.ALERT_TTS_ENABLED] ?: true
                _tempMaxC.value =
                    temperatureFieldValue(prefs[PreferencesKeys.ALERT_TEMP_MAX_C] ?: DEFAULT_TEMP_MAX_C, units).toString()
                _voltageMin.value = (prefs[PreferencesKeys.ALERT_VOLTAGE_MIN] ?: 11.8f).toString()
                _redlineRpm.value = (prefs[PreferencesKeys.ALERT_REDLINE_RPM] ?: 10_500).toString()
                _askVehicleOnStart.value = prefs[PreferencesKeys.ASK_VEHICLE_ON_START] ?: true
                val fuelPrices = FuelPricePrefs.read(settings)
                _fuelPriceCorriente.value = fuelPrices.corriente.toLong().toString()
                _fuelPriceExtra.value = fuelPrices.extra.toLong().toString()
                _fuelPriceDiesel.value = fuelPrices.diesel.toLong().toString()
                _voiceTemperature.value = prefs[PreferencesKeys.VOICE_TEMPERATURE] ?: true
                _voiceVoltage.value = prefs[PreferencesKeys.VOICE_VOLTAGE] ?: true
                _voiceSpeedCameras.value = prefs[PreferencesKeys.VOICE_SPEED_CAMERAS] ?: true
                _cameraAlertRadius.value =
                    radiusFieldValue(CameraAlertRadius.sanitize(prefs[PreferencesKeys.CAMERA_ALERT_RADIUS_M]), units)
                        .toString()
                _engineSoundEnabled.value = prefs[PreferencesKeys.ENGINE_SOUND_ENABLED] ?: false
                _engineSoundPack.value = SoundPack.fromId(prefs[PreferencesKeys.ENGINE_SOUND_PACK]).id
                _engineSoundVolume.value = (prefs[PreferencesKeys.ENGINE_SOUND_VOLUME] ?: 70).toString()
                _voiceAnomalies.value = prefs[PreferencesKeys.VOICE_ANOMALIES] ?: false
                _voiceMil.value = prefs[PreferencesKeys.VOICE_MIL] ?: false
                _voiceRedline.value = prefs[PreferencesKeys.VOICE_REDLINE] ?: false
                _voiceCustomThresholds.value = prefs[PreferencesKeys.VOICE_CUSTOM_THRESHOLDS] ?: true
                _voiceSport.value = prefs[PreferencesKeys.VOICE_SPORT] ?: true
                _voicePicoPlaca.value = prefs[PreferencesKeys.VOICE_PICO_PLACA] ?: true
                _voiceLocalInfo.value = prefs[PreferencesKeys.VOICE_LOCAL_INFO] ?: false
                _voiceSunset.value = prefs[PreferencesKeys.VOICE_SUNSET] ?: true
                _voicePotholes.value = prefs[PreferencesKeys.VOICE_POTHOLES] ?: true
                _voiceRain.value = prefs[PreferencesKeys.VOICE_RAIN] ?: true
                _voiceFatigue.value = prefs[PreferencesKeys.VOICE_FATIGUE] ?: true
                _zoneBriefEnabled.value = prefs[PreferencesKeys.ZONE_BRIEF_ENABLED] ?: false
                _voiceZoneBrief.value = prefs[PreferencesKeys.VOICE_ZONE_BRIEF] ?: true
                _serverUrl.value = prefs[PreferencesKeys.SERVER_BASE_URL].orEmpty()
                _serverToken.value = prefs[PreferencesKeys.SERVER_AUTH_TOKEN].orEmpty()
                _riderName.value = prefs[PreferencesKeys.SERVER_RIDER_NAME].orEmpty()
                _aiPicoPlaca.value = prefs[PreferencesKeys.AI_PICO_PLACA_ENABLED] ?: false
                _aiFieldsLoaded.value = true
            }.onFailure { Timber.w(it, "SettingsViewModel: failed to load settings") }
        }
    }

    fun updateAskVehicleOnStart(value: Boolean) {
        _askVehicleOnStart.value = value
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.ASK_VEHICLE_ON_START] = value } }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist ask-on-start") }
        }
    }

    fun updateVoiceTemperature(value: Boolean) =
        updateVoiceCategory(_voiceTemperature, PreferencesKeys.VOICE_TEMPERATURE, value)

    fun updateVoiceVoltage(value: Boolean) =
        updateVoiceCategory(_voiceVoltage, PreferencesKeys.VOICE_VOLTAGE, value)

    fun updateVoiceSpeedCameras(value: Boolean) =
        updateVoiceCategory(_voiceSpeedCameras, PreferencesKeys.VOICE_SPEED_CAMERAS, value)

    fun updateVoiceAnomalies(value: Boolean) =
        updateVoiceCategory(_voiceAnomalies, PreferencesKeys.VOICE_ANOMALIES, value)

    fun updateVoiceMil(value: Boolean) =
        updateVoiceCategory(_voiceMil, PreferencesKeys.VOICE_MIL, value)

    fun updateVoiceRedline(value: Boolean) =
        updateVoiceCategory(_voiceRedline, PreferencesKeys.VOICE_REDLINE, value)

    fun updateVoiceCustomThresholds(value: Boolean) =
        updateVoiceCategory(_voiceCustomThresholds, PreferencesKeys.VOICE_CUSTOM_THRESHOLDS, value)

    fun updateVoiceSport(value: Boolean) =
        updateVoiceCategory(_voiceSport, PreferencesKeys.VOICE_SPORT, value)

    fun updateVoicePicoPlaca(value: Boolean) =
        updateVoiceCategory(_voicePicoPlaca, PreferencesKeys.VOICE_PICO_PLACA, value)

    fun updateVoiceLocalInfo(value: Boolean) =
        updateVoiceCategory(_voiceLocalInfo, PreferencesKeys.VOICE_LOCAL_INFO, value)

    fun updateVoiceSunset(value: Boolean) =
        updateVoiceCategory(_voiceSunset, PreferencesKeys.VOICE_SUNSET, value)

    fun updateVoicePotholes(value: Boolean) =
        updateVoiceCategory(_voicePotholes, PreferencesKeys.VOICE_POTHOLES, value)

    fun updateVoiceRain(value: Boolean) =
        updateVoiceCategory(_voiceRain, PreferencesKeys.VOICE_RAIN, value)

    fun updateVoiceFatigue(value: Boolean) =
        updateVoiceCategory(_voiceFatigue, PreferencesKeys.VOICE_FATIGUE, value)

    fun updateZoneBriefEnabled(value: Boolean) =
        updateVoiceCategory(_zoneBriefEnabled, PreferencesKeys.ZONE_BRIEF_ENABLED, value)

    fun updateVoiceZoneBrief(value: Boolean) =
        updateVoiceCategory(_voiceZoneBrief, PreferencesKeys.VOICE_ZONE_BRIEF, value)

    // ── Modo guardia antirrobo ───────────────────────────────────────────────

    val guardRunning: StateFlow<Boolean> = GuardService.running
    val guardAlarmActive: StateFlow<Boolean> = GuardService.alarmActive

    fun armGuard() = GuardService.arm(appContext)

    fun disarmGuard() = GuardService.disarm(appContext)

    fun updateAiPicoPlaca(value: Boolean) =
        updateVoiceCategory(_aiPicoPlaca, PreferencesKeys.AI_PICO_PLACA_ENABLED, value)

    /** Persists a voice-alert category toggle immediately and reloads AlertsEngine's cache. */
    private fun updateVoiceCategory(
        state: MutableStateFlow<Boolean>,
        key: Preferences.Key<Boolean>,
        value: Boolean,
    ) {
        state.value = value
        viewModelScope.launch {
            runCatching { settings.edit { it[key] = value } }
                .onSuccess { alertsEngine.reloadThresholds() }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist voice category ${key.name}") }
        }
    }

    /** Switches the active AI provider and reloads that provider's own key/model into the fields. */
    fun updateAiProvider(value: String) {
        _aiProvider.value = value
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadAiProviderFields(value, settings.data.first()) } }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to load fields for AI provider $value") }
        }
    }

    fun updateAiApiKey(value: String) {
        _aiApiKey.value = value
    }

    fun updateAiModel(value: String) {
        _aiModel.value = value
    }

    fun updateAiCustomBaseUrl(value: String) {
        _aiCustomBaseUrl.value = value
    }

    fun updateCustomPidsJson(value: String) {
        _customPidsJson.value = value
    }

    fun updateCustomAlertsJson(value: String) {
        _customAlertsJson.value = value
    }

    fun updatePicoPlacaRulesJson(value: String) {
        _picoPlacaRulesJson.value = value
    }

    fun updateAlertsEnabled(value: Boolean) {
        _alertsEnabled.value = value
    }

    fun updateTtsEnabled(value: Boolean) {
        _ttsEnabled.value = value
    }

    fun updateTempMaxC(value: String) {
        _tempMaxC.value = value
    }

    fun updateVoltageMin(value: String) {
        _voltageMin.value = value
    }

    fun updateRedlineRpm(value: String) {
        _redlineRpm.value = value
    }

    fun updateFuelPriceCorriente(value: String) {
        _fuelPriceCorriente.value = value
    }

    fun updateFuelPriceExtra(value: String) {
        _fuelPriceExtra.value = value
    }

    fun updateFuelPriceDiesel(value: String) {
        _fuelPriceDiesel.value = value
    }

    fun saveFuelPrices() {
        viewModelScope.launch {
            val corriente = _fuelPriceCorriente.value.toDoubleOrNull()
            val extra = _fuelPriceExtra.value.toDoubleOrNull()
            val diesel = _fuelPriceDiesel.value.toDoubleOrNull()
            if (!isValidFuelPrice(corriente) || !isValidFuelPrice(extra) || !isValidFuelPrice(diesel)) {
                _lastSaveResult.value = SaveResult(false, stringResolver.get(R.string.settings_fuel_price_out_of_range))
                return@launch
            }
            val result = runCatching {
                settings.edit {
                    it[PreferencesKeys.FUEL_PRICE_CORRIENTE] = corriente!!
                    it[PreferencesKeys.FUEL_PRICE_EXTRA] = extra!!
                    it[PreferencesKeys.FUEL_PRICE_DIESEL] = diesel!!
                }
            }
            _lastSaveResult.value = if (result.isSuccess) {
                SaveResult(true, stringResolver.get(R.string.settings_fuel_prices_updated))
            } else {
                SaveResult(false, stringResolver.get(R.string.settings_fuel_prices_save_error))
            }
        }
    }

    private fun isValidFuelPrice(price: Double?): Boolean = price != null && price in 1_000.0..50_000.0

    fun saveAlertSettings() {
        viewModelScope.launch {
            val temp = _tempMaxC.value.toIntOrNull()?.let { temperatureFieldToCelsius(it, units) }
            val voltage = _voltageMin.value.toFloatOrNull()
            val redline = _redlineRpm.value.toIntOrNull()
            if (temp == null || temp !in TEMP_MAX_RANGE_C ||
                voltage == null || voltage !in 8f..15f ||
                redline == null || redline !in 3_000..20_000
            ) {
                _lastSaveResult.value = SaveResult(false, thresholdsOutOfRangeMessage())
                return@launch
            }
            _tempMaxC.value = temperatureFieldValue(temp, units).toString()
            val result = runCatching {
                settings.edit {
                    it[PreferencesKeys.ALERTS_ENABLED] = _alertsEnabled.value
                    it[PreferencesKeys.ALERT_TTS_ENABLED] = _ttsEnabled.value
                    it[PreferencesKeys.ALERT_TEMP_MAX_C] = temp
                    it[PreferencesKeys.ALERT_VOLTAGE_MIN] = voltage
                    it[PreferencesKeys.ALERT_REDLINE_RPM] = redline
                }
            }
            if (result.isSuccess) alertsEngine.reloadThresholds()
            _lastSaveResult.value = if (result.isSuccess) {
                SaveResult(true, stringResolver.get(R.string.settings_alerts_updated))
            } else {
                SaveResult(false, stringResolver.get(R.string.settings_alerts_save_error))
            }
        }
    }

    private fun thresholdsOutOfRangeMessage(): String = stringResolver.get(
        R.string.settings_alert_thresholds_out_of_range,
        temperatureFieldValue(TEMP_MAX_RANGE_C.first, units),
        temperatureFieldValue(TEMP_MAX_RANGE_C.last, units),
    )

    /** Persists provider + its model override/base URL to DataStore and its key to SecureKeyStore. */
    fun saveAiSettings() {
        viewModelScope.launch {
            val provider = _aiProvider.value
            val result = runCatching {
                val keyStored = withContext(Dispatchers.IO) { storeAiKey(provider) }
                if (keyStored) persistAiSelection(provider)
                keyStored
            }
            _lastSaveResult.value =
                aiSaveResult(result, keyEntered = _aiApiKey.value.isNotBlank(), resolver = stringResolver)
        }
    }

    private suspend fun storeAiKey(provider: String): Boolean {
        if (!secureKeyStore.setApiKey(provider, _aiApiKey.value.trim())) return false
        // Wipe any plaintext Claude key left from before encryption existed
        if (provider == AI_PROVIDER_ANTHROPIC) settings.edit { it.remove(PreferencesKeys.CLAUDE_API_KEY) }
        return true
    }

    private suspend fun persistAiSelection(provider: String) {
        settings.edit {
            it[PreferencesKeys.AI_PROVIDER] = provider
            it[modelKeyFor(provider)] = _aiModel.value.trim()
            if (provider == AI_PROVIDER_CUSTOM || provider == AI_PROVIDER_NODO) {
                it[PreferencesKeys.AI_CUSTOM_BASE_URL] = _aiCustomBaseUrl.value.trim()
            }
        }
        // Guardar una key = quiero IA: se encienden todas las funciones IA de una
        // vez (el usuario puede apagarlas individualmente después). Sin esto cada
        // función era un opt-in enterrado que nadie descubría.
        if (_aiApiKey.value.isNotBlank()) {
            settings.edit {
                it[PreferencesKeys.AI_PICO_PLACA_ENABLED] = true
                it[PreferencesKeys.VOICE_LOCAL_INFO] = true
            }
            _aiPicoPlaca.value = true
            _voiceLocalInfo.value = true
        }
    }

    /** Builds a provider from the fields as currently edited (not necessarily saved yet) and pings it. */
    fun testAiConnection() {
        viewModelScope.launch {
            _aiTesting.value = true
            val selection = AiProviderSelection(
                provider = _aiProvider.value,
                apiKey = _aiApiKey.value.trim(),
                model = _aiModel.value.trim(),
                customBaseUrl = _aiCustomBaseUrl.value.trim(),
            )
            val provider = AiProviderFactory(stringResolver) { selection }.current()
            _lastSaveResult.value = if (provider == null) {
                SaveResult(
                    false,
                    stringResolver.get(
                        if (selection.provider == AI_PROVIDER_CUSTOM) {
                            R.string.settings_ai_api_key_or_base_url_missing
                        } else {
                            R.string.settings_ai_api_key_missing
                        },
                    ),
                )
            } else {
                val result = withContext(Dispatchers.IO) {
                    provider.complete(AiRequest(user = "di OK", maxTokens = 10)) // i18n-ignore
                }
                result.fold(
                    onSuccess = {
                        SaveResult(true, stringResolver.get(R.string.settings_ai_connection_success, provider.displayName))
                    },
                    onFailure = { error -> aiConnectionFailure(error, stringResolver) },
                )
            }
            _aiTesting.value = false
        }
    }

    /** Loads [provider]'s own key/model/base-URL into the editable fields — called on init and provider switch. */
    private suspend fun loadAiProviderFields(provider: String, prefs: Preferences) {
        val key = if (provider == AI_PROVIDER_ANTHROPIC) {
            loadAnthropicKeyMigrating(prefs[PreferencesKeys.CLAUDE_API_KEY])
        } else {
            secureKeyStore.getApiKey(provider).orEmpty()
        }
        _aiApiKey.value = key
        _aiModel.value = prefs[modelKeyFor(provider)].orEmpty()
        _aiCustomBaseUrl.value = prefs[PreferencesKeys.AI_CUSTOM_BASE_URL].orEmpty()
    }

    /** Reads the Claude key from encrypted storage, migrating a plaintext DataStore copy if found. */
    private suspend fun loadAnthropicKeyMigrating(plaintextLegacy: String?): String {
        val secure = secureKeyStore.getApiKey(AI_PROVIDER_ANTHROPIC)
        if (secure != null) return secure
        if (!plaintextLegacy.isNullOrBlank()) {
            val migrated = secureKeyStore.setApiKey(AI_PROVIDER_ANTHROPIC, plaintextLegacy)
            if (migrated) runCatching { settings.edit { it.remove(PreferencesKeys.CLAUDE_API_KEY) } }
            return plaintextLegacy
        }
        return ""
    }

    private fun modelKeyFor(provider: String) = when (provider) {
        AI_PROVIDER_OPENAI -> PreferencesKeys.AI_MODEL_OPENAI
        AI_PROVIDER_GEMINI -> PreferencesKeys.AI_MODEL_GEMINI
        AI_PROVIDER_CUSTOM, AI_PROVIDER_NODO -> PreferencesKeys.AI_MODEL_CUSTOM
        else -> PreferencesKeys.AI_MODEL_ANTHROPIC
    }

    fun saveCustomPids() {
        viewModelScope.launch {
            val json = _customPidsJson.value.trim()
            if (json.isNotEmpty() && !isValidPidJson(json)) {
                _lastSaveResult.value = SaveResult(false, stringResolver.get(R.string.settings_json_invalid))
                return@launch
            }
            val result = runCatching {
                settings.edit { it[PreferencesKeys.CUSTOM_PIDS_JSON] = json }
            }
            if (result.isSuccess && json.isNotEmpty()) {
                // Apply live so the scheduler picks the new PIDs up without a restart
                registry.addDefinitions(json)
            }
            _lastSaveResult.value = if (result.isSuccess) {
                val count = if (json.isEmpty()) 0 else JSONArray(json).length()
                SaveResult(
                    true,
                    if (count > 0) stringResolver.get(R.string.settings_custom_pids_applied, count)
                    else stringResolver.get(R.string.settings_custom_pids_cleared),
                )
            } else {
                SaveResult(false, stringResolver.get(R.string.settings_custom_pids_save_error))
            }
        }
    }

    fun saveCustomAlerts() {
        viewModelScope.launch {
            val json = _customAlertsJson.value.trim()
            if (json.isNotEmpty() && !isValidCustomAlertJson(json)) {
                _lastSaveResult.value = SaveResult(false, stringResolver.get(R.string.settings_json_invalid))
                return@launch
            }
            val result = runCatching {
                settings.edit { it[PreferencesKeys.CUSTOM_ALERTS_JSON] = json }
            }
            if (result.isSuccess) alertsEngine.reloadThresholds()
            _lastSaveResult.value = if (result.isSuccess) {
                val count = if (json.isEmpty()) 0 else JSONArray(json).length()
                SaveResult(
                    true,
                    if (count > 0) stringResolver.get(R.string.settings_custom_alerts_applied, count)
                    else stringResolver.get(R.string.settings_custom_alerts_cleared),
                )
            } else {
                SaveResult(false, stringResolver.get(R.string.settings_custom_alerts_save_error))
            }
        }
    }

    /** Persists a user override of PicoYPlacaEngine.CityRules; consumers read it live via DataStore, no restart needed. */
    fun savePicoPlacaRules() {
        viewModelScope.launch {
            val json = _picoPlacaRulesJson.value.trim()
            if (json.isNotEmpty() && !isValidPicoPlacaRulesJson(json)) {
                _lastSaveResult.value = SaveResult(false, stringResolver.get(R.string.settings_json_invalid))
                return@launch
            }
            val result = runCatching {
                settings.edit { it[PreferencesKeys.PICO_PLACA_RULES_JSON] = json }
            }
            _lastSaveResult.value = if (result.isSuccess) {
                SaveResult(
                    true,
                    if (json.isEmpty()) {
                        stringResolver.get(R.string.settings_custom_driving_restrictions_cleared)
                    } else {
                        stringResolver.get(R.string.settings_custom_driving_restrictions_applied)
                    },
                )
            } else {
                SaveResult(false, stringResolver.get(R.string.settings_custom_driving_restrictions_save_error))
            }
        }
    }

    fun dismissSaveResult() {
        _lastSaveResult.value = null
    }

    // ── Backup / restore ─────────────────────────────────────────────────────

    private val _backupState = MutableStateFlow(BackupState.IDLE)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(true)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { settings.data.first()[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: true }
                .onSuccess { _autoBackupEnabled.value = it }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to load auto-backup toggle") }
        }
    }

    fun updateAutoBackupEnabled(value: Boolean) {
        _autoBackupEnabled.value = value
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.AUTO_BACKUP_ENABLED] = value } }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist auto-backup toggle") }
        }
    }

    // ── Mapa offline de Colombia ─────────────────────────────────────────────

    val mapDownloadState: StateFlow<MapDownloadState> = mapDownloadService.state

    fun downloadOfflineMap(allowCellular: Boolean) = mapDownloadService.download(allowCellular)

    fun cancelOfflineMapDownload() = mapDownloadService.cancel()

    fun deleteOfflineMap() = mapDownloadService.delete()

    /** Lee la conectividad actual para que la UI decida qué diálogo de confirmación mostrar
     * antes de llamar a [downloadOfflineMap] — MapDownloadService igual re-chequea WiFi al
     * arrancar la descarga, esto solo evita mostrarle al usuario el diálogo equivocado. */
    fun isOnWifiNow(): Boolean = isOnWifi(appContext)

    // ── Pantalla ─────────────────────────────────────────────────────────────

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { settings.data.first()[PreferencesKeys.KEEP_SCREEN_ON] ?: true }
                .onSuccess { _keepScreenOn.value = it }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to load keep-screen-on toggle") }
        }
    }

    fun updateKeepScreenOn(value: Boolean) {
        _keepScreenOn.value = value
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.KEEP_SCREEN_ON] = value } }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist keep-screen-on toggle") }
        }
    }

    // ── Detección de caída (SAFETY-CRITICAL: desactivada por defecto) ───────

    private val _crashDetectionEnabled = MutableStateFlow(false)
    val crashDetectionEnabled: StateFlow<Boolean> = _crashDetectionEnabled.asStateFlow()

    private val _emergencyPhone = MutableStateFlow("")
    val emergencyPhone: StateFlow<String> = _emergencyPhone.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val prefs = settings.data.first()
                _crashDetectionEnabled.value = prefs[PreferencesKeys.CRASH_DETECTION_ENABLED] ?: false
                _emergencyPhone.value = prefs[PreferencesKeys.EMERGENCY_PHONE].orEmpty()
            }.onFailure { Timber.w(it, "SettingsViewModel: failed to load crash detection settings") }
        }
    }

    fun updateEmergencyPhone(value: String) {
        _emergencyPhone.value = value
    }

    fun saveEmergencyPhone() {
        viewModelScope.launch {
            val phone = _emergencyPhone.value.trim()
            val result = runCatching { settings.edit { it[PreferencesKeys.EMERGENCY_PHONE] = phone } }
            _lastSaveResult.value = if (result.isSuccess) {
                SaveResult(true, stringResolver.get(R.string.settings_emergency_phone_saved))
            } else {
                SaveResult(false, stringResolver.get(R.string.settings_emergency_phone_save_error))
            }
        }
    }

    /** UI must request SEND_SMS and only call this with true once it's granted. */
    fun updateCrashDetectionEnabled(value: Boolean) {
        if (value && !canEnableCrashDetection()) {
            _lastSaveResult.value =
                SaveResult(false, stringResolver.get(R.string.settings_emergency_phone_required))
            return
        }
        _crashDetectionEnabled.value = value
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.CRASH_DETECTION_ENABLED] = value } }
                .onSuccess { crashResponder.reloadSettings() }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist crash detection toggle") }
        }
    }

    /**
     * Simulates a TRIGGERED crash alarm (countdown + notification) without sending a real SMS.
     * L1: uses CrashResponder's own long-lived scope, not [viewModelScope] — otherwise
     * navigating away from Settings mid-countdown cancels it and leaves a frozen alarm dialog.
     */
    fun testCrashAlert() {
        crashResponder.simulateTrigger(
            activeVehicleProfile.value?.name ?: stringResolver.get(R.string.settings_crash_alert_vehicle_fallback),
        )
    }

    private fun canEnableCrashDetection(): Boolean =
        _emergencyPhone.value.isNotBlank() && hasSmsPermission()

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.EXPORTING
            val error = runBackupOperation {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    backupManager.export(out).getOrThrow()
                } ?: error(stringResolver.get(R.string.settings_backup_destination_unavailable))
            }
            _backupState.value = BackupState.IDLE
            _lastSaveResult.value = if (error == null) {
                SaveResult(true, stringResolver.get(R.string.settings_backup_exported))
            } else {
                SaveResult(
                    false,
                    stringResolver.get(
                        R.string.settings_backup_export_error,
                        error.message ?: stringResolver.get(R.string.settings_unknown_error),
                    ),
                )
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.IMPORTING
            val error = runBackupOperation {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    backupManager.import(input).getOrThrow()
                } ?: error(stringResolver.get(R.string.settings_backup_file_unavailable))
            }
            if (error == null) {
                _backupState.value = BackupState.RESTARTING_AFTER_IMPORT
            } else {
                _backupState.value = BackupState.IDLE
                _lastSaveResult.value = SaveResult(
                    false,
                    stringResolver.get(
                        R.string.settings_backup_import_error,
                        error.message ?: stringResolver.get(R.string.settings_unknown_error),
                    ),
                )
            }
        }
    }

    private suspend fun runBackupOperation(block: suspend () -> Unit): Throwable? = try {
        withContext(Dispatchers.IO) { block() }
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "SettingsViewModel: backup operation failed")
        e
    }

    // ── Speed cameras ────────────────────────────────────────────────────────

    private val _cameraStatus = MutableStateFlow<String?>(null)
    val cameraStatus: StateFlow<String?> = _cameraStatus.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { cameraUpdater.storedCounts() }.getOrNull()?.let { stored ->
                if (stored.total > 0) {
                    _cameraStatus.value = stringResolver.get(
                        R.string.settings_speed_cameras_stored,
                        stored.total,
                        sourceBreakdown(stored),
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun downloadSpeedCameras() {
        viewModelScope.launch {
            _cameraStatus.value = stringResolver.get(R.string.settings_speed_cameras_location_search)
            val location = runCatching {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }.getOrNull()
            if (location == null) {
                _cameraStatus.value = stringResolver.get(R.string.settings_speed_cameras_no_location)
                return@launch
            }
            _cameraStatus.value = stringResolver.get(R.string.settings_speed_cameras_downloading)
            runCatching { cameraUpdater.downloadAround(location.latitude, location.longitude) }
                .onSuccess { result ->
                    _cameraStatus.value = stringResolver.get(
                        R.string.settings_speed_cameras_downloaded,
                        result.total,
                        sourceBreakdown(result),
                        cameraDownloadDistance(units),
                        units.distanceLabel(),
                    )
                    persistLastCameraCenter(location.latitude, location.longitude)
                }
                .onFailure {
                    _cameraStatus.value = stringResolver.get(R.string.settings_speed_cameras_download_error)
                }
        }
    }

    private fun sourceBreakdown(result: CameraDownloadResult): String =
        stringResolver.get(R.string.settings_speed_cameras_sources, result.osmCount, result.ansvCount)

    fun updateCameraAlertRadius(value: String) {
        _cameraAlertRadius.value = value
    }

    fun updateEngineSoundEnabled(value: Boolean) {
        _engineSoundEnabled.value = value
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.ENGINE_SOUND_ENABLED] = value } }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist engine sound enabled") }
        }
    }

    fun updateEngineSoundPack(id: String) {
        val pack = SoundPack.fromId(id)
        _engineSoundPack.value = pack.id
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.ENGINE_SOUND_PACK] = pack.id } }
                .onFailure { Timber.w(it, "SettingsViewModel: failed to persist engine sound pack") }
        }
    }

    fun updateEngineSoundVolume(value: String) {
        _engineSoundVolume.value = value
    }

    fun saveEngineSoundVolume() {
        viewModelScope.launch {
            val parsed = _engineSoundVolume.value.toIntOrNull()
            if (parsed == null) {
                _lastSaveResult.value = SaveResult(false, stringResolver.get(R.string.settings_engine_volume_invalid))
                return@launch
            }
            val volume = parsed.coerceIn(0, 100)
            _engineSoundVolume.value = volume.toString()
            _lastSaveResult.value = runCatching {
                settings.edit { it[PreferencesKeys.ENGINE_SOUND_VOLUME] = volume }
            }.fold(
                onSuccess = {
                    SaveResult(true, stringResolver.get(R.string.settings_engine_volume_saved, volume))
                },
                onFailure = { SaveResult(false, stringResolver.get(R.string.settings_engine_volume_save_error)) },
            )
        }
    }

    fun saveCameraAlertRadius() {
        viewModelScope.launch {
            val parsed = _cameraAlertRadius.value.toIntOrNull()
            if (parsed == null) {
                _lastSaveResult.value = SaveResult(false, radiusInvalidMessage())
                return@launch
            }
            val radius = CameraAlertRadius.sanitize(radiusFieldToMeters(parsed, units))
            val shown = radiusFieldValue(radius, units)
            _cameraAlertRadius.value = shown.toString()
            _lastSaveResult.value = runCatching {
                settings.edit { it[PreferencesKeys.CAMERA_ALERT_RADIUS_M] = radius }
            }.fold(
                onSuccess = {
                    SaveResult(
                        true,
                        stringResolver.get(R.string.settings_camera_alert_radius_saved, shown, radiusFieldUnit(units).label),
                    )
                },
                onFailure = { SaveResult(false, stringResolver.get(R.string.settings_camera_alert_radius_save_error)) },
            )
        }
    }

    private fun radiusInvalidMessage(): String = stringResolver.get(
        R.string.settings_camera_alert_radius_invalid,
        stringResolver.get(radiusUnitWordRes(units)),
    )

    /** Guarda el centro de la última descarga exitosa para el refresco semanal automático. */
    private suspend fun persistLastCameraCenter(latitude: Double, longitude: Double) {
        runCatching {
            settings.edit {
                it[PreferencesKeys.LAST_CAMERA_LAT] = latitude
                it[PreferencesKeys.LAST_CAMERA_LON] = longitude
            }
        }.onFailure { Timber.w(it, "SettingsViewModel: failed to persist camera center") }
    }

    private fun isValidPidJson(json: String): Boolean = try {
        val array = JSONArray(json)
        (0 until array.length()).all { i ->
            val obj = array.getJSONObject(i)
            listOf("mode", "pid", "name", "nameEs", "bytes", "formula", "unit", "min", "max", "priority")
                .all { obj.has(it) }
        }
    } catch (_: Exception) {
        false
    }

    private fun isValidCustomAlertJson(json: String): Boolean = try {
        val array = JSONArray(json)
        (0 until array.length()).all { i -> isValidCustomAlertRule(array.getJSONObject(i)) }
    } catch (_: Exception) {
        false
    }

    private fun isValidCustomAlertRule(obj: JSONObject): Boolean {
        if (!obj.has("pid") || !obj.has("nombre")) return false
        val min = if (obj.has("min")) obj.getDouble("min") else null
        val max = if (obj.has("max")) obj.getDouble("max") else null
        return !CustomAlertRules.isInvertedRange(min, max)
    }

    private fun isValidPicoPlacaRulesJson(json: String): Boolean = PicoYPlacaEngine.parseRulesJson(json) != null
}

private const val DEFAULT_TEMP_MAX_C = 105
private val TEMP_MAX_RANGE_C = 60..150
private const val CAMERA_DOWNLOAD_RADIUS_KM = 50.0
private const val METERS_PER_KM = 1000.0
private const val FEET_PER_MILE = 5280.0

internal fun temperatureFieldValue(celsius: Int, units: UnitFormatter): Int =
    units.temperature(celsius.toDouble()).roundToInt()

internal fun temperatureFieldToCelsius(value: Int, units: UnitFormatter): Int =
    units.toMetricTemperature(value.toDouble()).roundToInt()

internal fun radiusFieldUnit(units: UnitFormatter): DistanceUnit =
    if (units.system == UnitSystem.METRIC) DistanceUnit.METERS else DistanceUnit.FEET

internal fun radiusUnitWordRes(units: UnitFormatter): Int =
    if (units.system == UnitSystem.METRIC) R.string.settings_units_word_meters else R.string.settings_units_word_feet

internal fun radiusFieldValue(meters: Int, units: UnitFormatter): Int =
    (units.distance(meters / METERS_PER_KM) * subunitsPerDistanceUnit(units)).roundToInt()

internal fun radiusFieldToMeters(value: Int, units: UnitFormatter): Int =
    (units.toMetricDistance(value / subunitsPerDistanceUnit(units)) * METERS_PER_KM).roundToInt()

internal fun cameraDownloadDistance(units: UnitFormatter): Int =
    units.distance(CAMERA_DOWNLOAD_RADIUS_KM).roundToInt()

private fun subunitsPerDistanceUnit(units: UnitFormatter): Double =
    if (units.system == UnitSystem.METRIC) METERS_PER_KM else FEET_PER_MILE
