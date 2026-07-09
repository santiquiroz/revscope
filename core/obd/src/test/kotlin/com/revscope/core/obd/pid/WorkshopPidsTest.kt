package com.revscope.core.obd.pid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkshopPidsTest {

    private val registry = PidRegistry(TestPids.load())

    @Test
    fun `fuel trim B2 centrado en cero`() {
        val reading = registry.evaluate("08", byteArrayOf(128.toByte()))
        assertNotNull(reading)
        assertEquals(0.0, reading!!.value, 0.01)
    }

    @Test
    fun `lambda comandado uno es estequiometrico`() {
        // 0x8000 = 32768 → 32768/32768 = 1.0
        val reading = registry.evaluate("44", byteArrayOf(0x80.toByte(), 0x00))
        assertEquals(1.0, reading!!.value, 0.001)
    }

    @Test
    fun `temperatura catalizador con offset`() {
        // A=1, B=194 → (256+194)/10 - 40 = 5.0 °C
        val reading = registry.evaluate("3C", byteArrayOf(0x01, 0xC2.toByte()))
        assertEquals(5.0, reading!!.value, 0.01)
    }

    @Test
    fun `avance de encendido negativo posible`() {
        val reading = registry.evaluate("0E", byteArrayOf(0))
        assertEquals(-64.0, reading!!.value, 0.01)
    }

    @Test
    fun `los pids de taller tienen prioridad 4`() {
        listOf("08", "09", "0A", "0E", "15", "18", "19", "2E", "3C", "44").forEach { pid ->
            assertEquals("PID $pid", 4, registry.getDefinition(pid)!!.priority)
        }
    }
}
