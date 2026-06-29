package com.revscope.core.obd.telemetry

import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DerivedMetricsEngineTest {

    private val engine = DerivedMetricsEngine()

    // ── BOOST ──────────────────────────────────────────────────────────────────

    @Test
    fun `calculates positive boost for turbocharged reading`() = runTest {
        val derived = engine.observeDerived(flowOf(reading("0B", 120.0, "kPa"))).toList()

        assertEquals(1, derived.size)
        assertEquals("BOOST", derived[0].pid)
        assertEquals(19.0, derived[0].value, 0.001)   // 120 - 101 = 19
        assertEquals("kPa", derived[0].unit)
    }

    @Test
    fun `calculates negative boost for naturally aspirated engine`() = runTest {
        val derived = engine.observeDerived(flowOf(reading("0B", 90.0, "kPa"))).toList()

        assertEquals(1, derived.size)
        assertEquals(-11.0, derived[0].value, 0.001)  // 90 - 101 = -11
    }

    // ── GEAR ───────────────────────────────────────────────────────────────────

    @Test
    fun `calculates gear from RPM and speed`() = runTest {
        // ratio = 60 * 1000 / 2000 = 30.0 → closest entry is 3 → 31.0
        val derived = engine.observeDerived(
            flowOf(reading("0C", 2000.0, "rpm"), reading("0D", 60.0, "km/h"))
        ).toList()

        val gear = derived.last { it.pid == "GEAR" }
        assertEquals(3.0, gear.value, 0.001)
        assertEquals("", gear.unit)
    }

    @Test
    fun `returns gear 0 when vehicle is stopped`() = runTest {
        val derived = engine.observeDerived(
            flowOf(reading("0C", 800.0, "rpm"), reading("0D", 0.0, "km/h"))
        ).toList()

        val gearReadings = derived.filter { it.pid == "GEAR" }
        assertTrue(gearReadings.isNotEmpty())
        assertTrue("gear must be 0 when stopped", gearReadings.all { it.value == 0.0 })
    }

    @Test
    fun `returns gear 0 when RPM is below idle threshold`() = runTest {
        val derived = engine.observeDerived(
            flowOf(reading("0C", 400.0, "rpm"), reading("0D", 15.0, "km/h"))
        ).toList()

        val gear = derived.last { it.pid == "GEAR" }
        assertEquals(0.0, gear.value, 0.001)
    }

    @Test
    fun `gear not emitted when only RPM is available`() = runTest {
        // Speed (0D) is missing — gear cannot be computed
        val derived = engine.observeDerived(flowOf(reading("0C", 2000.0, "rpm"))).toList()

        assertTrue(derived.none { it.pid == "GEAR" })
    }

    // ── POWER ──────────────────────────────────────────────────────────────────

    @Test
    fun `calculates power from torque and RPM`() = runTest {
        // torqueNm = 200 Nm ref * 100 pct / 100 = 200 Nm
        // power = 200 * 3000 / 9549 ≈ 62.83 kW
        val derived = engine.observeDerived(
            flowOf(
                reading("63", 200.0, "Nm"),
                reading("62", 100.0, "%"),
                reading("0C", 3000.0, "rpm"),
            )
        ).toList()

        val power = derived.last { it.pid == "POWER" }
        assertEquals(62.83, power.value, 0.1)
        assertEquals("kW", power.unit)
    }

    @Test
    fun `power not emitted when RPM is missing`() = runTest {
        val derived = engine.observeDerived(
            flowOf(reading("63", 200.0, "Nm"), reading("62", 80.0, "%"))
        ).toList()

        assertTrue(derived.none { it.pid == "POWER" })
    }

    @Test
    fun `power updates when new RPM arrives after torque data`() = runTest {
        // All torque data present, then RPM arrives — power emits on RPM update
        val derived = engine.observeDerived(
            flowOf(
                reading("62", 50.0, "%"),
                reading("63", 300.0, "Nm"),
                reading("0C", 4000.0, "rpm"),
            )
        ).toList()

        val powerReadings = derived.filter { it.pid == "POWER" }
        assertEquals(1, powerReadings.size)
        // torqueNm = 300 * 50 / 100 = 150 Nm; power = 150 * 4000 / 9549 ≈ 62.83 kW
        assertEquals(62.83, powerReadings[0].value, 0.1)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun reading(pid: String, value: Double, unit: String) =
        ObdReading(pid = pid, value = value, unit = unit, timestamp = 0L)
}
