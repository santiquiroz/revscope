package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val READINGS_SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
class LiveMixtureViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val registry: PidRegistry,
) : ViewModel() {

    // The dashboard polls PIDs well outside this screen's rows at up to 10 Hz — without
    // this filter every unrelated reading recomposes the whole mixture list.
    val readings: StateFlow<Map<String, ObdReading>> = sessionManager.readings
        .map { it.filterKeys(MIXTURE_ROW_PIDS::contains) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(READINGS_SUBSCRIPTION_TIMEOUT_MS),
            emptyMap(),
        )

    fun setWorkshopMode(enabled: Boolean) = sessionManager.setWorkshopMode(enabled)

    fun definition(pid: String): PidDefinition? = registry.getDefinition(pid)
}
