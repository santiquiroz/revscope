package com.revscope.core.intelligence

import com.revscope.core.intelligence.anomaly.AnomalyAlert
import com.revscope.core.intelligence.anomaly.AnomalyDetector
import com.revscope.core.intelligence.dtc.DtcExplanation
import com.revscope.core.intelligence.dtc.DtcExplainer
import com.revscope.core.intelligence.efficiency.DriveStyleClassifier
import com.revscope.core.intelligence.efficiency.TripScore
import com.revscope.core.intelligence.gear.AdaptiveGearLearner
import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.CoroutineScope
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
) {
    val gearLearner = AdaptiveGearLearner()

    private val anomalyDetector = AnomalyDetector()
    private val driveStyleClassifier = DriveStyleClassifier()

    private val _anomalyAlerts = MutableSharedFlow<AnomalyAlert>(extraBufferCapacity = 16)
    val anomalyAlerts: SharedFlow<AnomalyAlert> = _anomalyAlerts.asSharedFlow()

    private val _tripScore = MutableStateFlow(TripScore.empty())
    val tripScore: StateFlow<TripScore> = _tripScore.asStateFlow()

    /**
     * Begins observing [readings] and drives all intelligence features.
     * Must be called once per session; cancel [scope] to stop.
     */
    fun start(readings: Flow<ObdReading>, scope: CoroutineScope) {
        scope.launch {
            readings.collect { reading -> processReading(reading) }
        }

        scope.launch {
            while (true) {
                delay(TRIP_SCORE_UPDATE_INTERVAL_MS)
                _tripScore.value = driveStyleClassifier.score()
            }
        }

        Timber.i("IntelligenceOrchestrator: started (tier=$tier)")
    }

    /**
     * Explains a DTC fault code. Falls back gracefully when API key absent or network fails.
     * [context] — recent readings to include in the AI prompt for better diagnosis.
     */
    suspend fun explainDtc(code: String, context: List<ObdReading>): DtcExplanation =
        dtcExplainer.explain(code, context)

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
        }
    }
}
