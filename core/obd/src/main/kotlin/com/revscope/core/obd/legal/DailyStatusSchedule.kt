package com.revscope.core.obd.legal

import java.time.Instant
import java.time.ZoneId

/**
 * Cálculo puro del horario del aviso diario "Vehículo al día". Sin Android, sin I/O:
 * el disparo real lo agenda [DailyStatusScheduler] y el aviso lo postea
 * [DailyStatusNotifier].
 */
object DailyStatusSchedule {

    const val HOUR = 5
    const val MINUTE = 30
    const val ZONE_ID = "America/Bogota"

    /** Instante de las próximas [HOUR]:[MINUTE] en [zoneId] — hoy si aún no pasan, si no mañana. */
    fun nextTriggerAtMs(nowMs: Long, zoneId: String = ZONE_ID): Long {
        val zone = ZoneId.of(zoneId)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val todayTarget = now.withHour(HOUR).withMinute(MINUTE).withSecond(0).withNano(0)
        val next = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    /** Día calendario local de [atMs] — la clave de deduplicación del aviso. */
    fun epochDayAt(atMs: Long, zoneId: String = ZONE_ID): Long =
        Instant.ofEpochMilli(atMs).atZone(ZoneId.of(zoneId)).toLocalDate().toEpochDay()

    /**
     * El aviso se postea una sola vez por día calendario: la alarma exacta es el disparo
     * primario y el trabajo periódico de WorkManager queda como red de seguridad, así que
     * ambos pueden coincidir el mismo día.
     */
    fun shouldNotify(lastNotifiedEpochDay: Long?, nowMs: Long, zoneId: String = ZONE_ID): Boolean =
        lastNotifiedEpochDay != epochDayAt(nowMs, zoneId)
}
