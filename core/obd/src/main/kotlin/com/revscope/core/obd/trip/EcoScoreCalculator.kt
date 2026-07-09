package com.revscope.core.obd.trip

import kotlin.math.roundToInt

private const val HARSH_ACCEL_THRESHOLD_MS2 = 3.0
private const val HARSH_BRAKE_THRESHOLD_MS2 = -4.0
private const val HARSH_ACCEL_PENALTY = 2
private const val HARSH_BRAKE_PENALTY = 3
private const val HIGH_RPM_REDLINE_FRACTION = 0.8
private const val HIGH_RPM_PENALTY_WINDOW_SEC = 30
private const val CRUISE_BAND_MIN_FRACTION = 0.2
private const val CRUISE_BAND_MAX_FRACTION = 0.6
private const val CRUISE_STABILITY_TOLERANCE = 0.1
private const val CRUISE_BONUS_MAX = 10
private const val BASE_SCORE = 100
private const val MS_PER_SECOND = 1_000.0

/**
 * Pure eco-driving score over recorded telemetry — no I/O, fully unit-testable.
 * Events are threshold CROSSINGS (transitions), not per-sample counts, so one sustained
 * spike only penalizes once.
 */
object EcoScoreCalculator {

    data class Desglose(
        val score: Int,
        val aceleradasBruscas: Int,
        val frenadasBruscas: Int,
        val tiempoAltasRpmSeg: Int,
        val bonusCrucero: Int,
    )

    fun calculate(
        accelLongitudinal: List<Double>,
        rpmPoints: List<Pair<Long, Double>>,
        redlineRpm: Int,
    ): Desglose {
        val aceleradas = countRisingCrossings(accelLongitudinal, HARSH_ACCEL_THRESHOLD_MS2)
        val frenadas = countFallingCrossings(accelLongitudinal, HARSH_BRAKE_THRESHOLD_MS2)
        val highRpmSeconds = highRpmSeconds(rpmPoints, redlineRpm)
        val rpmPenalty = highRpmSeconds / HIGH_RPM_PENALTY_WINDOW_SEC
        val bonus = cruiseBonus(rpmPoints, redlineRpm)

        val rawScore = BASE_SCORE -
            aceleradas * HARSH_ACCEL_PENALTY -
            frenadas * HARSH_BRAKE_PENALTY -
            rpmPenalty +
            bonus

        return Desglose(
            score = rawScore.coerceIn(0, 100),
            aceleradasBruscas = aceleradas,
            frenadasBruscas = frenadas,
            tiempoAltasRpmSeg = highRpmSeconds,
            bonusCrucero = bonus,
        )
    }

    /** Counts low→over-threshold transitions (rising edge crossing [threshold]). */
    private fun countRisingCrossings(values: List<Double>, threshold: Double): Int {
        var count = 0
        for (i in 1 until values.size) {
            if (values[i - 1] <= threshold && values[i] > threshold) count++
        }
        return count
    }

    /** Counts high→under-threshold transitions (falling edge crossing [threshold]). */
    private fun countFallingCrossings(values: List<Double>, threshold: Double): Int {
        var count = 0
        for (i in 1 until values.size) {
            if (values[i - 1] >= threshold && values[i] < threshold) count++
        }
        return count
    }

    /** Seconds spent above 80% of redline, via trapezoidal (average-crosses-threshold) integration. */
    private fun highRpmSeconds(rpmPoints: List<Pair<Long, Double>>, redlineRpm: Int): Int {
        if (rpmPoints.size < 2) return 0
        val threshold = redlineRpm * HIGH_RPM_REDLINE_FRACTION
        var highMs = 0L
        for (i in 1 until rpmPoints.size) {
            val (prevMs, prevRpm) = rpmPoints[i - 1]
            val (currMs, currRpm) = rpmPoints[i]
            val dtMs = currMs - prevMs
            if (dtMs <= 0) continue
            if ((prevRpm + currRpm) / 2.0 > threshold) highMs += dtMs
        }
        return (highMs / MS_PER_SECOND).toInt()
    }

    /**
     * Fraction of trip time spent in a stable cruise (RPM within 20-60% of redline and
     * changing less than 10% sample-to-sample), scaled to a 0..10 bonus.
     */
    private fun cruiseBonus(rpmPoints: List<Pair<Long, Double>>, redlineRpm: Int): Int {
        if (rpmPoints.size < 2) return 0
        val totalMs = rpmPoints.last().first - rpmPoints.first().first
        if (totalMs <= 0) return 0
        val lowBand = redlineRpm * CRUISE_BAND_MIN_FRACTION
        val highBand = redlineRpm * CRUISE_BAND_MAX_FRACTION
        var cruiseMs = 0L
        for (i in 1 until rpmPoints.size) {
            val (prevMs, prevRpm) = rpmPoints[i - 1]
            val (currMs, currRpm) = rpmPoints[i]
            val dtMs = currMs - prevMs
            if (dtMs <= 0) continue
            val inBand = prevRpm in lowBand..highBand && currRpm in lowBand..highBand
            val stable = currRpm != 0.0 && kotlin.math.abs(currRpm - prevRpm) <= CRUISE_STABILITY_TOLERANCE * currRpm
            if (inBand && stable) cruiseMs += dtMs
        }
        val fraction = cruiseMs.toDouble() / totalMs
        return (fraction * CRUISE_BONUS_MAX).roundToInt().coerceIn(0, CRUISE_BONUS_MAX)
    }
}
