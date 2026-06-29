package com.revscope.core.obd.protocol

import timber.log.Timber

/**
 * Parses raw ELM327 ASCII responses into structured data.
 *
 * Handles both space-separated ("41 0C 0F A0 >") and compact ("410C0FA0>") formats
 * that arise from AT S1 (spaces on) vs AT S0 (spaces off) adapter settings.
 * The [cleanResponse] step normalises both before further processing.
 */
object ResponseParser {

    private val WHITESPACE = Regex("\\s+")

    // ── Known error strings returned by ELM327 adapters ─────────────────────

    private val ERROR_TOKENS = setOf(
        "NODATA",
        "UNABLETOCONNECT",
        "CANERROR",
        "BUSERROR",
        "BUFFERFULL",
        "FBERROR",
        "ERR",
        "SEARCHING",
        "STOPPED",
        "?",
    )

    // ── Core utilities ───────────────────────────────────────────────────────

    /**
     * Strips whitespace, carriage returns, line feeds, and the '>' prompt.
     * Uppercases the result so all comparisons are case-insensitive.
     */
    fun cleanResponse(raw: String): String =
        raw.replace(WHITESPACE, "")
            .replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .uppercase()
            .trim()

    /**
     * Converts a hex string (e.g. "0FA0") to a ByteArray.
     * Returns null if the string length is odd or contains non-hex characters.
     */
    fun hexToBytes(hex: String): ByteArray? {
        if (hex.isEmpty() || hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (e: NumberFormatException) {
            Timber.w("hexToBytes: invalid hex string '$hex'")
            null
        }
    }

    // ── Mode 01 PID parsing ──────────────────────────────────────────────────

    /**
     * Parses a Mode 01 PID response and returns the raw data bytes [A, B, C, D].
     *
     * @param raw   Raw adapter response string (may contain spaces, prompt, etc.)
     * @param pid   Two-char hex PID identifier (e.g. "0C" for RPM)
     * @return Data bytes array, or null on error/unsupported PID
     *
     * Example — RPM:
     *   Input:  "41 0C 0F A0 >" or "410C0FA0>"
     *   Output: [0x0F, 0xA0]
     *   Formula: ((A*256)+B)/4 → ((15*256)+160)/4 = 1000 rpm
     */
    fun parsePidResponse(raw: String, pid: String): ByteArray? {
        val clean = cleanResponse(raw)

        if (isErrorResponse(clean)) {
            Timber.w("parsePidResponse: error for PID $pid — '$raw'")
            return null
        }

        // Positive response header is "41" + the requested PID
        val header = "41${pid.uppercase()}"
        if (!clean.startsWith(header)) {
            Timber.w("parsePidResponse: unexpected header for PID $pid. Got '$clean', expected prefix '$header'")
            return null
        }

        val dataHex = clean.removePrefix(header)
        return hexToBytes(dataHex).also {
            if (it == null) Timber.w("parsePidResponse: failed hex decode '$dataHex' for PID $pid")
        }
    }

    // ── Supported PIDs bitmask parsing ───────────────────────────────────────

    /**
     * Decodes a "supported PIDs" bitmask response (reply to 01 00, 01 20, 01 40, 01 60).
     *
     * @param raw Raw adapter response
     * @return Set of 2-char hex PID strings that are supported by the ECU
     *
     * Example:
     *   Request  "01 00" → Response "4100BE1FA813"
     *   Bytes: BE=1011_1110, 1F=0001_1111, A8=1010_1000, 13=0001_0011
     *   Bit 0 of BE = PID 0x01, bit 1 = 0x02, ... bit 31 of 13 = 0x20
     */
    fun parseSupportedPids(raw: String): Set<String> {
        val clean = cleanResponse(raw)
        if (clean.length < 4) return emptySet()

        // Header "41XX" — XX is the "supported PIDs" PID that was requested
        val requestPidHex = clean.substring(2, 4)
        val requestPid = requestPidHex.toIntOrNull(16) ?: return emptySet()

        val dataHex = clean.drop(4)
        val bytes = hexToBytes(dataHex) ?: return emptySet()

        return buildSet {
            bytes.forEachIndexed { byteIndex, byte ->
                val unsigned = byte.toInt() and 0xFF
                repeat(8) { bitIndex ->
                    if ((unsigned and (0x80 ushr bitIndex)) != 0) {
                        val pidNum = requestPid + byteIndex * 8 + bitIndex + 1
                        add(pidNum.toString(16).uppercase().padStart(2, '0'))
                    }
                }
            }
        }
    }

    // ── DTC parsing ──────────────────────────────────────────────────────────

    /**
     * Parses DTC codes from Mode 03, 07, or 0A responses.
     *
     * @return List of DTC strings in standard format (e.g. ["P0300", "C0121"])
     *
     * Response byte pairs encoding:
     *   High byte bits 7-6: type prefix (00=P, 01=C, 10=B, 11=U)
     *   High byte bits 5-4: first digit
     *   High byte bits 3-0: second digit
     *   Low byte bits 7-4:  third digit
     *   Low byte bits 3-0:  fourth digit
     */
    fun parseDtcResponse(raw: String): List<String> {
        val clean = cleanResponse(raw)
        if (isErrorResponse(clean) || clean.isEmpty()) return emptyList()

        // Strip mode prefix (43 = Mode 03, 47 = Mode 07, 4A = Mode 0A)
        val dataStart = when {
            clean.startsWith("43") || clean.startsWith("47") || clean.startsWith("4A") -> 2
            else -> return emptyList()
        }

        val bytes = hexToBytes(clean.drop(dataStart)) ?: return emptyList()

        return buildList {
            var i = 0
            while (i + 1 < bytes.size) {
                val high = bytes[i].toInt() and 0xFF
                val low = bytes[i + 1].toInt() and 0xFF
                if (high == 0 && low == 0) break  // zero-padding signals end of DTC list
                decodeDtcPair(high, low)?.let { add(it) }
                i += 2
            }
        }
    }

    private fun decodeDtcPair(high: Int, low: Int): String? {
        val prefix = when ((high shr 6) and 0x03) {
            0x00 -> 'P'
            0x01 -> 'C'
            0x02 -> 'B'
            0x03 -> 'U'
            else -> return null
        }
        val d1 = (high shr 4) and 0x03
        val d2 = high and 0x0F
        val d3 = (low shr 4) and 0x0F
        val d4 = low and 0x0F
        return "$prefix$d1${d2.toString(16).uppercase()}${d3.toString(16).uppercase()}${d4.toString(16).uppercase()}"
    }

    // ── Error detection helpers ───────────────────────────────────────────────

    /**
     * Returns true if the cleaned response matches any known ELM327 error token.
     * This includes "NO DATA" (PID not supported), "UNABLE TO CONNECT" (no ECU),
     * "BUFFER FULL" (polling too fast), and generic ERROR responses.
     */
    fun isErrorResponse(response: String): Boolean {
        val clean = cleanResponse(response)
        return clean.isEmpty() || ERROR_TOKENS.any { clean.contains(it) }
    }

    /** Returns true if the adapter reported the PID is not supported by this ECU. */
    fun isNoData(response: String): Boolean =
        cleanResponse(response).contains("NODATA")

    /** Returns true if the ECU is not communicating (protocol mismatch or no ECU). */
    fun isUnableToConnect(response: String): Boolean {
        val clean = cleanResponse(response)
        return clean.contains("UNABLETOCONNECT") || clean.contains("CANERROR")
    }

    /** Returns true if the adapter's input buffer is full — caller should slow polling. */
    fun isBufferFull(response: String): Boolean =
        cleanResponse(response).contains("BUFFERFULL")
}
