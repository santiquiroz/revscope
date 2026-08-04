package com.revscope.core.obd.alerts

import javax.inject.Inject
import javax.inject.Singleton

private const val CHECK_THROTTLE_MS = 5 * 60_000L
private const val WARN_WINDOW_MS = 25 * 60_000L
private const val DAY_MS = 86_400_000L

/**
 * Aviso hablado al acercarse el atardecer con un viaje activo — la ventana 3-9 pm
 * concentra ~42% de los accidentes fatales de moto; el ocaso es el pico de riesgo.
 * Solo recibe fixes durante una sesión, así que nunca suena con el vehículo guardado.
 */
@Singleton
class SunsetAlerter @Inject constructor(
    private val alertsEngine: AlertsEngine,
) {

    @Volatile private var lastCheckMs = 0L
    @Volatile private var lastAnnouncedDay = -1L

    fun onGpsFix(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        if (now - lastCheckMs < CHECK_THROTTLE_MS) return
        lastCheckMs = now
        val today = now / DAY_MS
        if (today == lastAnnouncedDay) return
        val sunsetMs = SunsetCalculator.sunsetUtcMillis(now, latitude, longitude) ?: return
        val untilSunset = sunsetMs - now
        if (untilSunset in 0..WARN_WINDOW_MS) {
            lastAnnouncedDay = today
            alertsEngine.announceSunset((untilSunset / 60_000L).toInt().coerceAtLeast(1))
        }
    }
}
