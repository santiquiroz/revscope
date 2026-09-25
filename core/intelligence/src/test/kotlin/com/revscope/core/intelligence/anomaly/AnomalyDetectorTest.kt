package com.revscope.core.intelligence.anomaly

import com.revscope.core.obd.model.ObdReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AnomalyDetectorTest {

    private val detector = AnomalyDetector()

    private fun reading(pid: String, value: Double) =
        ObdReading(pid = pid, value = value, unit = "", timestamp = 0)

    private fun warmUpEngine() {
        detector.observe(reading(COOLANT, WARM_C))
    }

    // Alternating center±1: mean = center, sample stddev = sqrt(n / (n - 1))
    private fun feedStable(pid: String, samples: Int, center: Double = 0.0) {
        repeat(samples) { i ->
            val offset = if (i % 2 == 0) 1.0 else -1.0
            detector.observe(reading(pid, center + offset))
        }
    }

    @Test
    fun `no alert before the baseline has 200 samples`() {
        feedStable(MAP, samples = 199)

        assertNull(detector.observe(reading(MAP, 100.0)))
    }

    @Test
    fun `alerts once the baseline has 200 samples`() {
        feedStable(MAP, samples = 200)

        assertNotNull(detector.observe(reading(MAP, 100.0)))
    }

    @Test
    fun `constant signal never alerts`() {
        val alerts = (1..500).mapNotNull { detector.observe(reading(MAP, 42.0)) }

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `coolant outlier is a HighTemperature alert`() {
        feedStable(COOLANT, samples = 200, center = 90.0)

        val alert = detector.observe(reading(COOLANT, 110.0))

        assertTrue("got $alert", alert is AnomalyAlert.HighTemperature)
        assertEquals(COOLANT, alert!!.pid)
    }

    @Test
    fun `warm fuel trim outlier is an UnusualFuelTrim alert`() {
        warmUpEngine()
        feedStable(SHORT_FUEL_TRIM, samples = 200)

        val alert = detector.observe(reading(SHORT_FUEL_TRIM, 25.0))

        assertTrue("got $alert", alert is AnomalyAlert.UnusualFuelTrim)
        assertEquals(SHORT_FUEL_TRIM, alert!!.pid)
    }

    @Test
    fun `outlier on another pid is an AbnormalReading alert`() {
        feedStable(MAP, samples = 200, center = 40.0)

        val alert = detector.observe(reading(MAP, 80.0))

        assertTrue("got $alert", alert is AnomalyAlert.AbnormalReading)
        assertEquals(MAP, alert!!.pid)
    }

    @Test
    fun `cold fuel trim neither alerts nor accumulates baseline`() {
        detector.observe(reading(COOLANT, COLD_C))
        feedStable(SHORT_FUEL_TRIM, samples = 300)
        assertNull(detector.observe(reading(SHORT_FUEL_TRIM, 25.0)))

        warmUpEngine()
        feedStable(SHORT_FUEL_TRIM, samples = 199)

        assertNull(detector.observe(reading(SHORT_FUEL_TRIM, 25.0)))
    }

    @Test
    fun `fuel trim is gated until coolant has been seen`() {
        feedStable(SHORT_FUEL_TRIM, samples = 300)

        assertNull(detector.observe(reading(SHORT_FUEL_TRIM, 25.0)))
    }

    @Test
    fun `cold coolant readings do not build the coolant baseline`() {
        repeat(300) { detector.observe(reading(COOLANT, 69.0)) }
        feedStable(COOLANT, samples = 200, center = 90.0)

        val alert = detector.observe(reading(COOLANT, 100.0))

        assertTrue("got $alert", alert is AnomalyAlert.HighTemperature)
    }

    @Test
    fun `non engine temperature pids are not gated by a cold engine`() {
        detector.observe(reading(COOLANT, COLD_C))
        feedStable(MAP, samples = 200)

        assertNotNull(detector.observe(reading(MAP, 100.0)))
    }

    @Test
    fun `reset clears the baselines`() {
        feedStable(MAP, samples = 200)

        detector.reset()

        assertNull(detector.observe(reading(MAP, 100.0)))
    }

    @Test
    fun `reset forgets the engine temperature`() {
        warmUpEngine()

        detector.reset()
        feedStable(SHORT_FUEL_TRIM, samples = 200)

        assertNull(detector.observe(reading(SHORT_FUEL_TRIM, 25.0)))
    }

    @Test
    fun `reading is compared against the baseline before it is incorporated`() {
        feedStable(MAP, samples = 200)
        val previousStddev = sqrt(200.0 / 199.0)

        val alert = detector.observe(reading(MAP, 3.05))

        assertNotNull("3.05 is > 3 sigma of the previous baseline", alert)
        assertEquals(3.05 / previousStddev, alert!!.deviation, 1e-9)
    }

    private companion object {
        const val COOLANT = "05"
        const val SHORT_FUEL_TRIM = "06"
        const val MAP = "0B"
        const val WARM_C = 90.0
        const val COLD_C = 40.0
    }
}
