package com.revscope.core.obd.cameras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraApproachGateTest {

    @Test
    fun `no heading means no alert`() {
        assertFalse(
            CameraApproachGate.shouldAlert(
                headingDeg = null,
                bearingToCameraDeg = 0.0,
                previousDistanceM = 300.0,
                distanceM = 250.0,
            ),
        )
    }

    @Test
    fun `camera behind is not alerted`() {
        assertFalse(
            CameraApproachGate.shouldAlert(
                headingDeg = 0f,
                bearingToCameraDeg = 180.0,
                previousDistanceM = 200.0,
                distanceM = 250.0,
            ),
        )
    }

    @Test
    fun `camera on perpendicular street is not alerted`() {
        assertFalse(
            CameraApproachGate.shouldAlert(
                headingDeg = 0f,
                bearingToCameraDeg = 90.0,
                previousDistanceM = 300.0,
                distanceM = 250.0,
            ),
        )
    }

    @Test
    fun `inside cone but moving away is not alerted`() {
        assertFalse(
            CameraApproachGate.shouldAlert(
                headingDeg = 0f,
                bearingToCameraDeg = 10.0,
                previousDistanceM = 200.0,
                distanceM = 250.0,
            ),
        )
    }

    @Test
    fun `first fix inside radius only records distance`() {
        assertFalse(
            CameraApproachGate.shouldAlert(
                headingDeg = 0f,
                bearingToCameraDeg = 10.0,
                previousDistanceM = null,
                distanceM = 250.0,
            ),
        )
    }

    @Test
    fun `inside cone and approaching is alerted`() {
        assertTrue(
            CameraApproachGate.shouldAlert(
                headingDeg = 0f,
                bearingToCameraDeg = 45.0,
                previousDistanceM = 300.0,
                distanceM = 250.0,
            ),
        )
    }

    @Test
    fun `angular difference wraps around north`() {
        // 350° vs 10° is 20° apart, not 340°
        assertEquals(20.0, CameraApproachGate.angularDifferenceDeg(350.0, 10.0), 0.0001)
        assertTrue(
            CameraApproachGate.shouldAlert(
                headingDeg = 350f,
                bearingToCameraDeg = 10.0,
                previousDistanceM = 300.0,
                distanceM = 250.0,
            ),
        )
    }

    @Test
    fun `just outside cone is not alerted`() {
        assertFalse(
            CameraApproachGate.shouldAlert(
                headingDeg = 0f,
                bearingToCameraDeg = 61.0,
                previousDistanceM = 300.0,
                distanceM = 250.0,
            ),
        )
    }
}
