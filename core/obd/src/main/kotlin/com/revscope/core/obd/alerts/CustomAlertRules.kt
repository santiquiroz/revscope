package com.revscope.core.obd.alerts

import com.revscope.core.obd.model.ObdReading
import java.util.Locale
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

    /** Parses the JSON array `[{pid, min?, max?, nombre}]`. Invalid JSON yields an empty list. */
    fun parse(json: String): List<Rule> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        Rule(
                            pid = obj.getString("pid").uppercase(),
                            min = if (obj.has("min")) obj.getDouble("min") else null,
                            max = if (obj.has("max")) obj.getDouble("max") else null,
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

    /** Returns the Spanish "out of range" message for [reading], or null if within range. */
    fun evaluate(reading: ObdReading, rules: List<Rule>): String? {
        val rule = rules.find { it.pid == reading.pid.uppercase() } ?: return null
        val outOfRange = (rule.min != null && reading.value < rule.min) ||
            (rule.max != null && reading.value > rule.max)
        if (!outOfRange) return null
        return "${rule.nombre} fuera de rango: ${formatValue(reading.value)} ${reading.unit}".trimEnd()
    }

    // Locale.US pinned so the spoken/displayed number is deterministic regardless of device locale
    private fun formatValue(value: Double): String {
        val rounded = "%.1f".format(Locale.US, value)
        return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
    }
}
