package com.revscope.core.obd.workshop

/** Counts how often an O2 sensor's voltage crosses the 0.45V switch threshold. */
object O2SwitchCounter {

    private const val SWITCH_THRESHOLD_V = 0.45
    private const val MIN_ELAPSED_MS = 1_000L
    private const val MS_PER_MINUTE = 60_000.0

    /**
     * @param timestampedVolts samples as (epoch ms, volts), oldest first.
     * @return crossings normalized to a per-minute rate over the samples' actual time span.
     */
    fun perMinute(timestampedVolts: List<Pair<Long, Double>>): Double {
        if (timestampedVolts.size < 2) return 0.0
        val crossings = countCrossings(timestampedVolts)
        val elapsedMs = (timestampedVolts.last().first - timestampedVolts.first().first)
            .coerceAtLeast(MIN_ELAPSED_MS)
        return crossings * MS_PER_MINUTE / elapsedMs
    }

    private fun countCrossings(samples: List<Pair<Long, Double>>): Int {
        var crossings = 0
        for (i in 1 until samples.size) {
            val wasAbove = samples[i - 1].second >= SWITCH_THRESHOLD_V
            val isAbove = samples[i].second >= SWITCH_THRESHOLD_V
            if (wasAbove != isAbove) crossings++
        }
        return crossings
    }
}
