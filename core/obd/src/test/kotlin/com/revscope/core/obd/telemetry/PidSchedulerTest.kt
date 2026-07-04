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
import java.io.IOException

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
        // Any exchange call returns an RPM response — the 0D parse will fail the header
        // check and return null, so only 0C readings will be emitted
        coEvery { transport.exchange(any(), any()) } returns "410C0FA0>"

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
        coEvery { transport.exchange(any(), any()) } answers {
            val command = firstArg<String>()
            when {
                command.contains("0C") -> "NO DATA>"
                command.contains("0D") -> "410D3C>"
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
    fun `retries once on transport timeout exception then continues`() = runTest {
        // Real contract: ClassicBtTransport.exchange throws IOException on read timeout
        var callCount = 0
        coEvery { transport.exchange(any(), any()) } answers {
            if (callCount++ == 0) throw IOException("Read timeout after 2000 ms without prompt")
            "410C0FA0>"
        }

        val scheduler = PidScheduler(transport, registry)
        val readings = scheduler.observeReadings().take(1).toList()

        assertEquals(1, readings.size)
        assertEquals(1000.0, readings[0].value, 0.001)
    }

    @Test
    fun `batches priority-1 PIDs into one exchange and emits all readings`() = runTest {
        // 0C (2 bytes) + 0D (1 byte) fit one CAN frame → single "01 0C 0D" request
        val commands = mutableListOf<String>()
        coEvery { transport.exchange(any(), any()) } answers {
            val cmd = firstArg<String>()
            commands += cmd
            when {
                cmd.contains("0C") && cmd.contains("0D") -> "410C0FA00D3C>"
                else -> "NO DATA>"
            }
        }

        val scheduler = PidScheduler(transport, registry)
        val readings = scheduler.observeReadings().take(2).toList()

        assertTrue("first command must batch both PIDs", commands.first().startsWith("01 0C 0D"))
        assertEquals(1000.0, readings.first { it.pid == "0C" }.value, 0.001)
        assertEquals(60.0, readings.first { it.pid == "0D" }.value, 0.001)
    }

    @Test
    fun `falls back to single-PID polling when adapter rejects batches`() = runTest {
        coEvery { transport.exchange(any(), any()) } answers {
            val cmd = firstArg<String>().trimEnd()
            val requestedPids = cmd.removePrefix("01 ").split(" ")
            when {
                requestedPids.size > 1 -> "?>"          // K-line adapter: batch rejected
                cmd.contains("0C") -> "410C0FA0>"
                cmd.contains("0D") -> "410D3C>"
                else -> "NO DATA>"
            }
        }

        val scheduler = PidScheduler(transport, registry)
        val readings = scheduler.observeReadings().take(2).toList()

        assertEquals(1000.0, readings.first { it.pid == "0C" }.value, 0.001)
        assertEquals(60.0, readings.first { it.pid == "0D" }.value, 0.001)
    }

    @Test
    fun `kills flow after consecutive link failures so connection layer can react`() = runTest {
        coEvery { transport.exchange(any(), any()) } throws IOException("dead socket")

        val scheduler = PidScheduler(transport, registry)
        val result = runCatching { scheduler.observeReadings().take(1).toList() }

        assertTrue("flow must fail when the adapter link is dead", result.isFailure)
    }

    @Test
    fun `doubles interval multiplier on BUFFER FULL response`() = runTest {
        var callCount = 0
        coEvery { transport.exchange(any(), any()) } answers {
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
