package com.revscope.core.obd.alerts

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import java.text.DecimalFormatSymbols
import org.json.JSONArray
import timber.log.Timber

/**
 * User-defined per-PID threshold rules, parsed from Settings' custom-alerts JSON editor.
 *
 * Pure functions — no Android dependencies — so [AlertsEngine]'s threshold evaluation
 * stays unit-testable even though the engine itself needs a Context for TTS/tone/vibration.
 */
object CustomAlertRules {

    data class Rule(val pid: String, val min: Double?, val max: Double?, val nombre: String)

    /**
     * Parses the JSON array `[{pid, min?, max?, nombre}]`. Invalid JSON yields an empty list.
     * A rule where both bounds are present and `min > max` matches every reading, so it is
     * skipped rather than included.
     */
    fun parse(json: String): List<Rule> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val min = if (obj.has("min")) obj.getDouble("min") else null
                    val max = if (obj.has("max")) obj.getDouble("max") else null
                    if (isInvertedRange(min, max)) continue
                    add(
                        Rule(
                            pid = obj.getString("pid").uppercase(),
                            min = min,
                            max = max,
                            nombre = obj.getString("nombre"),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CustomAlertRules: failed to parse JSON")
            emptyList()
        }
    }

    /** True when both bounds are present and inverted (`min > max`) — such a rule matches everything. */
    fun isInvertedRange(min: Double?, max: Double?): Boolean =
        min != null && max != null && min > max

    fun evaluate(reading: ObdReading, rules: List<Rule>, resolver: StringResolver): String? {
        val rule = rules.find { it.pid == reading.pid.uppercase() } ?: return null
        val outOfRange = (rule.min != null && reading.value < rule.min) ||
            (rule.max != null && reading.value > rule.max)
        if (!outOfRange) return null
        val value = formatValue(reading.value, resolver)
        return resolver.get(R.string.obd_custom_alert_out_of_range, rule.nombre, value, reading.unit).trimEnd()
    }

    private fun formatValue(value: Double, resolver: StringResolver): String {
        val rounded = LocaleNumbers.decimal(value, 1, resolver.locale)
        return rounded.removeSuffix("${DecimalFormatSymbols.getInstance(resolver.locale).decimalSeparator}0")
    }
}
