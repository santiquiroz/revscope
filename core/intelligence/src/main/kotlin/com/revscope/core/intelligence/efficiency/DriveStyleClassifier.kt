package com.revscope.core.intelligence.efficiency

import com.revscope.core.obd.model.ObdReading

private const val DEFAULT_REDLINE = 6_500
private const val HIGH_RPM_THRESHOLD_RATIO = 0.80
private const val HARD_ACCEL_THROTTLE_DELTA = 50.0    // % change
private const val HARD_ACCEL_WINDOW_MS = 2_000L
private const val PENALTY_HIGH_RPM_MAX = 40
private const val PENALTY_HARD_ACCEL_PER_EVENT = 3
private const val PENALTY_HARD_ACCEL_MAX = 30
private const val HIGH_LOAD_BASELINE = 40.0
private const val PENALTY_LOAD_MAX = 15

/**
 * Rule-based drive style scorer. Runs in O(1) per reading with no ML overhead.
 *
 * Observes RPM (0C), throttle (11), and engine load (04). At the end of a trip
 * call [score] to get a [TripScore] with an overall 0–100 efficiency index.
 *
 * Works at [IntelligenceTier.MINIMAL] — no ML required.
 */
class DriveStyleClassifier(redlineRpm: Int = DEFAULT_REDLINE) {

    private val highRpmThreshold = redlineRpm * HIGH_RPM_THRESHOLD_RATIO

    private var totalRpmReadings = 0
    private var highRpmReadings = 0
    private var hardAccelerations = 0
    private var totalLoad = 0.0
    private var loadReadings = 0
    private var lastThrottlePct = 0.0
    private var lastThrottleTime = 0L

    fun observe(reading: ObdReading) {
        when (reading.pid) {
            "0C" -> {
                totalRpmReadings++
                if (reading.value >= highRpmThreshold) highRpmReadings++
            }
            "11" -> {
                val now = reading.timestamp
                val delta = reading.value - lastThrottlePct
                if (lastThrottleTime > 0L &&
                    now - lastThrottleTime <= HARD_ACCEL_WINDOW_MS &&
                    delta >= HARD_ACCEL_THROTTLE_DELTA
                ) {
                    hardAccelerations++
                }
                lastThrottlePct = reading.value
                lastThrottleTime = now
            }
            "04" -> {
                totalLoad += reading.value
                loadReadings++
            }
        }
    }

    fun score(): TripScore {
        if (totalRpmReadings == 0) return TripScore.empty()

        val highRpmPct = highRpmReadings.toFloat() / totalRpmReadings
        val avgLoad = if (loadReadings > 0) (totalLoad / loadReadings).toFloat() else 0f

        val penaltyHighRpm = (highRpmPct * PENALTY_HIGH_RPM_MAX).toInt()
        val penaltyHardAcc = (hardAccelerations * PENALTY_HARD_ACCEL_PER_EVENT)
            .coerceAtMost(PENALTY_HARD_ACCEL_MAX)
        val penaltyLoad = ((avgLoad - HIGH_LOAD_BASELINE).coerceAtLeast(0f) /
            (100f - HIGH_LOAD_BASELINE) * PENALTY_LOAD_MAX).toInt()

        val overall = (100 - penaltyHighRpm - penaltyHardAcc - penaltyLoad).coerceIn(0, 100)

        return TripScore(
            overall = overall,
            style = DriveStyle.fromScore(overall),
            highRpmTimePercent = highRpmPct * 100f,
            hardAccelerationCount = hardAccelerations,
            avgEngineLoad = avgLoad,
        )
    }

    fun reset() {
        totalRpmReadings = 0
        highRpmReadings = 0
        hardAccelerations = 0
        totalLoad = 0.0
        loadReadings = 0
        lastThrottlePct = 0.0
        lastThrottleTime = 0L
    }
}
