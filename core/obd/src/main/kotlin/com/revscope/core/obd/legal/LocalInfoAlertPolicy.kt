package com.revscope.core.obd.legal

/**
 * Motor puro que decide si corresponde anunciar información local de un municipio
 * recién detectado por GPS. Como máximo un anuncio por municipio por día calendario
 * (reutiliza [CityAlertPolicy.dayKey] para la zona horaria — misma semántica que el
 * cooldown de pico y placa, pero sin comparación contra la ciudad del perfil: esta
 * alerta aplica a cualquier municipio nuevo, no solo a los de PicoYPlacaEngine).
 */
object LocalInfoAlertPolicy {

    data class LastAnnouncement(val municipio: String, val dayKey: String)

    fun shouldAnnounce(
        municipio: String,
        lastAnnouncement: LastAnnouncement?,
        nowMs: Long,
        timeZoneId: String = "America/Bogota",
    ): Boolean {
        if (lastAnnouncement == null || lastAnnouncement.municipio != municipio) return true
        return lastAnnouncement.dayKey != CityAlertPolicy.dayKey(nowMs, timeZoneId)
    }
}
