package com.revscope.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun `MIN_RESTORABLE_DB_VERSION es 9`() {
        assertEquals(9, MIN_RESTORABLE_DB_VERSION)
    }

    @Test
    fun `isRestorableVersion valida correctamente el rango de versiones`() {
        val current = 20

        assertFalse(isRestorableVersion(imported = 8, current = current))
        assertTrue(isRestorableVersion(imported = 9, current = current))
        assertTrue(isRestorableVersion(imported = current, current = current))
        assertFalse(isRestorableVersion(imported = current + 1, current = current))
        assertFalse(isRestorableVersion(imported = 0, current = current))
    }
}
