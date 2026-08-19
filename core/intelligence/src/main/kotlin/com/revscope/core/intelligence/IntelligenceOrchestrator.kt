package com.revscope.core.intelligence

import com.revscope.core.intelligence.anomaly.AnomalyAlert
import com.revscope.core.intelligence.anomaly.AnomalyDetector
import com.revscope.core.intelligence.dtc.DtcExplanation
import com.revscope.core.intelligence.dtc.DtcExplainer
import com.revscope.core.intelligence.efficiency.DriveStyleClassifier
import com.revscope.core.intelligence.efficiency.TripScore
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.intelligence.gear.AdaptiveGearLearner
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TRIP_SCORE_UPDATE_INTERVAL_MS = 30_000L

/**
 * Orchestrates all AI/ML intelligence features for a single telemetry session.
 *
 * Architecture note: lives in :core:intelligence, not :core:obd, to avoid a
 * circular dependency. The app module (or a feature ViewModel) wires it to
 * [ConnectionViewModel] by:
 *   1. Feeding [start] with the readings [Flow] from ConnectionViewModel.
 *   2. Observing [gearLearner.gearTable] and calling ConnectionViewModel.setGearTable()
 *      once the learner has calibrated.
 *
 * Active features depend on [tier]:
 *  MINIMAL   → DriveStyleClassifier only (rule-based, O(1) per reading)
 *  ON_DEVICE → + AnomalyDetector (Welford statistics, no network)
 *  ON_DEVICE → + AdaptiveGearLearner (EMA clustering, no network)
 *  FULL      → + DtcExplainer via Claude API (requires API key)
 */
class IntelligenceOrchestrator(
    val tier: IntelligenceTier,
    private val dtcExplainer: DtcExplainer,
    private val alertsEngine: AlertsEngine,
) {
    val gearLearner = AdaptiveGearLearner()

    private val anomalyDetector = AnomalyDetector()
    private val driveStyleClassifier = DriveStyleClassifier()

    private val _anomalyAlerts = MutableSharedFlow<AnomalyAlert>(extraBufferCapacity = 16)
    val anomalyAlerts: SharedFlow<AnomalyAlert> = _anomalyAlerts.asSharedFlow()

    private val _tripScore = MutableStateFlow(TripScore.empty())
    val tripScore: StateFlow<TripScore> = _tripScore.asStateFlow()

    private var readingsJob: Job? = null
    private var scoreJob: Job? = null

    /**
     * Begins observing [readings] and drives all intelligence features.
     * Idempotent: calling again (e.g. on reconnect) cancels the previous session's
     * collectors before launching new ones, so processing is never duplicated.
     *
     * [gearCount]/[vehicleType] come from the active vehicle profile — this orchestrator is
     * a Hilt singleton built before any profile is known, so [gearLearner] only learns the
     * real gearCount/type here, at session start, via [AdaptiveGearLearner.reconfigure].
     */
    fun start(
        readings: Flow<ObdReading>,
        scope: CoroutineScope,
        gearCount: Int = 6,
        vehicleType: VehicleType = VehicleType.CAR,
    ) {
        readingsJob?.cancel()
        scoreJob?.cancel()
        gearLearner.reconfigure(gearCount, vehicleType)

        readingsJob = scope.launch {
            readings.collect { reading -> processReading(reading) }
        }

        scoreJob = scope.launch {
            while (true) {
                delay(TRIP_SCORE_UPDATE_INTERVAL_MS)
                _tripScore.value = driveStyleClassifier.score()
            }
        }

        Timber.i("IntelligenceOrchestrator: started (tier=$tier)")
    }

    /** Cancela colectores y el ticker de score — llamar al desconectar, no solo al morir el VM. */
    fun stop() {
        readingsJob?.cancel()
        readingsJob = null
        scoreJob?.cancel()
        scoreJob = null
    }

    /**
     * Explains a DTC fault code. Falls back gracefully when API key absent or network fails.
     * [context] — recent readings to include in the AI prompt for better diagnosis.
     */
    suspend fun explainDtc(
        code: String,
        context: List<ObdReading>,
        freezeFrame: List<Pair<String, String>> = emptyList(),
    ): DtcExplanation = dtcExplainer.explain(code, context, freezeFrame)

    /** Finalizes and returns the current trip score without resetting state. */
    fun currentScore(): TripScore = driveStyleClassifier.score()

    /** Resets all per-trip accumulators. Call at session end before [start] of a new session. */
    fun resetTrip() {
        driveStyleClassifier.reset()
        anomalyDetector.reset()
        _tripScore.value = TripScore.empty()
    }

    private suspend fun processReading(reading: ObdReading) {
        driveStyleClassifier.observe(reading)

        if (tier == IntelligenceTier.MINIMAL) return

        gearLearner.observe(reading)

        val alert = anomalyDetector.observe(reading)
        if (alert != null) {
            Timber.d("IntelligenceOrchestrator: anomaly detected — $alert")
            _anomalyAlerts.emit(alert)
            // Single app-wide Hilt singleton — the one non-duplicating place to speak anomalies,
            // regardless of which (if any) screen is collecting anomalyAlerts.
            alertsEngine.announceAnomaly(anomalyAlertKey(alert), alert.message)
        }
    }

    /** Stable cooldown key per anomaly subtype+PID — the message text embeds the live value. */
    private fun anomalyAlertKey(alert: AnomalyAlert): String =
        "${alert::class.simpleName}:${alert.pid}"
}
