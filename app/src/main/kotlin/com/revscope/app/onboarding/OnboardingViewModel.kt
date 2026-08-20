package com.revscope.app.onboarding

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
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject

/**
 * Decide si el primer arranque debe mostrar el wizard de onboarding.
 * `onboardingDone` empieza en null (aún cargando) para que el NavGraph no componga
 * su startDestination hasta conocer el valor persistido.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: DataStore<Preferences>,
    private val profileDao: VehicleProfileDao,
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    private val _onboardingDone = MutableStateFlow<Boolean?>(null)
    val onboardingDone: StateFlow<Boolean?> = _onboardingDone.asStateFlow()

    init {
        viewModelScope.launch {
            _onboardingDone.value = runCatching { settings.data.first()[PreferencesKeys.ONBOARDING_DONE] ?: false }
                .onFailure { Timber.w(it, "OnboardingViewModel: failed to load onboarding flag") }
                .getOrDefault(false)
        }
    }

    fun markDone() {
        _onboardingDone.value = true
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.ONBOARDING_DONE] = true } }
                .onFailure { Timber.w(it, "OnboardingViewModel: failed to persist onboarding flag") }
        }
    }

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    fun next() { _step.value = WizardSteps.next(_step.value) }

    fun back() { _step.value = WizardSteps.back(_step.value) }

    fun goTo(step: Int) { _step.value = WizardSteps.clamp(step) }

    fun setGpsOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.GPS_ONLY_MODE] = enabled } }
                .onFailure { Timber.w(it, "OnboardingViewModel: failed to persist gps-only mode") }
        }
    }

    private val _profileCreated = MutableStateFlow(false)
    val profileCreated: StateFlow<Boolean> = _profileCreated.asStateFlow()

    /**
     * Perfil mínimo del wizard: defaults por tipo (los de sub-proyecto C), sin campos avanzados.
     * Marca [profileCreated] de forma optimista y síncrona para bloquear taps repetidos del botón
     * "Crear" antes de que termine el insert; si falla, se revierte para permitir reintentar.
     */
    fun createFirstProfile(name: String, type: String, plate: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _profileCreated.value) return
        _profileCreated.value = true
        val motorcycle = type == "MOTORCYCLE"
        viewModelScope.launch {
            runCatching {
                val profile = VehicleProfileEntity(
                    name = trimmed,
                    type = type,
                    vin = null,
                    enabledPids = JSONArray(listOf("0C", "0D", "05")).toString(),
                    gearRatios = null,
                    createdAt = System.currentTimeMillis(),
                    maxRpm = if (motorcycle) 12_000 else 8_000,
                    redlineRpm = if (motorcycle) 10_500 else 6_500,
                    plate = plate.trim().uppercase(),
                    gearCount = if (motorcycle) 5 else 6,
                )
                val insertedId = profileDao.insert(profile)
                sessionManager.setActiveProfile(profile.copy(id = insertedId))
            }.onFailure {
                Timber.w(it, "OnboardingViewModel: failed to create first profile")
                _profileCreated.value = false
            }
        }
    }

    companion object { const val TOTAL_STEPS = WizardSteps.TOTAL }
}
