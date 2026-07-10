package com.revscope.core.obd.protocol

/**
 * Parses Mode 06 (on-board monitoring test results) responses.
 *
 * Two request/response shapes are handled:
 *  - "06 00" → supported-MID bitmap, same bit scheme as the Mode 01 "01 00" PID bitmap.
 *  - "06 <MID>" → CAN test results: repeating 9-byte records of
 *    [MID][TID][UASID][2B value][2B min][2B max] after the "46" response header.
 */
object Mode06Parser {

    /** One on-board monitoring test result. [pass] compares raw counts, unaffected by UAS scaling. */
    data class TestResult(
        val mid: String,
        val tid: Int,
        val uasId: Int,
        val rawValue: Int,
        val rawMin: Int,
        val rawMax: Int,
        val value: Double,
        val min: Double,
        val max: Double,
        val unit: String,
        val pass: Boolean,
    )

    private const val RECORD_BYTES = 9
    private const val RESPONSE_HEADER = "46"

    /**
     * Decodes the "06 00" supported-MID bitmap (identical bit layout to [ResponseParser.parseSupportedPids]).
     */
    fun parseSupportedMids(raw: String): Set<String> {
        val clean = ResponseParser.cleanResponse(raw)
        if (clean.length < 4 || !clean.startsWith(RESPONSE_HEADER)) return emptySet()

        val requestMid = clean.substring(2, 4).toIntOrNull(16) ?: return emptySet()
        val bytes = ResponseParser.hexToBytes(clean.drop(4)) ?: return emptySet()

        return buildSet {
            bytes.forEachIndexed { byteIndex, byte ->
                val unsigned = byte.toInt() and 0xFF
                repeat(8) { bitIndex ->
                    if ((unsigned and (0x80 ushr bitIndex)) != 0) {
                        val midNum = requestMid + byteIndex * 8 + bitIndex + 1
                        add(midNum.toString(16).uppercase().padStart(2, '0'))
                    }
                }
            }
        }
    }

    /** Decodes every [TestResult] record in a "06 &lt;MID&gt;" response. */
    fun parseTestResults(raw: String): List<TestResult> {
        var clean = ResponseParser.cleanResponse(raw)
        if (ResponseParser.isErrorResponse(clean)) return emptyList()
        clean = ResponseParser.stripIsoTpFraming(clean)

        val start = clean.indexOf(RESPONSE_HEADER)
        if (start == -1) return emptyList()

        val bytes = ResponseParser.hexToBytes(clean.substring(start + RESPONSE_HEADER.length)) ?: return emptyList()

        return buildList {
            var offset = 0
            while (offset + RECORD_BYTES <= bytes.size) {
                add(parseRecord(bytes, offset))
                offset += RECORD_BYTES
            }
        }
    }

    private fun parseRecord(bytes: ByteArray, offset: Int): TestResult {
        val mid = bytes.unsignedByte(offset)
        val tid = bytes.unsignedByte(offset + 1)
        val uasId = bytes.unsignedByte(offset + 2)
        val rawValue = bytes.unsignedWord(offset + 3)
        val rawMin = bytes.unsignedWord(offset + 5)
        val rawMax = bytes.unsignedWord(offset + 7)
        val scale = UasScale.forId(uasId)

        return TestResult(
            mid = mid.toString(16).uppercase().padStart(2, '0'),
            tid = tid,
            uasId = uasId,
            rawValue = rawValue,
            rawMin = rawMin,
            rawMax = rawMax,
            value = scale.toPhysical(rawValue),
            min = scale.toPhysical(rawMin),
            max = scale.toPhysical(rawMax),
            unit = scale.unit,
            pass = rawValue in rawMin..rawMax,
        )
    }

    /**
     * Physical scaling for a handful of commonly seen SAE J1979 Unit-and-Scaling IDs.
     * Manufacturer-specific test values are not standardized beyond this — unknown IDs
     * fall back to the raw count, which is always shown alongside the scaled value in the UI.
     */
    private data class UasScale(val factor: Double, val unit: String) {
        fun toPhysical(raw: Int): Double = raw * factor

        companion object {
            private val RAW = UasScale(1.0, "raw")

            private val KNOWN: Map<Int, UasScale> = mapOf(
                0x01 to UasScale(1.0, "cuentas"),
                0x02 to UasScale(0.001, "ratio"),
                0x04 to UasScale(0.001, "V"),
                0x05 to UasScale(0.005, "V"),
                0x09 to UasScale(0.01, "mA"),
                0x0B to UasScale(1.0, "kPa"),
            )

            fun forId(uasId: Int): UasScale = KNOWN[uasId] ?: RAW
        }
    }
}

private fun ByteArray.unsignedByte(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.unsignedWord(index: Int): Int =
    (unsignedByte(index) shl 8) or unsignedByte(index + 1)
