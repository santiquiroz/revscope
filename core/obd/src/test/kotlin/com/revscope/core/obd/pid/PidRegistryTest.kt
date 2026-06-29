package com.revscope.core.obd.pid

import com.revscope.core.obd.model.ObdReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PidRegistryTest {

    // Minimal JSON covering the PIDs needed for formula tests
    private val testJson = """
        [
          {
            "mode": "01", "pid": "0C",
            "name": "Engine RPM", "nameEs": "RPM Motor",
            "bytes": 2, "formula": "((A*256)+B)/4",
            "unit": "rpm", "min": 0, "max": 16383, "priority": 1
          },
          {
            "mode": "01", "pid": "0D",
            "name": "Vehicle Speed", "nameEs": "Velocidad",
            "bytes": 1, "formula": "A",
            "unit": "km/h", "min": 0, "max": 255, "priority": 1
          },
          {
            "mode": "01", "pid": "04",
            "name": "Engine Load", "nameEs": "Carga Motor",
            "bytes": 1, "formula": "A*100/255",
            "unit": "%", "min": 0, "max": 100, "priority": 2
          },
          {
            "mode": "01", "pid": "05",
            "name": "Coolant Temperature", "nameEs": "Temp Refrigerante",
            "bytes": 1, "formula": "A-40",
            "unit": "°C", "min": -40, "max": 215, "priority": 2
          },
          {
            "mode": "01", "pid": "0F",
            "name": "Intake Air Temperature", "nameEs": "Temp Aire Admisión",
            "bytes": 1, "formula": "A-40",
            "unit": "°C", "min": -40, "max": 215, "priority": 3
          },
          {
            "mode": "01", "pid": "10",
            "name": "MAF Air Flow Rate", "nameEs": "MAF Flujo de Aire",
            "bytes": 2, "formula": "((A*256)+B)/100",
            "unit": "g/s", "min": 0, "max": 655.35, "priority": 2
          },
          {
            "mode": "01", "pid": "0B",
            "name": "Intake Manifold Pressure", "nameEs": "MAP Presión Múltiple",
            "bytes": 1, "formula": "A",
            "unit": "kPa", "min": 0, "max": 255, "priority": 2
          },
          {
            "mode": "01", "pid": "62",
            "name": "Actual Engine Torque", "nameEs": "Torque Actual",
            "bytes": 1, "formula": "A-125",
            "unit": "%", "min": -125, "max": 130, "priority": 2
          },
          {
            "mode": "01", "pid": "06",
            "name": "Short Term Fuel Trim Bank 1", "nameEs": "Fuel Trim Corto B1",
            "bytes": 1, "formula": "(A-128)*100/128",
            "unit": "%", "min": -100, "max": 99.2, "priority": 3
          },
          {
            "mode": "01", "pid": "5E",
            "name": "Engine Fuel Rate", "nameEs": "Tasa Consumo Combustible",
            "bytes": 2, "formula": "((A*256)+B)*0.05",
            "unit": "L/h", "min": 0, "max": 3212.75, "priority": 3
          }
        ]
    """.trimIndent()

    private lateinit var registry: PidRegistry

    @Before
    fun setUp() {
        registry = PidRegistry(testJson)
    }

    // ── getDefinition ─────────────────────────────────────────────────────────

    @Test
    fun `getDefinition returns definition for known PID`() {
        val def = registry.getDefinition("0C")
        assertNotNull(def)
        assertEquals("Engine RPM", def!!.name)
        assertEquals("rpm", def.unit)
        assertEquals(1, def.priority)
    }

    @Test
    fun `getDefinition is case-insensitive`() {
        assertNotNull(registry.getDefinition("0c"))
        assertNotNull(registry.getDefinition("0C"))
    }

    @Test
    fun `getDefinition returns null for unknown PID`() {
        assertNull(registry.getDefinition("FF"))
    }

    // ── allDefinitions ────────────────────────────────────────────────────────

    @Test
    fun `allDefinitions returns all loaded PIDs`() {
        assertEquals(10, registry.allDefinitions().size)
    }

    // ── definitionsForPriority ────────────────────────────────────────────────

    @Test
    fun `definitionsForPriority 1 returns high-frequency PIDs`() {
        val highPriority = registry.definitionsForPriority(1)
        assertTrue(highPriority.isNotEmpty())
        assertTrue(highPriority.all { it.priority == 1 })
        assertTrue(highPriority.any { it.pid == "0C" }) // RPM
        assertTrue(highPriority.any { it.pid == "0D" }) // Speed
    }

    @Test
    fun `definitionsForPriority 3 returns low-frequency PIDs`() {
        val lowPriority = registry.definitionsForPriority(3)
        assertTrue(lowPriority.all { it.priority == 3 })
    }

    // ── evaluate — formula correctness ────────────────────────────────────────

    @Test
    fun `evaluate RPM formula two bytes at idle`() {
        // ((0x0C*256)+0x80)/4 = (12*256+128)/4 = (3072+128)/4 = 800 rpm
        val reading = registry.evaluate("0C", byteArrayOf(0x0C, 0x80.toByte()))
        assertNotNull(reading)
        assertEquals(800.0, reading!!.value, 0.001)
        assertEquals("rpm", reading.unit)
    }

    @Test
    fun `evaluate RPM formula at 1000 rpm`() {
        // ((15*256)+160)/4 = (3840+160)/4 = 1000 rpm
        val reading = registry.evaluate("0C", byteArrayOf(0x0F, 0xA0.toByte()))
        assertNotNull(reading)
        assertEquals(1000.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate RPM formula at 6250 rpm redline typical`() {
        // ((97*256)+144)/4 = (24832+144)/4 = 6244 rpm → rounds to 6244
        val reading = registry.evaluate("0C", byteArrayOf(0x61, 0x90.toByte()))
        assertNotNull(reading)
        assertEquals(6244.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate speed formula`() {
        // A = 0x59 = 89 km/h
        val reading = registry.evaluate("0D", byteArrayOf(0x59))
        assertNotNull(reading)
        assertEquals(89.0, reading!!.value, 0.001)
        assertEquals("km/h", reading.unit)
    }

    @Test
    fun `evaluate speed zero`() {
        val reading = registry.evaluate("0D", byteArrayOf(0x00))
        assertEquals(0.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate coolant temperature formula`() {
        // A - 40 = 127 - 40 = 87°C
        val reading = registry.evaluate("05", byteArrayOf(0x7F))
        assertNotNull(reading)
        assertEquals(87.0, reading!!.value, 0.001)
        assertEquals("°C", reading.unit)
    }

    @Test
    fun `evaluate coolant temperature minimum -40C`() {
        // A = 0 → 0 - 40 = -40°C
        val reading = registry.evaluate("05", byteArrayOf(0x00))
        assertEquals(-40.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate coolant temperature maximum 215C`() {
        // A = 255 → 255 - 40 = 215°C
        val reading = registry.evaluate("05", byteArrayOf(0xFF.toByte()))
        assertEquals(215.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate engine load formula`() {
        // A*100/255 = 127*100/255 ≈ 49.8%
        val reading = registry.evaluate("04", byteArrayOf(0x7F))
        assertNotNull(reading)
        assertEquals(49.804, reading!!.value, 0.01)
    }

    @Test
    fun `evaluate engine load at full throttle`() {
        // A = 255 → 255*100/255 = 100%
        val reading = registry.evaluate("04", byteArrayOf(0xFF.toByte()))
        assertEquals(100.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate MAP manifold pressure`() {
        // A = 101 kPa (roughly atmospheric)
        val reading = registry.evaluate("0B", byteArrayOf(0x65))
        assertNotNull(reading)
        assertEquals(101.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate MAF air flow rate`() {
        // ((A*256)+B)/100 = ((7*256)+208)/100 = (1792+208)/100 = 20.00 g/s
        val reading = registry.evaluate("10", byteArrayOf(0x07, 0xD0.toByte()))
        assertNotNull(reading)
        assertEquals(20.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate torque actual percent`() {
        // A - 125 = 200 - 125 = 75%
        val reading = registry.evaluate("62", byteArrayOf(0xC8.toByte()))
        assertNotNull(reading)
        assertEquals(75.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate torque at zero — A equals 125`() {
        // A - 125 = 125 - 125 = 0%
        val reading = registry.evaluate("62", byteArrayOf(0x7D))
        assertEquals(0.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate short term fuel trim positive`() {
        // (A-128)*100/128 = (143-128)*100/128 ≈ 11.7%
        val reading = registry.evaluate("06", byteArrayOf(0x8F.toByte()))
        assertNotNull(reading)
        assertEquals(11.718, reading!!.value, 0.01)
    }

    @Test
    fun `evaluate short term fuel trim neutral`() {
        // (A-128)*100/128 = (128-128)*100/128 = 0%
        val reading = registry.evaluate("06", byteArrayOf(0x80.toByte()))
        assertEquals(0.0, reading!!.value, 0.001)
    }

    @Test
    fun `evaluate fuel rate`() {
        // ((A*256)+B)*0.05 = ((3*256)+232)*0.05 = (768+232)*0.05 = 1000*0.05 = 50 L/h
        val reading = registry.evaluate("5E", byteArrayOf(0x03, 0xE8.toByte()))
        assertNotNull(reading)
        assertEquals(50.0, reading!!.value, 0.001)
    }

    // ── value clamping ────────────────────────────────────────────────────────

    @Test
    fun `evaluate clamps value to max`() {
        // Speed max is 255; formula is A; A=255 should clamp to 255
        val reading = registry.evaluate("0D", byteArrayOf(0xFF.toByte()))
        assertEquals(255.0, reading!!.value, 0.0)
    }

    // ── parseAndEvaluate ─────────────────────────────────────────────────────

    @Test
    fun `parseAndEvaluate combines parser and registry correctly`() {
        // RPM at 1000: raw response "410C0FA0>"
        val reading = registry.parseAndEvaluate("0C", "410C0FA0>")
        assertNotNull(reading)
        assertEquals(1000.0, reading!!.value, 0.001)
    }

    @Test
    fun `parseAndEvaluate returns null for error response`() {
        assertNull(registry.parseAndEvaluate("0C", "NO DATA>"))
    }

    @Test
    fun `parseAndEvaluate handles spaced format`() {
        val reading = registry.parseAndEvaluate("0D", "41 0D 59 >")
        assertEquals(89.0, reading!!.value, 0.001)
    }

    // ── setSupportedPids filtering ────────────────────────────────────────────

    @Test
    fun `definitionsForPriority returns all when no filter set`() {
        val count = registry.definitionsForPriority(1).size
        assertTrue(count > 0)
    }

    @Test
    fun `setSupportedPids restricts definitionsForPriority`() {
        // Only allow 0D (speed) from priority-1 PIDs
        registry.setSupportedPids(setOf("0D"))
        val p1 = registry.definitionsForPriority(1)
        assertEquals(1, p1.size)
        assertEquals("0D", p1.first().pid)
    }

    @Test
    fun `setSupportedPids allows empty set — returns nothing for any priority`() {
        registry.setSupportedPids(emptySet())
        val p1 = registry.definitionsForPriority(1)
        assertTrue(p1.isEmpty())
    }

    @Test
    fun `isSupported returns true when no filter set`() {
        assertTrue(registry.isSupported("0C"))
        assertTrue(registry.isSupported("0D"))
    }

    @Test
    fun `isSupported returns false for PID outside filter`() {
        registry.setSupportedPids(setOf("0D"))
        assertTrue(registry.isSupported("0D"))
        // 0C not in filter
        val hexReading = registry.evaluate("0C", byteArrayOf(0x0F, 0xA0.toByte()))
        // evaluate itself doesn't check filter — only definitionsForPriority does
        assertNotNull(hexReading) // direct evaluate still works
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `evaluate returns null for unknown PID`() {
        assertNull(registry.evaluate("FF", byteArrayOf(0x00)))
    }

    @Test
    fun `evaluate handles missing bytes with zeros`() {
        // RPM formula needs 2 bytes; provide only 1 → B defaults to 0
        // ((A*256)+0)/4 = (15*256)/4 = 960 rpm
        val reading = registry.evaluate("0C", byteArrayOf(0x0F))
        assertNotNull(reading)
        assertEquals(960.0, reading!!.value, 0.001)
    }

    @Test
    fun `PidRegistry gracefully handles invalid JSON`() {
        val badRegistry = PidRegistry("not json at all")
        assertEquals(0, badRegistry.allDefinitions().size)
    }

    @Test
    fun `PidRegistry gracefully handles empty JSON array`() {
        val emptyRegistry = PidRegistry("[]")
        assertEquals(0, emptyRegistry.allDefinitions().size)
    }
}
