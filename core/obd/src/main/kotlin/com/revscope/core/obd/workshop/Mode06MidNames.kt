package com.revscope.core.obd.workshop

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

/** Human-readable Spanish names for the most common Mode 06 monitor IDs (MIDs). */
object Mode06MidNames {

    private const val CATALYST_B1 = "21"
    private const val CATALYST_B2 = "22"
    private const val MISFIRE_GENERAL = "A1"
    private const val MISFIRE_CYLINDER_RANGE_START = 0xA2
    private const val MISFIRE_CYLINDER_RANGE_END = 0xAD

    fun nameFor(mid: String, resolver: StringResolver): String {
        val normalized = mid.uppercase()
        val midNum = normalized.toIntOrNull(16)
        return when {
            normalized == CATALYST_B1 -> resolver.get(R.string.obd_mode06_catalyst_b1)
            normalized == CATALYST_B2 -> resolver.get(R.string.obd_mode06_catalyst_b2)
            normalized == MISFIRE_GENERAL -> resolver.get(R.string.obd_mode06_misfire_general)
            midNum != null && midNum in MISFIRE_CYLINDER_RANGE_START..MISFIRE_CYLINDER_RANGE_END ->
                resolver.get(R.string.obd_mode06_misfire_cylinder, midNum - MISFIRE_CYLINDER_RANGE_START + 1)
            else -> resolver.get(R.string.obd_mode06_mid, normalized)
        }
    }
}
