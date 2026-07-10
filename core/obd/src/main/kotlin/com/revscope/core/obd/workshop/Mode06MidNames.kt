package com.revscope.core.obd.workshop

/** Human-readable Spanish names for the most common Mode 06 monitor IDs (MIDs). */
object Mode06MidNames {

    private const val CATALYST_B1 = "21"
    private const val CATALYST_B2 = "22"
    private const val MISFIRE_GENERAL = "A1"
    private const val MISFIRE_CYLINDER_RANGE_START = 0xA2
    private const val MISFIRE_CYLINDER_RANGE_END = 0xAD

    fun nameFor(mid: String): String {
        val normalized = mid.uppercase()
        val midNum = normalized.toIntOrNull(16)
        return when {
            normalized == CATALYST_B1 -> "Catalizador B1"
            normalized == CATALYST_B2 -> "Catalizador B2"
            normalized == MISFIRE_GENERAL -> "Misfire general"
            midNum != null && midNum in MISFIRE_CYLINDER_RANGE_START..MISFIRE_CYLINDER_RANGE_END ->
                "Misfire cilindro ${midNum - MISFIRE_CYLINDER_RANGE_START + 1}"
            else -> "MID $normalized"
        }
    }
}
