package com.revscope.feature.workshop.taller

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.sesion.ChequeoRegistrado
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.feature.workshop.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale

data class OpcionChequeoBase(val id: Long, val fecha: String, val resumen: String)

data class SesionPorCerrar(val titulo: String, val desde: String)

data class NuevaSesionEstado(
    val cargando: Boolean = true,
    val vehiculo: String? = null,
    val sintomas: Set<Sintoma> = emptySet(),
    val sintomasTexto: String = "",
    val odometro: String = "",
    val odometroDeEcu: Boolean = false,
    val notas: String = "",
    val bases: List<OpcionChequeoBase> = emptyList(),
    val baseElegida: Long? = null,
    val errorOdometro: String? = null,
    val porCerrar: SesionPorCerrar? = null,
    val abriendo: Boolean = false,
    val error: String? = null,
) {
    val puedeAbrir: Boolean get() = vehiculo != null && !abriendo && !cargando
}

internal object OdometroTexto {

    private const val MAXIMO_KM = 2_000_000.0

    fun leer(texto: String, resolver: StringResolver): Result<Double?> {
        val limpio = texto.trim().replace(" ", "").replace(".", "").replace(',', '.')
        if (limpio.isEmpty()) return Result.success(null)
        val km = limpio.toDoubleOrNull()?.takeIf { it in 0.0..MAXIMO_KM }
        return if (km == null) {
            Result.failure(IllegalArgumentException(resolver.get(R.string.workshop_new_session_odometer_error)))
        } else {
            Result.success(km)
        }
    }

    fun escribir(km: Double): String = FormatoTaller.compacto(FormatoTaller.redondear(km, 0))
}

internal object OpcionesChequeoBase {

    fun de(chequeo: ChequeoRegistrado, zona: ZoneId, resolver: StringResolver): OpcionChequeoBase =
        OpcionChequeoBase(chequeo.id, FechasSesion.fechaHora(chequeo.instante, zona, resolver.locale), resumen(chequeo, resolver))

    private fun resumen(chequeo: ChequeoRegistrado, resolver: StringResolver): String {
        val metricas = chequeo.metricas
        return when {
            metricas == null || !metricas.dtcsLeidos ->
                resolver.plural(R.plurals.workshop_new_session_findings, chequeo.items.size, chequeo.items.size)
            metricas.dtcs.isEmpty() -> resolver.get(R.string.workshop_new_session_no_codes)
            else -> resolver.get(R.string.workshop_new_session_codes, metricas.dtcs.joinToString())
        }
    }
}

internal object FechasSesion {
    fun hora(instante: Long, zona: ZoneId, locale: Locale): String {
        if (locale.language == "es") return FechasTaller.hora(instante, zona)
        val formatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter(locale)
        return formatter.format(Instant.ofEpochMilli(instante).atZone(zona))
    }

    fun horaExacta(instante: Long, zona: ZoneId, locale: Locale): String {
        if (locale.language == "es") return FechasTaller.horaExacta(instante, zona)
        val formatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter(locale)
        return formatter.format(Instant.ofEpochMilli(instante).atZone(zona))
    }

    fun dia(instante: Long, zona: ZoneId, locale: Locale): String {
        if (locale.language == "es") return FechasTaller.dia(instante, zona)
        val formatter = DateTimeFormatterBuilder()
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .toFormatter(locale)
        return formatter.format(Instant.ofEpochMilli(instante).atZone(zona))
    }

    fun fecha(instante: Long, zona: ZoneId, locale: Locale): String {
        if (locale.language == "es") return FechasTaller.fecha(instante, zona)
        val formatter = DateTimeFormatterBuilder()
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral(", ")
            .appendValue(ChronoField.YEAR)
            .toFormatter(locale)
        return formatter.format(Instant.ofEpochMilli(instante).atZone(zona))
    }

    fun fechaHora(instante: Long, zona: ZoneId, locale: Locale): String {
        if (locale.language == "es") return FechasTaller.fechaHora(instante, zona)
        return "${fecha(instante, zona, locale)}, ${hora(instante, zona, locale)}"
    }
}
