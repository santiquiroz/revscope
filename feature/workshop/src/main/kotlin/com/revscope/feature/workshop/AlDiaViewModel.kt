package com.revscope.feature.workshop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AlDiaViewModel @Inject constructor(
    sessionManager: ObdSessionManager,
    private val settings: DataStore<Preferences>,
) : ViewModel() {

    val activeProfile: StateFlow<VehicleProfileEntity?> = sessionManager.activeProfile

    private val licenseExpiresAtFlow: Flow<Long?> =
        settings.data.map { it[PreferencesKeys.LICENSE_EXPIRES_AT] }

    private val rulesFlow: Flow<PicoYPlacaEngine.CityRules> = settings.data.map { prefs ->
        prefs[PreferencesKeys.PICO_PLACA_RULES_JSON]
            ?.let(PicoYPlacaEngine::parseRulesJson)
            ?: PicoYPlacaEngine.MEDELLIN_2026_S1
    }

    val licenseExpiresAt: StateFlow<Long?> =
        licenseExpiresAtFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val docStatuses: StateFlow<List<DocumentStatusCalculator.DocStatus>> =
        combine(activeProfile, licenseExpiresAtFlow, rulesFlow, ::calculateStatuses)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun calculateStatuses(
        profile: VehicleProfileEntity?,
        license: Long?,
        rules: PicoYPlacaEngine.CityRules,
    ): List<DocumentStatusCalculator.DocStatus> {
        if (profile == null) return emptyList()
        val documents = DocumentStatusCalculator.fromProfile(profile, license)
        return DocumentStatusCalculator.calculate(documents, rules, System.currentTimeMillis())
    }

    fun setLicenseExpiresAt(value: Long?) {
        viewModelScope.launch {
            runCatching {
                settings.edit { prefs ->
                    if (value != null) prefs[PreferencesKeys.LICENSE_EXPIRES_AT] = value
                    else prefs.remove(PreferencesKeys.LICENSE_EXPIRES_AT)
                }
            }.onFailure { Timber.w(it, "AlDiaViewModel: failed to persist license expiry") }
        }
    }
}
