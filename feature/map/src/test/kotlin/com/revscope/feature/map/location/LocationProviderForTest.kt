package com.revscope.feature.map.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProviderForTest {

    @Test
    fun `con permiso preciso usa gps`() {
        assertEquals("gps", locationProviderFor(hasFine = true, sdkInt = 34, hasFused = true))
    }

    @Test
    fun `solo aproximada en API 30 usa network`() {
        assertEquals("network", locationProviderFor(hasFine = false, sdkInt = 30, hasFused = true))
    }

    @Test
    fun `solo aproximada en API 31 con fused usa fused`() {
        assertEquals("fused", locationProviderFor(hasFine = false, sdkInt = 31, hasFused = true))
    }

    @Test
    fun `solo aproximada en API 31 sin fused usa network`() {
        assertEquals("network", locationProviderFor(hasFine = false, sdkInt = 31, hasFused = false))
    }

    @Test
    fun `acceso se deriva de los permisos concedidos`() {
        assertEquals(LocationAccess.FINE, locationAccessOf(fine = true, coarse = true))
        assertEquals(LocationAccess.COARSE, locationAccessOf(fine = false, coarse = true))
        assertEquals(LocationAccess.NONE, locationAccessOf(fine = false, coarse = false))
    }
}
