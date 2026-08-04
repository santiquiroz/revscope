package com.revscope.core.obd.legal

import org.json.JSONObject
import timber.log.Timber
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Motor puro y offline de pico y placa: dado una placa, tipo de vehículo, reglas de ciudad
 * y un instante, determina si hay restricción de circulación. Sin dependencias de Android
 * ni efectos secundarios — fácil de probar con timestamps fijos.
 */
object PicoYPlacaEngine {

    enum class DigitSource { FIRST, LAST }

    /** WEEKDAY_ROTATION: dígito restringido varía por día de semana (Medellín). DATE_PARITY: por par/impar del día del mes (Bogotá). */
    enum class Scheme { WEEKDAY_ROTATION, DATE_PARITY }

    const val ODD_DAY_KEY = "ODD_DAY"
    const val EVEN_DAY_KEY = "EVEN_DAY"

    data class CityRules(
        val cityId: String,
        val displayName: String,
        /** Calendar.DAY_OF_WEEK (2=lunes..6=viernes) → dígitos restringidos ese día. Usado solo por WEEKDAY_ROTATION. */
        val rotation: Map<Int, List<Int>>,
        val startHour: Int,
        val endHour: Int,
        val carDigit: DigitSource,
        val motoDigit: DigitSource,
        val validFromMs: Long,
        val validUntilMs: Long,
        val scheme: Scheme = Scheme.WEEKDAY_ROTATION,
        /** "ODD_DAY"/"EVEN_DAY" → dígitos restringidos ese día del mes. Usado solo por DATE_PARITY. */
        val dateParityRestricted: Map<String, List<Int>> = emptyMap(),
        /** Si true, las motos nunca tienen restricción (ej. Bogotá). */
        val motosExentas: Boolean = false,
        /** Zona horaria donde se evalúa el horario — ciudades fuera de Colombia (reglas IA) la traen en su JSON. */
        val timeZoneId: String = "America/Bogota",
    )

    enum class Status {
        SIN_RESTRICCION,
        RESTRINGIDO_AHORA,
        RESTRINGIDO_HOY_FUERA_DE_HORARIO,
        REGLAS_VENCIDAS,
        SIN_DATOS,
    }

    data class Result(val status: Status, val endHour: Int?, val digitos: List<Int>)

    val MEDELLIN_2026_S1 = CityRules(
        cityId = "medellin",
        displayName = "Medellín",
        rotation = mapOf(
            2 to listOf(1, 7),
            3 to listOf(0, 3),
            4 to listOf(4, 6),
            5 to listOf(5, 9),
            6 to listOf(2, 8),
        ),
        startHour = 5,
        endHour = 20,
        carDigit = DigitSource.LAST,
        motoDigit = DigitSource.FIRST,
        validFromMs = 1_770_008_400_000L, // 2026-02-02 00:00:00 America/Bogota (UTC-5)
        validUntilMs = 1_785_560_399_000L, // 2026-07-31 23:59:59 America/Bogota (UTC-5)
    )

    /** Rotación oficial 2026-S2 (Alcaldía de Medellín): rige 2026-08-03 → 2027-01-29, L-V 5:00-20:00. */
    val MEDELLIN_2026_S2 = CityRules(
        cityId = "medellin",
        displayName = "Medellín",
        rotation = mapOf(
            2 to listOf(5, 8),
            3 to listOf(1, 4),
            4 to listOf(0, 2),
            5 to listOf(3, 6),
            6 to listOf(7, 9),
        ),
        startHour = 5,
        endHour = 20,
        carDigit = DigitSource.LAST,
        motoDigit = DigitSource.FIRST,
        validFromMs = 1_785_733_200_000L, // 2026-08-03 00:00:00 America/Bogota (UTC-5)
        validUntilMs = 1_801_285_199_000L, // 2027-01-29 23:59:59 America/Bogota (UTC-5)
    )

    /** L-V 6:00-21:00, par/impar por último dígito de la placa, motos exentas. Vigencia amplia 2026. */
    val BOGOTA_2026 = CityRules(
        cityId = "bogota",
        displayName = "Bogotá",
        rotation = emptyMap(),
        startHour = 6,
        endHour = 21,
        carDigit = DigitSource.LAST,
        motoDigit = DigitSource.LAST,
        validFromMs = 1_767_243_600_000L, // 2026-01-01 00:00:00 America/Bogota (UTC-5)
        validUntilMs = 1_798_779_599_000L, // 2026-12-31 23:59:59 America/Bogota (UTC-5)
        scheme = Scheme.DATE_PARITY,
        dateParityRestricted = mapOf(
            ODD_DAY_KEY to listOf(6, 7, 8, 9, 0),
            EVEN_DAY_KEY to listOf(1, 2, 3, 4, 5),
        ),
        motosExentas = true,
    )

    fun check(
        plate: String,
        isMotorcycle: Boolean,
        rules: CityRules,
        nowMs: Long,
        timeZoneId: String? = null,
    ): Result {
        if (nowMs !in rules.validFromMs..rules.validUntilMs) {
            return Result(Status.REGLAS_VENCIDAS, null, emptyList())
        }
        if (isMotorcycle && rules.motosExentas) {
            return Result(Status.SIN_RESTRICCION, null, emptyList())
        }
        val zonedNow = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of(timeZoneId ?: rules.timeZoneId))
        if (isWeekend(zonedNow.dayOfWeek)) {
            return Result(Status.SIN_RESTRICCION, null, emptyList())
        }
        return when (rules.scheme) {
            Scheme.WEEKDAY_ROTATION -> checkWeekdayRotation(plate, isMotorcycle, rules, zonedNow)
            Scheme.DATE_PARITY -> checkDateParity(plate, rules, zonedNow)
        }
    }

    private fun checkWeekdayRotation(
        plate: String,
        isMotorcycle: Boolean,
        rules: CityRules,
        zonedNow: ZonedDateTime,
    ): Result {
        val digit = extractDigit(plate, digitSourceFor(isMotorcycle, rules))
            ?: return Result(Status.SIN_DATOS, null, emptyList())
        val restrictedDigits = rules.rotation[zonedNow.dayOfWeek.toCalendarDayOfWeek()].orEmpty()
        return resolveByRestrictedDigits(digit, restrictedDigits, rules, zonedNow)
    }

    /** Bogotá: el último dígito SIEMPRE define la restricción, sin importar el tipo de vehículo. */
    private fun checkDateParity(plate: String, rules: CityRules, zonedNow: ZonedDateTime): Result {
        val digit = extractDigit(plate, DigitSource.LAST)
            ?: return Result(Status.SIN_DATOS, null, emptyList())
        val parityKey = if (zonedNow.dayOfMonth % 2 == 1) ODD_DAY_KEY else EVEN_DAY_KEY
        val restrictedDigits = rules.dateParityRestricted[parityKey].orEmpty()
        return resolveByRestrictedDigits(digit, restrictedDigits, rules, zonedNow)
    }

    private fun resolveByRestrictedDigits(
        digit: Int,
        restrictedDigits: List<Int>,
        rules: CityRules,
        zonedNow: ZonedDateTime,
    ): Result {
        if (digit !in restrictedDigits) {
            return Result(Status.SIN_RESTRICCION, null, restrictedDigits)
        }
        return if (zonedNow.hour in rules.startHour until rules.endHour) {
            Result(Status.RESTRINGIDO_AHORA, rules.endHour, restrictedDigits)
        } else {
            Result(Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO, rules.endHour, restrictedDigits)
        }
    }

    /** Parsea un JSON de reglas de ciudad editable por el usuario. Retorna null si es inválido. */
    fun parseRulesJson(json: String): CityRules? = try {
        val obj = JSONObject(json)
        CityRules(
            cityId = obj.getString("cityId"),
            displayName = obj.getString("displayName"),
            rotation = if (obj.has("rotation")) parseDigitsByKey(obj.getJSONObject("rotation")).mapKeys { it.key.toInt() } else emptyMap(),
            startHour = obj.getInt("startHour"),
            endHour = obj.getInt("endHour"),
            carDigit = DigitSource.valueOf(obj.getString("carDigit")),
            motoDigit = DigitSource.valueOf(obj.getString("motoDigit")),
            validFromMs = obj.getLong("validFromMs"),
            validUntilMs = obj.getLong("validUntilMs"),
            scheme = if (obj.has("scheme")) Scheme.valueOf(obj.getString("scheme")) else Scheme.WEEKDAY_ROTATION,
            dateParityRestricted = if (obj.has("dateParityRestricted")) {
                parseDigitsByKey(obj.getJSONObject("dateParityRestricted"))
            } else {
                emptyMap()
            },
            motosExentas = obj.optBoolean("motosExentas", false),
            timeZoneId = obj.optString("timeZoneId").ifBlank { "America/Bogota" },
        )
    } catch (e: Exception) {
        Timber.e(e, "PicoYPlacaEngine: failed to parse city rules JSON")
        null
    }

    private fun parseDigitsByKey(jsonObj: JSONObject): Map<String, List<Int>> = buildMap {
        jsonObj.keys().forEach { key ->
            val digitsJson = jsonObj.getJSONArray(key)
            put(key, buildList { for (i in 0 until digitsJson.length()) add(digitsJson.getInt(i)) })
        }
    }

    private fun digitSourceFor(isMotorcycle: Boolean, rules: CityRules): DigitSource =
        if (isMotorcycle) rules.motoDigit else rules.carDigit

    /** Primer o último dígito numérico de la placa (ej. moto "NZO28H" → dígitos "2","8"). */
    private fun extractDigit(plate: String, source: DigitSource): Int? {
        val digits = plate.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val char = if (source == DigitSource.FIRST) digits.first() else digits.last()
        return char.digitToInt()
    }

    private fun isWeekend(dayOfWeek: DayOfWeek): Boolean =
        dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    /** java.time DayOfWeek (lunes=1..domingo=7) → Calendar.DAY_OF_WEEK (domingo=1..sábado=7) */
    private fun DayOfWeek.toCalendarDayOfWeek(): Int = (value % 7) + 1
}
