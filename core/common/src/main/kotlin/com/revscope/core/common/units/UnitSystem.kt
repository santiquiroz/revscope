package com.revscope.core.common.units

import com.revscope.core.common.i18n.Region

enum class UnitSystem {
    METRIC,
    US,
    UK;

    companion object {
        fun defaultFor(region: Region): UnitSystem = when (region) {
            Region.US -> US
            Region.GB -> UK
            Region.CO, Region.OTHER -> METRIC
        }

        fun fromTag(tag: String, region: Region): UnitSystem =
            entries.firstOrNull { it.name == tag } ?: defaultFor(region)
    }
}
