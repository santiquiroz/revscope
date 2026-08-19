package com.revscope.core.common

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.tan

/**
 * Amanecer/atardecer con la ecuación del ángulo horario y declinación aproximada
 * (±20 min, de sobra para conmutar tiles). Todo en UTC: la zona horaria del
 * dispositivo no participa, así el cálculo es puro y testeable.
 */
object SunTimes {

    fun isNight(latDeg: Double, lonDeg: Double, epochMs: Long): Boolean {
        val utc = Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC)
        val dayOfYear = utc.dayOfYear
        val hourUtc = utc.hour + utc.minute / 60.0

        val declRad = Math.toRadians(-23.44 * cos(Math.toRadians(360.0 / 365.0 * (dayOfYear + 10))))
        val latRad = Math.toRadians(latDeg)
        val cosOmega = -tan(latRad) * tan(declRad)
        // Sol de medianoche / noche polar: fuera del trópico extremo no pasa en Colombia,
        // pero el clamp evita NaN si alguien navega en Laponia.
        val omegaDeg = Math.toDegrees(acos(cosOmega.coerceIn(-1.0, 1.0)))

        val solarNoonUtc = 12.0 - lonDeg / 15.0
        val sunriseUtc = solarNoonUtc - omegaDeg / 15.0
        val sunsetUtc = solarNoonUtc + omegaDeg / 15.0

        // La hora UTC puede caer "ayer/mañana" respecto al día solar local; normalizar a [0,24).
        val h = ((hourUtc - sunriseUtc).mod(24.0))
        val dayLength = sunsetUtc - sunriseUtc
        return h > dayLength
    }
}
