package com.revscope.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.Uri
import com.revscope.core.data.backup.BackupManager
import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.data.secure.SecureKeyStore
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.alerts.CustomAlertRules
import com.revscope.core.obd.cameras.SpeedCameraUpdater
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: DataStore<Preferences>,
    private val registry: PidRegistry,
    private val alertsEngine: AlertsEngine,
    private val secureKeyStore: SecureKeyStore,
    private val cameraUpdater: SpeedCameraUpdater,
    private val cameraDao: SpeedCameraDao,
    private val sessionManager: ObdSessionManager,
    private val backupManager: BackupManager,
) : ViewModel() {

    data class SaveResult(val success: Boolean, val message: String)

    enum class BackupState { IDLE, EXPORTING, IMPORTING, RESTARTING_AFTER_IMPORT }

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _customPidsJson = MutableStateFlow("")
    val customPidsJson: StateFlow<String> = _customPidsJson.asStateFlow()

    private val _customAlertsJson = MutableStateFlow("")
    val customAlertsJson: StateFlow<String> = _customAlertsJson.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(true)
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _tempMaxC = MutableStateFlow("105")
    val tempMaxC: StateFlow<String> = _tempMaxC.asStateFlow()

    private val _voltageMin = MutableStateFlow("11.8")
    val voltageMin: StateFlow<String> = _voltageMin.asStateFlow()

    private val _redlineRpm = MutableStateFlow("10500")
    val redlineRpm: StateFlow<String> = _redlineRpm.asStateFlow()

    private val _fuelPriceCop = MutableStateFlow("16000")
    val fuelPriceCop: StateFlow<String> = _fuelPriceCop.asStateFlow()

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

    val activeVehicleProfile: StateFlow<VehicleProfileEntity?> = sessionManager.activeProfile

    init {
        viewModelScope.launch {
            runCatching {
                val prefs = settings.data.first()
                _apiKey.value = withContext(Dispatchers.IO) { loadApiKeyMigrating(prefs[PreferencesKeys.CLAUDE_API_KEY]) }
                _customPidsJson.value = prefs[PreferencesKeys.CUSTOM_PIDS_JSON].orEmpty()
                _customAlertsJson.value = prefs[PreferencesKeys.CUSTOM_ALERTS_JSON].orEmpty()
                _alertsEnabled.value = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true
                _ttsEnabled.value = prefs[PreferencesKeys.ALERT_TTS_ENABLED] ?: true
                _tempMaxC.value = (prefs[PreferencesKeys.ALERT_TEMP_MAX_C] ?: 105).toString()
                _voltageMin.value = (prefs[PreferencesKeys.ALERT_VOLTAGE_MIN] ?: 11.8f).toString()
                _redlineRpm.value = (prefs[PreferencesKeys.ALERT_REDLINE_RPM] ?: 10_500).toString()
                _askVehicleOnStart.value = prefs[PreferencesKeys.ASK_VEHICLE_ON_START] ?: true
                _fuelPriceCop.value =
                    (prefs[PreferencesKeys.FUEL_PRICE_COP_PER_GALLON] ?: 16_000.0).toLong().toString()
                _voiceTemperature.value = prefs[PreferencesKeys.VOICE_TEMPERATURE] ?: true
                _voiceVoltage.value = prefs[PreferencesKeys.VOICE_VOLTAGE] ?: true
                _voiceSpeedCameras.value = prefs[PreferencesKeys.VOICE_SPEED_CAMERAS] ?: true
                _voiceAnomalies.value = prefs[PreferencesKeys.VOICE_ANOMALIES] ?: false
                _voiceMil.value = prefs[PreferencesKeys.VOICE_MIL] ?: false
                _voiceRedline.value = prefs[PreferencesKeys.VOICE_REDLINE] ?: false
                _voiceCustomThresholds.value = prefs[PreferencesKeys.VOICE_CUSTOM_THRESHOLDS] ?: true
                _voiceSport.value = prefs[PreferencesKeys.VOICE_SPORT] ?: true
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

    fun updateApiKey(value: String) {
        _apiKey.value = value
    }

    fun updateCustomPidsJson(value: String) {
        _customPidsJson.value = value
    }

    fun updateCustomAlertsJson(value: String) {
        _customAlertsJson.value = value
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

    fun updateFuelPriceCop(value: String) {
        _fuelPriceCop.value = value
    }

    fun saveFuelPriceCop() {
        viewModelScope.launch {
            val price = _fuelPriceCop.value.toDoubleOrNull()
            if (price == null || price !in 1_000.0..50_000.0) {
                _lastSaveResult.value = SaveResult(false, "Precio fuera de rango (1.000-50.000 COP)")
                return@launch
            }
            val result = runCatching {
                settings.edit { it[PreferencesKeys.FUEL_PRICE_COP_PER_GALLON] = price }
            }
            _lastSaveResult.value = if (result.isSuccess) {
                SaveResult(true, "Precio de combustible actualizado")
            } else {
                SaveResult(false, "Error guardando precio de combustible")
            }
        }
    }

    fun saveAlertSettings() {
        viewModelScope.launch {
            val temp = _tempMaxC.value.toIntOrNull()
            val voltage = _voltageMin.value.toFloatOrNull()
            val redline = _redlineRpm.value.toIntOrNull()
            if (temp == null || temp !in 60..150 ||
                voltage == null || voltage !in 8f..15f ||
                redline == null || redline !in 3_000..20_000
            ) {
                _lastSaveResult.value =
                    SaveResult(false, "Umbrales fuera de rango (temp 60-150, volt 8-15, RPM 3000-20000)")
                return@launch
            }
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
                SaveResult(true, "Alertas actualizadas")
            } else {
                SaveResult(false, "Error guardando alertas")
            }
        }
    }

    fun saveApiKey() {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { secureKeyStore.setClaudeApiKey(_apiKey.value.trim()) }
                // Wipe any plaintext copy left from before encryption existed
                settings.edit { it.remove(PreferencesKeys.CLAUDE_API_KEY) }
            }
            _lastSaveResult.value = if (result.isSuccess) {
                SaveResult(true, "API key guardada (cifrada)")
            } else {
                SaveResult(false, "Error guardando API key")
            }
        }
    }

    /** Reads the key from encrypted storage, migrating a plaintext DataStore copy if found. */
    private suspend fun loadApiKeyMigrating(plaintextLegacy: String?): String {
        val secure = secureKeyStore.getClaudeApiKey()
        if (secure != null) return secure
        if (!plaintextLegacy.isNullOrBlank()) {
            secureKeyStore.setClaudeApiKey(plaintextLegacy)
            runCatching { settings.edit { it.remove(PreferencesKeys.CLAUDE_API_KEY) } }
            return plaintextLegacy
        }
        return ""
    }

    fun saveCustomPids() {
        viewModelScope.launch {
            val json = _customPidsJson.value.trim()
            if (json.isNotEmpty() && !isValidPidJson(json)) {
                _lastSaveResult.value = SaveResult(false, "JSON inválido — revisa el formato")
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
                SaveResult(true, if (count > 0) "$count PIDs custom aplicados" else "PIDs custom limpiados")
            } else {
                SaveResult(false, "Error guardando PIDs custom")
            }
        }
    }

    fun saveCustomAlerts() {
        viewModelScope.launch {
            val json = _customAlertsJson.value.trim()
            if (json.isNotEmpty() && !isValidCustomAlertJson(json)) {
                _lastSaveResult.value = SaveResult(false, "JSON inválido — revisa el formato")
                return@launch
            }
            val result = runCatching {
                settings.edit { it[PreferencesKeys.CUSTOM_ALERTS_JSON] = json }
            }
            if (result.isSuccess) alertsEngine.reloadThresholds()
            _lastSaveResult.value = if (result.isSuccess) {
                val count = if (json.isEmpty()) 0 else JSONArray(json).length()
                SaveResult(true, if (count > 0) "$count alertas personalizadas aplicadas" else "Alertas personalizadas limpiadas")
            } else {
                SaveResult(false, "Error guardando alertas personalizadas")
            }
        }
    }

    fun dismissSaveResult() {
        _lastSaveResult.value = null
    }

    // ── Backup / restore ─────────────────────────────────────────────────────

    private val _backupState = MutableStateFlow(BackupState.IDLE)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.EXPORTING
            val error = runBackupOperation {
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    backupManager.export(out).getOrThrow()
                } ?: error("No se pudo abrir el destino elegido")
            }
            _backupState.value = BackupState.IDLE
            _lastSaveResult.value = if (error == null) {
                SaveResult(true, "Copia de seguridad exportada")
            } else {
                SaveResult(false, "Error exportando copia: ${error.message ?: "desconocido"}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupState.IMPORTING
            val error = runBackupOperation {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    backupManager.import(input).getOrThrow()
                } ?: error("No se pudo abrir el archivo seleccionado")
            }
            if (error == null) {
                _backupState.value = BackupState.RESTARTING_AFTER_IMPORT
            } else {
                _backupState.value = BackupState.IDLE
                _lastSaveResult.value = SaveResult(false, "Error importando copia: ${error.message ?: "desconocido"}")
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
            runCatching { cameraDao.count() }.getOrNull()?.let { count ->
                if (count > 0) _cameraStatus.value = "$count radares guardados"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun downloadSpeedCameras() {
        viewModelScope.launch {
            _cameraStatus.value = "Buscando tu ubicación…"
            val location = runCatching {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }.getOrNull()
            if (location == null) {
                _cameraStatus.value = "Sin ubicación — conecta el GPS o abre Maps un momento"
                return@launch
            }
            _cameraStatus.value = "Descargando radares de OpenStreetMap…"
            runCatching { cameraUpdater.downloadAround(location.latitude, location.longitude) }
                .onSuccess { count ->
                    _cameraStatus.value =
                        "$count radares en 50 km — avisos por voz activos al conducir"
                }
                .onFailure {
                    _cameraStatus.value = "Error descargando (¿internet?) — intenta de nuevo"
                }
        }
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
}
