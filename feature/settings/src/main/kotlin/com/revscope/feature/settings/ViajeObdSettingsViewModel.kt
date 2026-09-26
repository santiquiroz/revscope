package com.revscope.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Ajustes del viaje OBD separado del enlace — aparte de SettingsViewModel, que ya pasa de 1 000 líneas. */
@HiltViewModel
class ViajeObdSettingsViewModel @Inject constructor(
    private val settings: DataStore<Preferences>,
) : ViewModel() {

    val autoTripOnMove: StateFlow<Boolean> = flag(PreferencesKeys.AUTO_TRIP_ON_MOVE, default = true)

    fun updateAutoTripOnMove(value: Boolean) = write(PreferencesKeys.AUTO_TRIP_ON_MOVE, value)

    private fun flag(key: Preferences.Key<Boolean>, default: Boolean): StateFlow<Boolean> =
        settings.data
            .map { it[key] ?: default }
            .catch { emit(default) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, default)

    private fun write(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            runCatching { settings.edit { it[key] = value } }
                .onFailure { Timber.w(it, "ViajeObdSettings: no se pudo guardar ${key.name}") }
        }
    }
}
