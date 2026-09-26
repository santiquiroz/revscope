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

    @Test
    fun `pedal y mariposa existen con prioridad 4 y escala porcentual`() {
        listOf("45", "47", "49", "4A", "4B", "4C", "5A").forEach { pid ->
            val def = registry.getDefinition(pid)
            assertNotNull("PID $pid", def)
            assertEquals("PID $pid", 4, def!!.priority)
            assertEquals("PID $pid", "%", def.unit)
            assertEquals(100.0, registry.evaluate(pid, byteArrayOf(0xFF.toByte()))!!.value, 0.01)
            assertEquals(50.2, registry.evaluate(pid, byteArrayOf(0x80.toByte()))!!.value, 0.1)
        }
    }

    @Test
    fun `pedal y mariposa caben en una sola trama con tres pids`() {
        val bytes = listOf("49", "4A", "11").sumOf { 1 + registry.getDefinition(it)!!.bytes }
        assertEquals(6, bytes)
    }
}
