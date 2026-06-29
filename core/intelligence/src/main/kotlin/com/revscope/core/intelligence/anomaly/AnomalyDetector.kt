package com.revscope.core.intelligence.anomaly

import com.revscope.core.obd.model.ObdReading
import kotlin.math.abs
import kotlin.math.sqrt

private const val BASELINE_MIN_SAMPLES = 200L  // ~2 min at ~2 Hz per PID
private const val ANOMALY_SIGMA = 3.0           // flag readings > 3σ from mean

/**
 * Online statistical anomaly detector using Welford's one-pass algorithm.
 *
 * For each PID, maintains a running mean and variance. Once enough samples have
 * been collected ([BASELINE_MIN_SAMPLES]), it flags readings that fall more than
 * [ANOMALY_SIGMA] standard deviations from the mean.
 *
 * All state is in-memory (per session). Reset [reset] between sessions if the
 * vehicle's operating conditions change substantially (cold start vs highway).
 */
class AnomalyDetector {

    private data class Baseline(
        val mean: Double = 0.0,
        val m2: Double = 0.0,    // sum of squared deviations (Welford accumulator)
        val count: Long = 0L,
    ) {
        val variance: Double get() = if (count < 2) 0.0 else m2 / (count - 1)
        val stddev: Double get() = sqrt(variance)

        fun update(value: Double): Baseline {
            val n = count + 1
            val delta = value - mean
            val newMean = mean + delta / n
            val delta2 = value - newMean
            return copy(mean = newMean, m2 = m2 + delta * delta2, count = n)
        }
    }

    private val baselines = mutableMapOf<String, Baseline>()

    /**
     * Observes [reading] and returns an [AnomalyAlert] if the value deviates
     * significantly from the established baseline, or null otherwise.
     */
    fun observe(reading: ObdReading): AnomalyAlert? {
        val baseline = baselines.getOrPut(reading.pid) { Baseline() }
        val updated = baseline.update(reading.value)
        baselines[reading.pid] = updated

        if (updated.count < BASELINE_MIN_SAMPLES) return null
        if (updated.stddev < 0.01) return null   // constant signal — nothing to flag

        val sigma = abs(reading.value - updated.mean) / updated.stddev
        if (sigma < ANOMALY_SIGMA) return null

        return when (reading.pid) {
            "05", "0F", "46" -> AnomalyAlert.HighTemperature(reading.pid, reading.value, sigma)
            "06", "07", "08", "09" -> AnomalyAlert.UnusualFuelTrim(reading.pid, reading.value, sigma)
            else -> AnomalyAlert.AbnormalReading(reading.pid, reading.value, sigma)
        }
    }

    fun reset() = baselines.clear()
}
