package com.revscope.core.common.units

import com.revscope.core.common.i18n.LocaleNumbers
import java.util.Locale

class UnitFormatter(val system: UnitSystem, val locale: Locale) {

    private val imperialDistance: Boolean get() = system != UnitSystem.METRIC
    private val fahrenheit: Boolean get() = system == UnitSystem.US
    private val usGallons: Boolean get() = system == UnitSystem.US

    fun speed(kmh: Double): Double = if (imperialDistance) kmh / KM_PER_MILE else kmh
    fun speedLabel(): String = if (imperialDistance) "mph" else "km/h"
    fun toMetricSpeed(value: Double): Double = if (imperialDistance) value * KM_PER_MILE else value

    fun distance(km: Double): Double = if (imperialDistance) km / KM_PER_MILE else km
    fun distanceLabel(): String = if (imperialDistance) "mi" else "km"
    fun toMetricDistance(value: Double): Double = if (imperialDistance) value * KM_PER_MILE else value

    fun temperature(c: Double): Double = if (fahrenheit) c * F_PER_C + F_OFFSET else c
    fun temperatureLabel(): String = if (fahrenheit) "°F" else "°C"
    fun toMetricTemperature(value: Double): Double = if (fahrenheit) (value - F_OFFSET) / F_PER_C else value

    fun pressure(kpa: Double): Double = if (imperialDistance) kpa / KPA_PER_PSI else kpa
    fun pressureLabel(): String = if (imperialDistance) "psi" else "kPa"
    fun volume(l: Double): Double = if (usGallons) l / LITERS_PER_US_GALLON else l
    fun volumeLabel(): String = if (usGallons) "gal" else "L"
    fun fuelEconomy(kmPerL: Double): Double = when (system) {
        UnitSystem.METRIC -> kmPerL
        UnitSystem.US -> kmPerL * LITERS_PER_US_GALLON / KM_PER_MILE
        UnitSystem.UK -> kmPerL * LITERS_PER_IMPERIAL_GALLON / KM_PER_MILE
    }

    fun fuelEconomyLabel(): String = if (imperialDistance) "mpg" else "km/L"
    fun formatSpeed(kmh: Double): String = format(speed(kmh), 0, speedLabel())
    fun formatDistance(km: Double, decimals: Int = 1): String = format(distance(km), decimals, distanceLabel())
    fun formatTemperature(c: Double): String = format(temperature(c), 0, temperatureLabel())
    fun formatPressure(kpa: Double): String =
        format(pressure(kpa), if (imperialDistance) 1 else 0, pressureLabel())

    fun formatVolume(l: Double, decimals: Int = 1): String = format(volume(l), decimals, volumeLabel())
    fun formatFuelEconomy(kmPerL: Double): String = format(fuelEconomy(kmPerL), 1, fuelEconomyLabel())

    fun shortDistance(meters: Double): Measure =
        if (imperialDistance) imperialShortDistance(meters) else metricShortDistance(meters)

    fun formatShortDistance(meters: Double): String {
        val measure = shortDistance(meters)
        return format(measure.value, shortDistanceDecimals(measure.unit), measure.unit.label)
    }

    private fun metricShortDistance(meters: Double): Measure =
        if (meters < METERS_PER_KM) {
            Measure(roundToStep(meters), DistanceUnit.METERS)
        } else {
            Measure(meters / METERS_PER_KM, DistanceUnit.KILOMETERS)
        }

    private fun imperialShortDistance(meters: Double): Measure =
        if (meters < FEET_MAX_METERS) {
            Measure(roundToStep(meters * FEET_PER_METER), DistanceUnit.FEET)
        } else {
            Measure(meters / METERS_PER_MILE, DistanceUnit.MILES)
        }

    private fun roundToStep(value: Double): Double = Math.round(value / SHORT_DISTANCE_STEP) * SHORT_DISTANCE_STEP

    private fun shortDistanceDecimals(unit: DistanceUnit): Int = when (unit) {
        DistanceUnit.METERS, DistanceUnit.FEET -> 0
        DistanceUnit.KILOMETERS, DistanceUnit.MILES -> 1
    }

    private fun format(value: Double, decimals: Int, label: String): String =
        LocaleNumbers.decimal(value, decimals, locale) + NBSP + label

    private companion object {
        const val KM_PER_MILE = 1.609344
        const val F_PER_C = 9.0 / 5.0
        const val F_OFFSET = 32.0
        const val KPA_PER_PSI = 6.894757
        const val LITERS_PER_US_GALLON = 3.785411784
        const val LITERS_PER_IMPERIAL_GALLON = 4.54609
        const val FEET_PER_METER = 3.28084
        const val METERS_PER_MILE = 1609.344
        const val METERS_PER_KM = 1000.0
        const val FEET_MAX_METERS = 160.9344
        const val SHORT_DISTANCE_STEP = 10.0
        const val NBSP = " "
    }
}
