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
    /** Coolant or intake air temp significantly above session baseline. */
    data class HighTemperature(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation)

    /** Short or long-term fuel trim drifting outside normal range — possible vacuum leak,
     *  injector issue, or O2 sensor fault. */
    data class UnusualFuelTrim(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation)

    /** Generic statistical outlier for any other PID. */
    data class AbnormalReading(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation)
}
