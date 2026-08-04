package com.revscope.core.obd.road

import com.revscope.core.obd.alerts.AlertsEngine
import javax.inject.Inject
import javax.inject.Singleton

private const val CHECK_THROTTLE_MS = 5 * 60_000L
private const val BREAK_INTERVAL_MS = 2 * 60 * 60_000L
private const val HYDRATION_MIN_RIDE_MS = 90 * 60_000L
private const val HYDRATION_IAT_C = 32.0
private const val IAT_PID = "0F"

/**
 * Coach de fatiga: pausa sugerida cada 2 h de viaje continuo; con calor (IAT alta,
 * proxy de temperatura ambiente) recuerda hidratarse — en moto se pierde hasta
 * 1.5 L/hora y 2% de deshidratación ya degrada el tiempo de reacción.
 */
@Singleton
class FatigueCoach @Inject constructor(
    private val alertsEngine: AlertsEngine,
) {

    @Volatile private var sessionStartMs = 0L
    @Volatile private var lastCheckMs = 0L
    @Volatile private var lastBreakAnnouncedMs = 0L
    @Volatile private var hydrationAnnounced = false

    fun onSessionStart() {
        sessionStartMs = System.currentTimeMillis()
        lastBreakAnnouncedMs = 0L
        hydrationAnnounced = false
    }

    fun onSessionEnd() {
        sessionStartMs = 0L
    }

    /** [intakeAirTempC] = lectura viva del PID 0F si existe (proxy de ambiente). */
    fun onTick(intakeAirTempC: Double?) {
        val start = sessionStartMs
        if (start == 0L) return
        val now = System.currentTimeMillis()
        if (now - lastCheckMs < CHECK_THROTTLE_MS) return
        lastCheckMs = now

        val riding = now - start
        val sinceBreakAnnounce = now - maxOf(lastBreakAnnouncedMs, start)
        if (riding >= BREAK_INTERVAL_MS && sinceBreakAnnounce >= BREAK_INTERVAL_MS) {
            lastBreakAnnouncedMs = now
            alertsEngine.announceFatigue(hydration = false)
            return
        }
        if (!hydrationAnnounced && riding >= HYDRATION_MIN_RIDE_MS &&
            intakeAirTempC != null && intakeAirTempC >= HYDRATION_IAT_C
        ) {
            hydrationAnnounced = true
            alertsEngine.announceFatigue(hydration = true)
        }
    }

    companion object {
        const val PID = IAT_PID
    }
}
