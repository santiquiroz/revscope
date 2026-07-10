package com.revscope.core.obd.legal

import java.time.Instant
import java.time.ZoneId

/**
 * Motor puro que decide si corresponde anunciar por voz que la ciudad detectada por GPS
 * tiene pico y placa vigente para la placa activa. Sin dependencias de Android.
 *
 * Solo anuncia cuando la ciudad detectada es DISTINTA a la del perfil (la propia ya se
 * muestra en Al día / banner) y como máximo una vez por ciudad por día calendario.
 */
object CityAlertPolicy {

    data class LastAnnouncement(val cityId: String, val dayKey: String)

    private val ANNOUNCEABLE_STATUSES = setOf(
        PicoYPlacaEngine.Status.RESTRINGIDO_AHORA,
        PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO,
    )

    fun shouldAnnounce(
        detectedCityId: String,
        profileCityId: String?,
        status: PicoYPlacaEngine.Status,
        lastAnnouncement: LastAnnouncement?,
        nowMs: Long,
        timeZoneId: String = "America/Bogota",
    ): Boolean {
        if (detectedCityId == profileCityId) return false
        if (status !in ANNOUNCEABLE_STATUSES) return false
        return !alreadyAnnouncedToday(detectedCityId, lastAnnouncement, nowMs, timeZoneId)
    }

    /** Clave de día calendario en [timeZoneId], usada como cooldown por ciudad+día. */
    fun dayKey(nowMs: Long, timeZoneId: String = "America/Bogota"): String =
        Instant.ofEpochMilli(nowMs).atZone(ZoneId.of(timeZoneId)).toLocalDate().toString()

    private fun alreadyAnnouncedToday(
        cityId: String,
        lastAnnouncement: LastAnnouncement?,
        nowMs: Long,
        timeZoneId: String,
    ): Boolean {
        if (lastAnnouncement == null || lastAnnouncement.cityId != cityId) return false
        return lastAnnouncement.dayKey == dayKey(nowMs, timeZoneId)
    }
}
