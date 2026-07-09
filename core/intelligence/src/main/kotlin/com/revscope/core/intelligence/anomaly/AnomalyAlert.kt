package com.revscope.core.intelligence.anomaly

/**
 * An anomalous sensor reading detected by [AnomalyDetector].
 *
 * @property pid       PID that triggered the alert ("05", "06", etc.)
 * @property value     Observed value that deviated
 * @property deviation Standard deviations from the baseline mean
 */
sealed class AnomalyAlert(
    open val pid: String,
    open val value: Double,
    open val deviation: Double,
) {
    /** Spoken/displayed Spanish message — consumed by AlertsEngine.announceAnomaly. */
    abstract val message: String

    /** Coolant or intake air temp significantly above session baseline. */
    data class HighTemperature(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation) {
        override val message: String get() = "Temperatura anómala en PID $pid: ${formatValue(value)}"
    }

    /** Short or long-term fuel trim drifting outside normal range — possible vacuum leak,
     *  injector issue, or O2 sensor fault. */
    data class UnusualFuelTrim(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation) {
        override val message: String get() = "Corrección de combustible anómala en PID $pid: ${formatValue(value)}%"
    }

    /** Generic statistical outlier for any other PID. */
    data class AbnormalReading(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation) {
        override val message: String get() = "Lectura anómala en PID $pid: ${formatValue(value)}"
    }
}

// Locale.US pinned so the spoken number is deterministic regardless of device locale
private fun formatValue(value: Double): String {
    val rounded = "%.1f".format(java.util.Locale.US, value)
    return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
}
