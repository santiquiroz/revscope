package com.revscope.core.intelligence.efficiency

import com.revscope.core.obd.model.ObdReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveStyleClassifierTest {

    private fun rpm(value: Double, atMs: Long = 0) =
        ObdReading(pid = "0C", value = value, unit = "rpm", timestamp = atMs)

    private fun throttle(pct: Double, atMs: Long) =
        ObdReading(pid = "11", value = pct, unit = "%", timestamp = atMs)

    private fun load(pct: Double) =
        ObdReading(pid = "04", value = pct, unit = "%", timestamp = 0)

    @Test
    fun `empty session scores as empty`() {
        val classifier = DriveStyleClassifier()
        assertEquals(TripScore.empty(), classifier.score())
    }

    @Test
    fun `calm riding scores near 100`() {
        val classifier = DriveStyleClassifier(redlineRpm = 10_000)
        repeat(100) { classifier.observe(rpm(3_000.0)) }
        repeat(50) { classifier.observe(load(30.0)) }
        val score = classifier.score()
        assertTrue("expected >=95, got ${score.overall}", score.overall >= 95)
        assertEquals(0, score.hardAccelerationCount)
    }

    @Test
    fun `sustained high rpm is penalized`() {
        val classifier = DriveStyleClassifier(redlineRpm = 10_000)
        // 100% of samples above 80% of redline → full high-RPM penalty (40)
        repeat(100) { classifier.observe(rpm(9_000.0)) }
        val score = classifier.score()
        assertTrue("expected <=60, got ${score.overall}", score.overall <= 60)
        assertEquals(100f, score.highRpmTimePercent, 0.01f)
    }

    @Test
    fun `sudden throttle stab counts as hard acceleration`() {
        val classifier = DriveStyleClassifier()
        classifier.observe(rpm(3_000.0)) // needs RPM samples for score()
        classifier.observe(throttle(10.0, 1_000))
        classifier.observe(throttle(85.0, 1_500)) // +75% within 2s window
        assertEquals(1, classifier.score().hardAccelerationCount)
    }

    @Test
    fun `slow throttle roll-on is not a hard acceleration`() {
        val classifier = DriveStyleClassifier()
        classifier.observe(rpm(3_000.0))
        classifier.observe(throttle(10.0, 0))
        classifier.observe(throttle(80.0, 5_000)) // same delta, outside the 2s window
        assertEquals(0, classifier.score().hardAccelerationCount)
    }

    @Test
    fun `reset clears all accumulators`() {
        val classifier = DriveStyleClassifier()
        repeat(10) { classifier.observe(rpm(9_500.0)) }
        classifier.reset()
        assertEquals(TripScore.empty(), classifier.score())
    }
}
