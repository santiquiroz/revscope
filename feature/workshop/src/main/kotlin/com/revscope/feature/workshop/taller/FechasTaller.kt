package com.revscope.feature.workshop.taller

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

// Meses propios: el nombre corto del mes no depende de los datos de locale del teléfono ni del JDK de los tests.
internal object FechasTaller {

    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    fun hora(instante: Long, zona: ZoneId): String =
        en(instante, zona).let { String.format(Locale.ROOT, "%02d:%02d", it.hour, it.minute) }

    fun horaExacta(instante: Long, zona: ZoneId): String =
        en(instante, zona).let { String.format(Locale.ROOT, "%02d:%02d:%02d", it.hour, it.minute, it.second) }

    fun dia(instante: Long, zona: ZoneId): String = en(instante, zona).let { "${it.dayOfMonth} ${MESES[it.monthValue - 1]}" }

    fun fecha(instante: Long, zona: ZoneId): String = en(instante, zona).let { "${dia(instante, zona)} ${it.year}" }

    fun fechaHora(instante: Long, zona: ZoneId): String = "${fecha(instante, zona)}, ${hora(instante, zona)}"

    fun mismoDia(a: Long, b: Long, zona: ZoneId): Boolean = en(a, zona).toLocalDate() == en(b, zona).toLocalDate()

    private fun en(instante: Long, zona: ZoneId): ZonedDateTime = Instant.ofEpochMilli(instante).atZone(zona)
}
