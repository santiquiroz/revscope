package com.revscope.core.obd.legal

import org.json.JSONObject
import timber.log.Timber
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Motor puro y offline de pico y placa: dado una placa, tipo de vehículo, reglas de ciudad
 * y un instante, determina si hay restricción de circulación. Sin dependencias de Android
 * ni efectos secundarios — fácil de probar con timestamps fijos.
 */
object PicoYPlacaEngine {

    enum class DigitSource { FIRST, LAST }

    data class CityRules(
        val cityId: String,
        val displayName: String,
        /** Calendar.DAY_OF_WEEK (2=lunes..6=viernes) → dígitos restringidos ese día */
        val rotation: Map<Int, List<Int>>,
        val startHour: Int,
        val endHour: Int,
        val carDigit: DigitSource,
        val motoDigit: DigitSource,
        val validFromMs: Long,
        val validUntilMs: Long,
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

    fun check(
        plate: String,
        isMotorcycle: Boolean,
        rules: CityRules,
        nowMs: Long,
        timeZoneId: String = "America/Bogota",
    ): Result {
        if (nowMs !in rules.validFromMs..rules.validUntilMs) {
            return Result(Status.REGLAS_VENCIDAS, null, emptyList())
        }
        val zonedNow = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of(timeZoneId))
        if (isWeekend(zonedNow.dayOfWeek)) {
            return Result(Status.SIN_RESTRICCION, null, emptyList())
        }
        val digit = extractDigit(plate, digitSourceFor(isMotorcycle, rules))
            ?: return Result(Status.SIN_DATOS, null, emptyList())
        val restrictedDigits = rules.rotation[zonedNow.dayOfWeek.toCalendarDayOfWeek()].orEmpty()
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
            rotation = parseRotation(obj.getJSONObject("rotation")),
            startHour = obj.getInt("startHour"),
            endHour = obj.getInt("endHour"),
            carDigit = DigitSource.valueOf(obj.getString("carDigit")),
            motoDigit = DigitSource.valueOf(obj.getString("motoDigit")),
            validFromMs = obj.getLong("validFromMs"),
            validUntilMs = obj.getLong("validUntilMs"),
        )
    } catch (e: Exception) {
        Timber.e(e, "PicoYPlacaEngine: failed to parse city rules JSON")
        null
    }

    private fun parseRotation(rotationObj: JSONObject): Map<Int, List<Int>> = buildMap {
        rotationObj.keys().forEach { key ->
            val digitsJson = rotationObj.getJSONArray(key)
            put(key.toInt(), buildList { for (i in 0 until digitsJson.length()) add(digitsJson.getInt(i)) })
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
