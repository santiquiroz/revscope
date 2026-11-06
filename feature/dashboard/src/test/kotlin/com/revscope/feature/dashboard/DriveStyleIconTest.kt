package com.revscope.feature.dashboard

import com.revscope.core.intelligence.efficiency.DriveStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveStyleIconTest {
    @Test
    fun driveStyleIconsAreDistinct() {
        val icons = DriveStyle.entries.map(::driveStyleIcon)

        assertEquals(4, icons.toSet().size)
    }
}
