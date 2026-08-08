package com.revscope.core.obd.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineSoundSynthTest {

    @Test
    fun `firing frequency of a V8 at 3000 rpm is 200 Hz`() {
        assertEquals(200.0, EngineSoundSynth.firingFrequencyHz(3000.0, 8), 1e-9)
    }

    @Test
    fun `firing frequency of a 4 cylinder at 6000 rpm is 200 Hz`() {
        assertEquals(200.0, EngineSoundSynth.firingFrequencyHz(6000.0, 4), 1e-9)
    }

    @Test
    fun `smoothRpm moves toward target without overshoot`() {
        val result = EngineSoundSynth.smoothRpm(current = 0.0, target = 1000.0, dtMs = 50.0)
        assertTrue(result > 0 && result < 1000)
    }

    @Test
    fun `smoothRpm converges after many steps`() {
        var current = 0.0
        for (i in 0 until 200) {
            current = EngineSoundSynth.smoothRpm(current, target = 1000.0, dtMs = 50.0)
        }
        assertTrue(abs(current - 1000) < 1.0)
    }

    @Test
    fun `resolveLoad passes real throttle through`() {
        assertEquals(0.5, EngineSoundSynth.resolveLoad(throttle01 = 0.5, rpm = 6000.0), 1e-9)
    }

    @Test
    fun `resolveLoad falls back to rpm curve when throttle missing`() {
        assertEquals(0.5, EngineSoundSynth.resolveLoad(throttle01 = -1.0, rpm = 3500.0), 1e-9)
    }

    @Test
    fun `resolveLoad clamps rpm curve at 1`() {
        assertEquals(1.0, EngineSoundSynth.resolveLoad(throttle01 = -1.0, rpm = 99_999.0), 1e-9)
    }

    @Test
    fun `render with engine off produces silence`() {
        val synth = EngineSoundSynth()
        val buffer = ShortArray(2048)
        synth.render(buffer, targetRpm = 0.0, throttle01 = -1.0)
        for (value in buffer) {
            assertEquals(0.toShort(), value)
        }
    }

    @Test
    fun `render at 3000 rpm produces audible signal`() {
        val synth = EngineSoundSynth()
        val buffer1 = ShortArray(2048)
        synth.render(buffer1, targetRpm = 3000.0, throttle01 = 0.5)

        val buffer2 = ShortArray(2048)
        synth.render(buffer2, targetRpm = 3000.0, throttle01 = 0.5)

        assertTrue(buffer2.any { abs(it.toInt()) > 1000 })
    }

    @Test
    fun `render never clips to the extremes`() {
        val synth = EngineSoundSynth()
        val buffer1 = ShortArray(2048)
        synth.render(buffer1, targetRpm = 7000.0, throttle01 = 1.0)

        val buffer2 = ShortArray(2048)
        synth.render(buffer2, targetRpm = 7000.0, throttle01 = 1.0)

        assertTrue(buffer2.all { abs(it.toInt()) < Short.MAX_VALUE })
    }

    @Test
    fun `every pack produces sound at 3000 rpm`() {
        for (pack in SoundPack.entries) {
            val synth = EngineSoundSynth()
            synth.setPack(pack)
            val buffer1 = ShortArray(2048)
            synth.render(buffer1, targetRpm = 3000.0, throttle01 = 0.5)

            val buffer2 = ShortArray(2048)
            synth.render(buffer2, targetRpm = 3000.0, throttle01 = 0.5)

            assertTrue(buffer2.any { abs(it.toInt()) > 500 })
        }
    }

    @Test
    fun `fromId with unknown id falls back to default`() {
        assertEquals(SoundPack.V8_MUSCLE, SoundPack.fromId("nope"))
    }
}
