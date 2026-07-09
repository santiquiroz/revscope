package com.revscope.app.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Backs the startup vehicle picker sheet. Loads profiles and the ask-on-start flag once. */
@HiltViewModel
class VehiclePickerViewModel @Inject constructor(
    private val profileDao: VehicleProfileDao,
    private val settings: DataStore<Preferences>,
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    private val _profiles = MutableStateFlow<List<VehicleProfileEntity>>(emptyList())
    val profiles: StateFlow<List<VehicleProfileEntity>> = _profiles.asStateFlow()

    private val _askOnStart = MutableStateFlow(true)
    val askOnStart: StateFlow<Boolean> = _askOnStart.asStateFlow()

    val activeProfile: StateFlow<VehicleProfileEntity?> = sessionManager.activeProfile

    init {
        // Both loads land in the same coroutine resumption (no suspension between the two
        // assignments) so Compose collectors see them together — avoids the picker briefly
        // flashing with a stale askOnStart default while profiles has already loaded.
        viewModelScope.launch {
            val loadedProfiles = runCatching { profileDao.observeAll().first() }
                .onFailure { Timber.w(it, "VehiclePickerViewModel: failed to load profiles") }
                .getOrDefault(emptyList())
            val loadedAskOnStart = runCatching { settings.data.first()[PreferencesKeys.ASK_VEHICLE_ON_START] ?: true }
                .onFailure { Timber.w(it, "VehiclePickerViewModel: failed to load ask-on-start") }
                .getOrDefault(true)
            _profiles.value = loadedProfiles
            _askOnStart.value = loadedAskOnStart
        }
    }

    fun select(profile: VehicleProfileEntity) {
        sessionManager.setActiveProfile(profile)
    }

    fun disableAsking() {
        viewModelScope.launch {
            runCatching {
                settings.edit { it[PreferencesKeys.ASK_VEHICLE_ON_START] = false }
            }.onFailure { Timber.w(it, "VehiclePickerViewModel: failed to persist ask-on-start") }
        }
    }
}
