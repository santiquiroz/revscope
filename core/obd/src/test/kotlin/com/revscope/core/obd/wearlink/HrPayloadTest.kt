package com.revscope.core.obd.wearlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrPayloadTest {

    @Test
    fun `parse returns timestamp and bpm for valid payload`() {
        val result = HrPayload.parse("1720000000000;87.5".toByteArray(Charsets.UTF_8))
        assertEquals(Pair(1720000000000L, 87.5f), result)
    }

    @Test
    fun `parse returns null when bpm is zero`() {
        assertNull(HrPayload.parse("1720000000000;0".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `parse returns null when timestamp is negative`() {
        assertNull(HrPayload.parse("-1;72".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `parse returns null for payload without separator`() {
        assertNull(HrPayload.parse("garbage".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `parse returns null for empty payload`() {
        assertNull(HrPayload.parse(byteArrayOf()))
    }
}
