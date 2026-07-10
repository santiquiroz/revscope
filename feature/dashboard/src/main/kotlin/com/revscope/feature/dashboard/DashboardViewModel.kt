package com.revscope.feature.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MINUTE_MS = 60_000L

@HiltViewModel
class DashboardViewModel @Inject constructor(
    val orchestrator: IntelligenceOrchestrator,
    private val alertsEngine: AlertsEngine,
    sessionManager: ObdSessionManager,
    settings: DataStore<Preferences>,
) : ViewModel() {

    /** Redline used by the shift light — cached in AlertsEngine from DataStore. */
    val redlineRpm: Int get() = alertsEngine.currentRedlineRpm

    val anomalyAlerts = orchestrator.anomalyAlerts
    val tripScore = orchestrator.tripScore

    val gearCalibrated: StateFlow<Boolean> = orchestrator.gearLearner.gearTable
        .map { table -> table.all { it.observationCount >= 30 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Re-emits every minute so the banner re-evaluates across day/pico-y-placa boundaries. */
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MS)
        }
    }

    /** Thin contextual banner ("⚠ SOAT vencido · Pico y placa hasta las 20:00"); null when all clear. */
    val alDiaBanner: StateFlow<String?> = combine(
        sessionManager.activeProfile,
        settings.data.map { it[PreferencesKeys.LICENSE_EXPIRES_AT] },
        settings.data.map { prefs -> prefs[PreferencesKeys.PICO_PLACA_RULES_JSON]?.let(PicoYPlacaEngine::parseRulesJson) },
        minuteTicker,
    ) { profile, licenseExpiresAt, overrideRules, _ ->
        computeAlDiaBanner(profile, licenseExpiresAt, overrideRules)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var gearTableJob: Job? = null

    /**
     * Wires [readings] into the intelligence orchestrator and feeds calibrated gear
     * ratios back into [connectionVm] once [AdaptiveGearLearner] converges.
     *
     * Safe to call multiple times (e.g. on reconnect) — subsequent calls reset the
     * orchestrator session and replace the previous gear-table collector.
     */
    fun startIntelligence(readings: Flow<ObdReading>, connectionVm: ConnectionViewModel) {
        orchestrator.resetTrip()
        orchestrator.start(readings, viewModelScope)

        gearTableJob?.cancel()
        gearTableJob = viewModelScope.launch {
            orchestrator.gearLearner.gearTable.collect { table ->
                val calibrated = table.all { it.observationCount >= 30 }
                if (calibrated) {
                    connectionVm.setGearTable(orchestrator.gearLearner.toRatioTable())
                }
            }
        }
    }

    private fun computeAlDiaBanner(
        profile: VehicleProfileEntity?,
        licenseExpiresAt: Long?,
        overrideRules: PicoYPlacaEngine.CityRules?,
    ): String? {
        if (profile == null) return null
        val documents = DocumentStatusCalculator.fromProfile(profile, licenseExpiresAt)
        val statuses = DocumentStatusCalculator.calculate(documents, overrideRules, System.currentTimeMillis())
        return DocumentStatusCalculator.bannerText(statuses)
    }
}
