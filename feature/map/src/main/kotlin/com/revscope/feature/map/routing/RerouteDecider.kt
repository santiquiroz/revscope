package com.revscope.feature.map.routing

/**
 * Decide **cuándo** re-pedir la ruta a OSRM tras un desvío. Puro y determinístico, como
 * ManeuverAnnouncer: mismo tren de estados, mismas decisiones.
 *
 * Exige desvío sostenido para no recalcular por ruido de GPS, y un cooldown para no
 * castigar al OSRM público (es gratis; una petición por segundo sería abusar).
 */
class RerouteDecider(
    private val sustainedMs: Long = SUSTAINED_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
) {

    private var offRouteSinceMs: Long = NEVER
    private var lastRerouteAtMs: Long = NEVER

    fun shouldReroute(offRoute: Boolean, nowMs: Long): Boolean {
        if (!offRoute) {
            offRouteSinceMs = NEVER
            return false
        }
        if (offRouteSinceMs == NEVER) offRouteSinceMs = nowMs
        if (nowMs - offRouteSinceMs < sustainedMs) return false
        if (lastRerouteAtMs != NEVER && nowMs - lastRerouteAtMs < cooldownMs) return false
        lastRerouteAtMs = nowMs
        return true
    }

    fun reset() {
        offRouteSinceMs = NEVER
        lastRerouteAtMs = NEVER
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
        const val SUSTAINED_MS = 3_000L
        const val COOLDOWN_MS = 10_000L
    }
}
