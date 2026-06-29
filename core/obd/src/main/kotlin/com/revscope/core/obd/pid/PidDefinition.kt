package com.revscope.core.obd.pid

/**
 * Describes one OBD-II PID — its mode, identifier, formula, and display metadata.
 * Instances are loaded from pids_mode01.json via [PidRegistry].
 *
 * @property mode     OBD-II mode as 2-char hex string ("01", "22", …)
 * @property pid      PID as 2-char hex string ("0C", "0D", …)
 * @property name     English display name
 * @property nameEs   Spanish display name
 * @property bytes    Number of data bytes expected in the response (1–4)
 * @property formula  exp4j expression string with variables A, B, C, D
 * @property unit     Physical unit for display (e.g. "rpm", "km/h", "°C")
 * @property min      Minimum valid value (for gauge range and clamping)
 * @property max      Maximum valid value (for gauge range and clamping)
 * @property priority Polling priority — 1 = high (100 ms), 2 = medium (500 ms), 3 = low (2000 ms)
 */
data class PidDefinition(
    val mode: String,
    val pid: String,
    val name: String,
    val nameEs: String,
    val bytes: Int,
    val formula: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val priority: Int,
) {
    /** Fully qualified identifier used in request strings (e.g. "010C"). */
    val fullId: String get() = "${mode.uppercase()}${pid.uppercase()}"
}
