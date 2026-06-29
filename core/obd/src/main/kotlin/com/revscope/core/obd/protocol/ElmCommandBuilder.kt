package com.revscope.core.obd.protocol

/**
 * Builds ELM327 AT commands and OBD-II PID request strings.
 * All strings include the trailing '\r' required by the ELM327 protocol.
 */
object ElmCommandBuilder {

    // ── Initialisation sequence ──────────────────────────────────────────────

    /** Full reset — returns ELM version string, then '>'. */
    fun reset() = "AT Z\r"

    /** Disable echo — adapter will not repeat commands back. */
    fun echoOff() = "AT E0\r"

    /** Disable line feeds in responses. */
    fun lineFeedsOff() = "AT L0\r"

    /**
     * Disable spaces in OBD-II data bytes.
     * IMPORTANT: with this active, "41 0C 0F A0" becomes "410C0FA0".
     * ResponseParser handles both forms via whitespace stripping.
     */
    fun spacesOff() = "AT S0\r"

    /** Disable response headers (removes ECU address prefix). */
    fun headersOff() = "AT H0\r"

    /**
     * Auto-detect vehicle OBD protocol.
     * ELM327 tries all protocols (ISO 9141-2, KWP2000, CAN 11-bit/29-bit, etc.)
     * and latches onto the first that gets a valid response.
     */
    fun autoProtocol() = "AT SP 0\r"

    /** Enable adaptive timing mode 1 — auto-adjusts response timeout. */
    fun adaptiveTiming() = "AT AT 1\r"

    /** Request the ELM327 firmware version string. */
    fun printVersion() = "AT I\r"

    // ── Supported PIDs queries ───────────────────────────────────────────────

    /** Query which standard PIDs (0x01–0x20) the ECU supports. */
    fun supportedPids00() = "01 00\r"

    /** Query which PIDs 0x21–0x40 the ECU supports. */
    fun supportedPids20() = "01 20\r"

    /** Query which PIDs 0x41–0x60 the ECU supports. */
    fun supportedPids40() = "01 40\r"

    /** Query which PIDs 0x61–0x80 the ECU supports (torque, gear ratio). */
    fun supportedPids60() = "01 60\r"

    // ── Mode 01 — real-time sensor data ─────────────────────────────────────

    /** Builds a Mode 01 PID request. [pid] is a 2-char hex string (e.g. "0C" for RPM). */
    fun mode01Pid(pid: String): String = "01 ${pid.uppercase().trimStart('0').padStart(2, '0')}\r"

    // ── Mode 03/07/0A/04 — DTC handling ─────────────────────────────────────

    /** Read active (confirmed) DTCs. */
    fun readDtcActive() = "03\r"

    /** Read pending DTCs (detected in current drive cycle but not yet confirmed). */
    fun readDtcPending() = "07\r"

    /** Read permanent DTCs (cannot be cleared by Mode 04). */
    fun readDtcPermanent() = "0A\r"

    /** Clear all DTCs and reset freeze-frame data. Use with user confirmation. */
    fun clearDtc() = "04\r"

    // ── Mode 09 — vehicle information ────────────────────────────────────────

    /** Request the 17-char VIN (Vehicle Identification Number). */
    fun readVin() = "09 02\r"

    /** Request the ECU/calibration name string. */
    fun readEcuName() = "09 0A\r"

    // ── Mode 22 — manufacturer-specific PIDs ─────────────────────────────────

    /**
     * Builds a Mode 22 request for OEM-specific PIDs (e.g. Mazda AWD torque split).
     * [pid] is a 4-char hex string (e.g. "0C20").
     */
    fun mode22Pid(pid: String): String = "22 ${pid.uppercase()}\r"
}
