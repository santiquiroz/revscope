package com.revscope.core.intelligence.anomaly

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.intelligence.R
import java.text.DecimalFormatSymbols
import java.util.Locale

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
    /** Spoken/displayed message in the app language — consumed by AlertsEngine.announceAnomaly. */
    abstract fun message(resolver: StringResolver): String

    /** Coolant or intake air temp significantly above session baseline. */
    data class HighTemperature(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation) {
        override fun message(resolver: StringResolver): String =
            resolver.get(R.string.ai_anomaly_high_temperature, pid, formatValue(value, resolver.locale))
    }

    /** Short or long-term fuel trim drifting outside normal range — possible vacuum leak,
     *  injector issue, or O2 sensor fault. */
    data class UnusualFuelTrim(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation) {
        override fun message(resolver: StringResolver): String =
            resolver.get(R.string.ai_anomaly_fuel_trim, pid, formatValue(value, resolver.locale))
    }

    /** Generic statistical outlier for any other PID. */
    data class AbnormalReading(
        override val pid: String,
        override val value: Double,
        override val deviation: Double,
    ) : AnomalyAlert(pid, value, deviation) {
        override fun message(resolver: StringResolver): String =
            resolver.get(R.string.ai_anomaly_abnormal_reading, pid, formatValue(value, resolver.locale))
    }
}

// One decimal with the app locale's separator; a trailing zero decimal is dropped (98.0 → 98)
private fun formatValue(value: Double, locale: Locale): String {
    val rounded = LocaleNumbers.decimal(value, 1, locale)
    val zeroDecimal = "${DecimalFormatSymbols.getInstance(locale).decimalSeparator}0"
    return if (rounded.endsWith(zeroDecimal)) rounded.dropLast(2) else rounded
}
