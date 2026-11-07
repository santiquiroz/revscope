package com.revscope.core.common.units

// Las lecturas OBD llegan en métrico con su etiqueta; aquí se pasan al sistema elegido sin tocar lo guardado.
fun UnitFormatter.fromMetric(value: Double, metricUnit: String): Double = when (metricUnit) {
    METRIC_SPEED -> speed(value)
    METRIC_TEMPERATURE -> temperature(value)
    METRIC_PRESSURE -> pressure(value)
    METRIC_DISTANCE -> distance(value)
    else -> value
}

fun UnitFormatter.labelForMetric(metricUnit: String): String = when (metricUnit) {
    METRIC_SPEED -> speedLabel()
    METRIC_TEMPERATURE -> temperatureLabel()
    METRIC_PRESSURE -> pressureLabel()
    METRIC_DISTANCE -> distanceLabel()
    else -> metricUnit
}

fun UnitFormatter.formatMetric(value: Double, metricUnit: String): String? = when (metricUnit) {
    METRIC_SPEED -> formatSpeed(value)
    METRIC_TEMPERATURE -> formatTemperature(value)
    METRIC_PRESSURE -> formatPressure(value)
    METRIC_DISTANCE -> formatDistance(value)
    else -> null
}

private const val METRIC_SPEED = "km/h" // i18n-ignore
private const val METRIC_TEMPERATURE = "°C" // i18n-ignore
private const val METRIC_PRESSURE = "kPa" // i18n-ignore
private const val METRIC_DISTANCE = "km" // i18n-ignore
