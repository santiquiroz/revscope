package com.revscope.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.telemetry.SamplingPreset
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Tarjeta «Muestreo OBD»: frecuencia del sondeo normal. Aparte de SettingsViewModel (ya > 1 000 líneas). */
@HiltViewModel
class SamplingSettingsViewModel @Inject constructor(
    private val settings: DataStore<Preferences>,
    manager: ObdSessionManager,
) : ViewModel() {

    val ultimaCaptura: StateFlow<ResumenCaptura?> = manager.captura.ultimoResumen

    val maxCapturaMin: StateFlow<Int> = settings.data
        .map { it[PreferencesKeys.FAST_CAPTURE_MAX_MIN] ?: DEFAULT_MAX_CAPTURA_MIN }
        .catch { emit(DEFAULT_MAX_CAPTURA_MIN) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MAX_CAPTURA_MIN)

    fun updateMaxCapturaMin(minutos: Int) {
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.FAST_CAPTURE_MAX_MIN] = minutos.coerceIn(1, 30) } }
                .onFailure { Timber.w(it, "SamplingSettings: no se pudo guardar la duración de captura") }
        }
    }

    val preset: StateFlow<SamplingPreset> = settings.data
        .map { SamplingPreset.desdeClave(it[PreferencesKeys.SAMPLING_PRESET]) }
        .catch { emit(SamplingPreset.DEFAULT) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SamplingPreset.DEFAULT)

    fun updatePreset(value: SamplingPreset) {
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.SAMPLING_PRESET] = value.name } }
                .onFailure { Timber.w(it, "SamplingSettings: no se pudo guardar el preset") }
        }
    }

    companion object {
        const val DEFAULT_MAX_CAPTURA_MIN = 5
        val OPCIONES_MAX_CAPTURA_MIN = listOf(1, 5, 10, 30)
    }
}
