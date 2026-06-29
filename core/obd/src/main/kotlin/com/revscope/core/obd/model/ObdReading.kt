package com.revscope.core.obd.model

/**
 * A single parsed sensor value from the OBD-II adapter.
 *
 * @property pid       Two-char hex PID identifier (e.g. "0C" for RPM)
 * @property value     Parsed and formula-evaluated numeric value
 * @property unit      Physical unit string for display (e.g. "rpm", "km/h", "°C")
 * @property timestamp Epoch ms when the reading was received from the adapter
 */
data class ObdReading(
    val pid: String,
    val value: Double,
    val unit: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/** A Diagnostic Trouble Code read from the ECU. */
data class DtcCode(
    val code: String,   // Standard format: "P0300", "C0121", "B1234", "U0100"
    val mode: DtcMode,
)

sealed class DtcMode {
    /** Active (confirmed) — reported by Mode 03. */
    object Active : DtcMode()

    /** Pending — detected in current drive cycle, reported by Mode 07. */
    object Pending : DtcMode()

    /** Permanent — cannot be cleared by Mode 04, reported by Mode 0A. */
    object Permanent : DtcMode()
}
