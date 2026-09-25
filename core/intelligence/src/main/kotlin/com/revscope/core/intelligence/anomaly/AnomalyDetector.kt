package com.revscope.core.intelligence.anomaly

import com.revscope.core.obd.model.ObdReading
import kotlin.math.abs
import kotlin.math.sqrt

private const val BASELINE_MIN_SAMPLES = 200L  // ~2 min at ~2 Hz per PID
private const val ANOMALY_SIGMA = 3.0           // flag readings > 3σ from mean
private const val MIN_STDDEV = 0.01             // below this the signal is constant — nothing to flag
private const val COOLANT_PID = "05"
private const val OPERATING_COOLANT_C = 70.0

// Values that depend on engine temperature: a cold start would teach a baseline that is not "normal"
private val ENGINE_TEMPERATURE_PIDS = setOf("05", "06", "07", "08", "09", "0F", "46")

/**
 * Online statistical anomaly detector using Welford's one-pass algorithm.
 *
 * For each PID, maintains a running mean and variance. Once enough samples have
 * been collected ([BASELINE_MIN_SAMPLES]), it flags readings that fall more than
 * [ANOMALY_SIGMA] standard deviations from the mean of the baseline as it was
 * before that reading.
 *
 * Engine-temperature dependent PIDs ([ENGINE_TEMPERATURE_PIDS]) are ignored — no
 * baseline, no alerts — until the last coolant reading (PID 05) is at least
 * [OPERATING_COOLANT_C], so a cold start never becomes the "normal" to compare against.
 *
 * All state is in-memory (per session). [reset] between sessions. [observe] and
 * [reset] are thread-safe.
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

    private val lock = Any()
    private val baselines = mutableMapOf<String, Baseline>()
    private var lastCoolantC: Double? = null

    /**
     * Observes [reading] and returns an [AnomalyAlert] if the value deviates
     * significantly from the established baseline, or null otherwise.
     */
    fun observe(reading: ObdReading): AnomalyAlert? = synchronized(lock) {
        if (reading.pid == COOLANT_PID) lastCoolantC = reading.value
        if (!isBaselineAllowed(reading.pid)) return null

        val previous = baselines[reading.pid] ?: Baseline()
        baselines[reading.pid] = previous.update(reading.value)
        return deviationFrom(previous, reading.value)?.let { sigma -> alertFor(reading, sigma) }
    }

    fun reset() = synchronized(lock) {
        baselines.clear()
        lastCoolantC = null
    }

    private fun isBaselineAllowed(pid: String): Boolean =
        pid !in ENGINE_TEMPERATURE_PIDS || isEngineWarm()

    private fun isEngineWarm(): Boolean {
        val coolant = lastCoolantC ?: return false
        return coolant >= OPERATING_COOLANT_C
    }

    private fun deviationFrom(baseline: Baseline, value: Double): Double? {
        if (baseline.count < BASELINE_MIN_SAMPLES) return null
        if (baseline.stddev < MIN_STDDEV) return null
        val sigma = abs(value - baseline.mean) / baseline.stddev
        return sigma.takeIf { it >= ANOMALY_SIGMA }
    }

    private fun alertFor(reading: ObdReading, sigma: Double): AnomalyAlert = when (reading.pid) {
        "05", "0F", "46" -> AnomalyAlert.HighTemperature(reading.pid, reading.value, sigma)
        "06", "07", "08", "09" -> AnomalyAlert.UnusualFuelTrim(reading.pid, reading.value, sigma)
        else -> AnomalyAlert.AbnormalReading(reading.pid, reading.value, sigma)
    }
}
