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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Backs the startup vehicle picker sheet. Streams profiles reactively and loads the ask-on-start flag once. */
@HiltViewModel
class VehiclePickerViewModel @Inject constructor(
    private val profileDao: VehicleProfileDao,
    private val settings: DataStore<Preferences>,
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    val profiles: StateFlow<List<VehicleProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _askOnStart = MutableStateFlow(true)
    val askOnStart: StateFlow<Boolean> = _askOnStart.asStateFlow()

    val activeProfile: StateFlow<VehicleProfileEntity?> = sessionManager.activeProfile

    init {
        viewModelScope.launch {
            _askOnStart.value = runCatching { settings.data.first()[PreferencesKeys.ASK_VEHICLE_ON_START] ?: true }
                .onFailure { Timber.w(it, "VehiclePickerViewModel: failed to load ask-on-start") }
                .getOrDefault(true)
        }
    }

    fun select(profile: VehicleProfileEntity) {
        sessionManager.setActiveProfile(profile)
    }

    fun disableAsking() {
        _askOnStart.value = false
        viewModelScope.launch {
            runCatching {
                settings.edit { it[PreferencesKeys.ASK_VEHICLE_ON_START] = false }
            }.onFailure { Timber.w(it, "VehiclePickerViewModel: failed to persist ask-on-start") }
        }
    }
}
