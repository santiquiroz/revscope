package com.revscope.core.obd.cameras

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraAlertRadiusTest {

    @Test
    fun `null falls back to default`() {
        assertEquals(CameraAlertRadius.DEFAULT_M, CameraAlertRadius.sanitize(null))
    }

    @Test
    fun `value inside range passes through`() {
        assertEquals(300, CameraAlertRadius.sanitize(300))
    }

    @Test
    fun `value below minimum clamps to minimum`() {
        assertEquals(CameraAlertRadius.MIN_M, CameraAlertRadius.sanitize(10))
    }

    @Test
    fun `value above maximum clamps to maximum`() {
        assertEquals(CameraAlertRadius.MAX_M, CameraAlertRadius.sanitize(5_000))
    }

    @Test
    fun `default is stricter than the old hardcoded 400`() {
        assertEquals(250, CameraAlertRadius.DEFAULT_M)
    }

    @Test
    fun `parse from text rejects garbage`() {
        assertEquals(CameraAlertRadius.DEFAULT_M, CameraAlertRadius.sanitize("abc".toIntOrNull()))
    }
}
