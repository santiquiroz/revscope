package com.revscope.core.obd.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `mayor minor es mas nuevo`() {
        assertTrue(VersionCompare.isNewer("1.10.0", "1.9.1"))
    }

    @Test
    fun `misma version no es mas nueva`() {
        assertFalse(VersionCompare.isNewer("1.9.1", "1.9.1"))
    }

    @Test
    fun `version instalada mayor no dispara update`() {
        assertFalse(VersionCompare.isNewer("1.9.0", "1.9.1"))
    }

    @Test
    fun `tolera prefijo v y compara patch`() {
        assertTrue(VersionCompare.isNewer("v1.9.2", "1.9.1"))
    }

    @Test
    fun `numero de partes distinto`() {
        assertTrue(VersionCompare.isNewer("2.0", "1.9.9"))
        assertFalse(VersionCompare.isNewer("1.9", "1.9.0"))
    }

    @Test
    fun `entradas invalidas retornan false`() {
        assertFalse(VersionCompare.isNewer("abc", "1.9.1"))
        assertFalse(VersionCompare.isNewer("1.9.1", "xyz"))
    }
}
