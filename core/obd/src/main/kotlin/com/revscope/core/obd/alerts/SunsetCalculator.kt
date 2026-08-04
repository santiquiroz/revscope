package com.revscope.core.obd.alerts

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Atardecer local por ecuación solar NOAA simplificada (declinación aproximada,
 * exactitud ±5 min — suficiente para un aviso de "enciende luces"). Puro y offline.
 */
object SunsetCalculator {

    private const val ZENITH_OFFICIAL_DEG = -0.83 // refracción + radio solar

    /** Epoch ms UTC del atardecer para la fecha de [nowMs] en [latDeg]/[lonDeg]; null en sol de medianoche/noche polar. */
    fun sunsetUtcMillis(nowMs: Long, latDeg: Double, lonDeg: Double): Long? {
        val date = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        val declinationRad = Math.toRadians(-23.44 * cos(2 * PI / 365.0 * (date.dayOfYear + 10)))
        val latRad = Math.toRadians(latDeg)
        val cosHourAngle = (sin(Math.toRadians(ZENITH_OFFICIAL_DEG)) - sin(latRad) * sin(declinationRad)) /
            (cos(latRad) * cos(declinationRad))
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
        val hourAngleDeg = Math.toDegrees(acos(cosHourAngle))
        val solarNoonUtcHours = 12.0 - lonDeg / 15.0
        val sunsetUtcHours = solarNoonUtcHours + hourAngleDeg / 15.0
        val midnightUtcMs = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return midnightUtcMs + (sunsetUtcHours * 3_600_000.0).toLong()
    }
}
