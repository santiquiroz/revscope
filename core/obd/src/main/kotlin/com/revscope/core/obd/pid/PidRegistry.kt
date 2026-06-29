package com.revscope.core.obd.pid

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.protocol.ResponseParser
import net.objecthunter.exp4j.ExpressionBuilder
import org.json.JSONArray
import timber.log.Timber

/**
 * Central registry of OBD-II PID definitions loaded from [pidsJson].
 *
 * Responsibilities:
 * - Parse pids_mode01.json into [PidDefinition] objects at creation time
 * - Filter definitions to only those the ECU reports as supported (via [setSupportedPids])
 * - Evaluate exp4j formula strings against raw byte arrays from the adapter
 * - Provide priority-grouped lists for the PidScheduler (Phase 2)
 *
 * Constructor accepts the raw JSON string so the class is testable without an Android Context.
 * Production code should use the companion [fromAssets] factory.
 */
class PidRegistry(pidsJson: String) {

    private val definitions: Map<String, PidDefinition> = parseDefinitions(pidsJson)

    // Null means "not yet set" — all PIDs allowed. Set after ProtocolNegotiator runs.
    private var supportedPidFilter: Set<String>? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Restricts polling to the PIDs that the ECU reported as supported.
     * Call this once after [ProtocolNegotiator.initialize] succeeds.
     */
    fun setSupportedPids(ecuSupportedPids: Set<String>) {
        supportedPidFilter = ecuSupportedPids.mapTo(mutableSetOf()) { it.uppercase() }
        Timber.d("PidRegistry: ECU supports ${supportedPidFilter!!.size} PIDs from our definition set")
    }

    /** Returns the [PidDefinition] for [pid], or null if unknown. */
    fun getDefinition(pid: String): PidDefinition? = definitions[pid.uppercase()]

    /** All definitions regardless of ECU support status. */
    fun allDefinitions(): Collection<PidDefinition> = definitions.values

    /**
     * Definitions for PIDs at [priority] that the ECU supports (or all, if [setSupportedPids]
     * has not been called yet).
     */
    fun definitionsForPriority(priority: Int): List<PidDefinition> =
        definitions.values.filter { it.priority == priority && isAllowedByFilter(it.pid) }

    /**
     * Evaluates the formula for [pid] against [rawBytes] from the adapter.
     * Returns a clamped [ObdReading] or null if the PID is unknown or evaluation fails.
     */
    fun evaluate(pid: String, rawBytes: ByteArray): ObdReading? {
        val def = definitions[pid.uppercase()] ?: run {
            Timber.w("PidRegistry.evaluate: unknown PID $pid")
            return null
        }
        return try {
            val raw = evalFormula(def.formula, rawBytes)
            val clamped = raw.coerceIn(def.min, def.max)
            ObdReading(pid = def.pid, value = clamped, unit = def.unit)
        } catch (e: Exception) {
            Timber.e(e, "Formula eval failed for PID $pid ('${def.formula}')")
            null
        }
    }

    /**
     * Convenience: parse a raw adapter response for [pid] and evaluate the formula.
     * Returns null on parse error, error response, or unknown PID.
     */
    fun parseAndEvaluate(pid: String, rawResponse: String): ObdReading? {
        val bytes = ResponseParser.parsePidResponse(rawResponse, pid) ?: return null
        return evaluate(pid, bytes)
    }

    /** Returns true if [pid] passes the ECU support filter (or no filter is set). */
    fun isSupported(pid: String): Boolean = isAllowedByFilter(pid)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun isAllowedByFilter(pid: String): Boolean {
        val filter = supportedPidFilter ?: return true
        return filter.contains(pid.uppercase())
    }

    private fun evalFormula(formula: String, bytes: ByteArray): Double {
        val a = bytes.getUnsigned(0)
        val b = bytes.getUnsigned(1)
        val c = bytes.getUnsigned(2)
        val d = bytes.getUnsigned(3)

        return ExpressionBuilder(formula)
            .variables("A", "B", "C", "D")
            .build()
            .setVariable("A", a)
            .setVariable("B", b)
            .setVariable("C", c)
            .setVariable("D", d)
            .evaluate()
    }

    private fun parseDefinitions(json: String): Map<String, PidDefinition> =
        try {
            val array = JSONArray(json)
            buildMap {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val def = PidDefinition(
                        mode    = obj.getString("mode"),
                        pid     = obj.getString("pid").uppercase(),
                        name    = obj.getString("name"),
                        nameEs  = obj.getString("nameEs"),
                        bytes   = obj.getInt("bytes"),
                        formula = obj.getString("formula"),
                        unit    = obj.getString("unit"),
                        min     = obj.getDouble("min"),
                        max     = obj.getDouble("max"),
                        priority = obj.getInt("priority"),
                    )
                    put(def.pid, def)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "PidRegistry: failed to parse PID definitions JSON")
            emptyMap()
        }
}

private fun ByteArray.getUnsigned(index: Int): Double =
    if (index < size) (this[index].toInt() and 0xFF).toDouble() else 0.0
