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

    private fun format(value: Double, decimals: Int, label: String): String =
        LocaleNumbers.decimal(value, decimals, locale) + NBSP + label

    private companion object {
        const val KM_PER_MILE = 1.609344
        const val F_PER_C = 9.0 / 5.0
        const val F_OFFSET = 32.0
        const val KPA_PER_PSI = 6.894757
        const val LITERS_PER_US_GALLON = 3.785411784
        const val LITERS_PER_IMPERIAL_GALLON = 4.54609
        const val NBSP = " "
    }
}
