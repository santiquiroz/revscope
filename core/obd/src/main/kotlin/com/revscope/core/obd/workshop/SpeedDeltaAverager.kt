package com.revscope.core.obd.workshop

/**
 * Running average of the speedometer's over-reading percentage — feeds the
 * "Comparar velocímetros" screen. One sample is meant to be added per GPS fix,
 * paired with the OBD speed at that same instant.
 */
class SpeedDeltaAverager {

    private var sampleCount = 0
    private var sumPercent = 0.0

    /** Cumulative average delta %, or null before the first valid sample. */
    val average: Double?
        get() = if (sampleCount == 0) null else sumPercent / sampleCount

    /** Ignores samples at or below [MIN_GPS_SPEED_KMH] — the % delta is noise near a stop. */
    fun addSample(obdKmh: Double, gpsKmh: Double) {
        if (gpsKmh <= MIN_GPS_SPEED_KMH) return
        sumPercent += deltaPercent(obdKmh, gpsKmh)
        sampleCount += 1
    }

    fun reset() {
        sampleCount = 0
        sumPercent = 0.0
    }

    companion object {
        const val MIN_GPS_SPEED_KMH = 10.0

        fun deltaPercent(obdKmh: Double, gpsKmh: Double): Double =
            (obdKmh - gpsKmh) / gpsKmh * 100.0
    }
}
