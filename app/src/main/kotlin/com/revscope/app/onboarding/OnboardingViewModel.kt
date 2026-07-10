package com.revscope.app.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Decide si el primer arranque debe mostrar la pantalla de permisos.
 * `onboardingDone` empieza en null (aún cargando) para que el NavGraph no componga
 * su startDestination hasta conocer el valor persistido.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: DataStore<Preferences>,
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
}
