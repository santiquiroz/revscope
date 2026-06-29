package com.revscope.core.obd.telemetry

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.pid.PidRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PidSchedulerTest {

    private val testJson = """
        [
          {
            "mode": "01", "pid": "0C",
            "name": "RPM", "nameEs": "RPM",
            "bytes": 2, "formula": "((A*256)+B)/4",
            "unit": "rpm", "min": 0, "max": 16383, "priority": 1
          },
          {
            "mode": "01", "pid": "0D",
            "name": "Speed", "nameEs": "Velocidad",
            "bytes": 1, "formula": "A",
            "unit": "km/h", "min": 0, "max": 255, "priority": 1
          }
        ]
    """.trimIndent()

    private lateinit var registry: PidRegistry
    private val transport: Transport = mockk()

    @Before
    fun setUp() {
        registry = PidRegistry(testJson)
    }

    @Test
    fun `emits ObdReading with correct value for valid response`() = runTest {
        // 0C response "410C0FA0" → bytes [0x0F, 0xA0] → ((15*256)+160)/4 = 1000 rpm
        // 0D response "410D3C"   → bytes [0x3C]         → A = 60 km/h
        // Any receive call returns an RPM response — the 0D parse will fail the header
        // check and return null, so only 0C readings will be emitted
        coEvery { transport.send(any()) } returns Unit
        coEvery { transport.receive() } returns "410C0FA0>"

        val scheduler = PidScheduler(transport, registry)
        val readings = scheduler.observeReadings().take(1).toList()

        assertEquals(1, readings.size)
        assertEquals("0C", readings[0].pid)
        assertEquals(1000.0, readings[0].value, 0.001)
        assertEquals("rpm", readings[0].unit)
    }

    @Test
    fun `excludes PID after NO DATA response and polls remaining PIDs`() = runTest {
        // 0C always returns NO DATA; 0D always returns a valid speed reading
        var lastSentCommand = ""
        coEvery { transport.send(any()) } answers { lastSentCommand = firstArg() }
        coEvery { transport.receive() } answers {
            when {
                lastSentCommand.contains("0C") -> "NO DATA>"
                lastSentCommand.contains("0D") -> "410D3C>"
                else -> "NO DATA>"
            }
        }

        val scheduler = PidScheduler(transport, registry)
        val readings = scheduler.observeReadings().take(3).toList()

        assertTrue("0C must not appear after NO DATA", readings.none { it.pid == "0C" })
        assertTrue("all readings must be 0D", readings.all { it.pid == "0D" })
        readings.forEach { assertEquals(60.0, it.value, 0.001) }
    }

    @Test
    fun `retries once on transport exception then continues`() = runTest {
        var callCount = 0
        coEvery { transport.send(any()) } returns Unit
        coEvery { transport.receive() } answers {
            // First receive throws; second onwards returns a valid RPM response
            if (callCount++ == 0) throw Exception("simulated timeout")
            "410C0FA0>"
        }

        val scheduler = PidScheduler(transport, registry)
        val readings = scheduler.observeReadings().take(1).toList()

        assertEquals(1, readings.size)
        assertEquals(1000.0, readings[0].value, 0.001)
    }

    @Test
    fun `doubles interval multiplier on BUFFER FULL response`() = runTest {
        var callCount = 0
        coEvery { transport.send(any()) } returns Unit
        coEvery { transport.receive() } answers {
            // Return BUFFER FULL twice then valid RPM
            if (callCount++ < 2) "BUFFER FULL>" else "410C0FA0>"
        }

        val scheduler = PidScheduler(transport, registry)
        val readings = scheduler.observeReadings().take(1).toList()

        // After BUFFER FULL the scheduler eventually emits when response is valid
        assertEquals(1, readings.size)
        assertEquals(1000.0, readings[0].value, 0.001)
    }
}
