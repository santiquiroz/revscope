package com.revscope.core.obd.legal

/**
 * Motor puro que decide si corresponde anunciar información local de un municipio
 * recién detectado por GPS. Como máximo un anuncio por municipio por día calendario
 * (reutiliza [CityAlertPolicy.dayKey] para la zona horaria — misma semántica que el
 * cooldown de pico y placa, pero sin comparación contra la ciudad del perfil: esta
 * alerta aplica a cualquier municipio nuevo, no solo a los de PicoYPlacaEngine).
 */
object LocalInfoAlertPolicy {

    /**
     * Checks if a municipio should be announced based on the daily registry.
     * Returns true if municipio has never been announced today; false if already announced today.
     * Lazily prunes entries from the map whose dayKey != today.
     */
    fun shouldAnnounce(
        municipio: String,
        announcedToday: MutableMap<String, String>,
        nowMs: Long,
        timeZoneId: String = "America/Bogota",
    ): Boolean {
        val today = CityAlertPolicy.dayKey(nowMs, timeZoneId)

        // Lazy prune: remove entries from previous days
        announcedToday.entries.removeAll { (_, dayKey) -> dayKey != today }

        // Check if municipio was announced today
        return announcedToday[municipio] == null
    }
}
