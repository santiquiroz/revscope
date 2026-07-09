package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LiveMixtureViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val registry: PidRegistry,
) : ViewModel() {

    val readings: StateFlow<Map<String, ObdReading>> = sessionManager.readings

    fun setWorkshopMode(enabled: Boolean) = sessionManager.setWorkshopMode(enabled)

    fun definition(pid: String): PidDefinition? = registry.getDefinition(pid)
}
