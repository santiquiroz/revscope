package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDownloadDeciderTest {

    @Test
    fun `espacio sobrado permite arrancar`() {
        assertTrue(MapDownloadDecider.canStart(usableSpaceBytes = 2_000L, totalSizeBytes = 1_000L))
    }

    @Test
    fun `espacio justo en el limite del margen no permite arrancar`() {
        // 1000 * 1.2 = 1200: con exactamente 1200 libres no alcanza, el chequeo exige > estricto.
        assertFalse(MapDownloadDecider.canStart(usableSpaceBytes = 1_200L, totalSizeBytes = 1_000L))
    }

    @Test
    fun `espacio apenas por encima del margen permite arrancar`() {
        assertTrue(MapDownloadDecider.canStart(usableSpaceBytes = 1_201L, totalSizeBytes = 1_000L))
    }

    @Test
    fun `espacio insuficiente no permite arrancar`() {
        assertFalse(MapDownloadDecider.canStart(usableSpaceBytes = 500L, totalSizeBytes = 1_000L))
    }

    @Test
    fun `sin wifi y sin datos moviles permitidos bloquea`() {
        assertTrue(MapDownloadDecider.shouldBlockOnCellular(allowCellular = false, isOnWifi = false))
    }

    @Test
    fun `sin wifi pero con datos moviles permitidos no bloquea`() {
        assertFalse(MapDownloadDecider.shouldBlockOnCellular(allowCellular = true, isOnWifi = false))
    }

    @Test
    fun `con wifi nunca bloquea aunque no se permitan datos moviles`() {
        assertFalse(MapDownloadDecider.shouldBlockOnCellular(allowCellular = false, isOnWifi = true))
    }

    @Test
    fun `con wifi y datos moviles permitidos tampoco bloquea`() {
        assertFalse(MapDownloadDecider.shouldBlockOnCellular(allowCellular = true, isOnWifi = true))
    }

    @Test
    fun `promote con rename exitoso es Promoted`() {
        assertEquals(MapDownloadPromoteOutcome.PROMOTED, MapDownloadDecider.promoteOutcome(renameOk = true))
    }

    @Test
    fun `promote con rename fallido es RenameFailed`() {
        assertEquals(MapDownloadPromoteOutcome.RENAME_FAILED, MapDownloadDecider.promoteOutcome(renameOk = false))
    }
}
