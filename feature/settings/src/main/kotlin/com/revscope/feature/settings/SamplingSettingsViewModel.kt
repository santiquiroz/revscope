package com.revscope.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.telemetry.SamplingPreset
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
) : ViewModel() {

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
}
