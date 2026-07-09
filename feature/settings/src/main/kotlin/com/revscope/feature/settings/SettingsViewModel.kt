package com.revscope.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.secure.SecureKeyStore
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.pid.PidRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: DataStore<Preferences>,
    private val registry: PidRegistry,
    private val alertsEngine: AlertsEngine,
    private val secureKeyStore: SecureKeyStore,
) : ViewModel() {

    data class SaveResult(val success: Boolean, val message: String)

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _customPidsJson = MutableStateFlow("")
    val customPidsJson: StateFlow<String> = _customPidsJson.asStateFlow()

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

    private val _lastSaveResult = MutableStateFlow<SaveResult?>(null)
    val lastSaveResult: StateFlow<SaveResult?> = _lastSaveResult.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val prefs = settings.data.first()
                _apiKey.value = withContext(Dispatchers.IO) { loadApiKeyMigrating(prefs[PreferencesKeys.CLAUDE_API_KEY]) }
                _customPidsJson.value = prefs[PreferencesKeys.CUSTOM_PIDS_JSON].orEmpty()
                _alertsEnabled.value = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true
                _ttsEnabled.value = prefs[PreferencesKeys.ALERT_TTS_ENABLED] ?: true
                _tempMaxC.value = (prefs[PreferencesKeys.ALERT_TEMP_MAX_C] ?: 105).toString()
                _voltageMin.value = (prefs[PreferencesKeys.ALERT_VOLTAGE_MIN] ?: 11.8f).toString()
                _redlineRpm.value = (prefs[PreferencesKeys.ALERT_REDLINE_RPM] ?: 10_500).toString()
            }.onFailure { Timber.w(it, "SettingsViewModel: failed to load settings") }
        }
    }

    fun updateApiKey(value: String) {
        _apiKey.value = value
    }

    fun updateCustomPidsJson(value: String) {
        _customPidsJson.value = value
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

    fun dismissSaveResult() {
        _lastSaveResult.value = null
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
}
