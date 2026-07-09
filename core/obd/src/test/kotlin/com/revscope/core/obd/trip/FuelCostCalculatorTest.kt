package com.revscope.core.obd.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCostCalculatorTest {

    private val precioGalonCop = 16_000.0

    // ── fromFuelRate (PID 5E, L/h) ──────────────────────────────────────────

    @Test
    fun `tasa constante de 1,8 L h por 30 minutos consume 0,9 litros`() {
        val points = listOf(0L to 1.8, 1_800_000L to 1.8)
        val result = FuelCostCalculator.fromFuelRate(points, precioGalonCop)
        assertEquals(0.9, result!!.liters, 0.0001)
    }

    @Test
    fun `el costo se calcula desde litros consumidos y precio por galon`() {
        val points = listOf(0L to 1.8, 1_800_000L to 1.8)
        val result = FuelCostCalculator.fromFuelRate(points, precioGalonCop)
        val expectedCost = 0.9 / FuelCostCalculator.LITERS_PER_GALLON * precioGalonCop
        assertEquals(expectedCost, result!!.costCop, 0.01)
    }

    @Test
    fun `fromFuelRate no es estimado`() {
        val points = listOf(0L to 1.8, 1_800_000L to 1.8)
        assertEquals(false, FuelCostCalculator.fromFuelRate(points, precioGalonCop)!!.estimado)
    }

    @Test
    fun `fromFuelRate con lista vacia retorna null`() {
        assertNull(FuelCostCalculator.fromFuelRate(emptyList(), precioGalonCop))
    }

    @Test
    fun `fromFuelRate con un solo punto retorna null`() {
        assertNull(FuelCostCalculator.fromFuelRate(listOf(0L to 1.8), precioGalonCop))
    }

    // ── fromMaf (PID 10, g/s de aire — fallback) ────────────────────────────

    @Test
    fun `maf constante de 14,7 g s durante 750 s consume 1 litro`() {
        // combustible g/s = maf/14.7 = 1 g/s constante → 750 g en 750 s → 750/750 = 1 L
        val points = listOf(0L to 14.7, 750_000L to 14.7)
        val result = FuelCostCalculator.fromMaf(points, precioGalonCop)
        assertEquals(1.0, result!!.liters, 0.0001)
    }

    @Test
    fun `fromMaf marca el resultado como estimado`() {
        val points = listOf(0L to 14.7, 750_000L to 14.7)
        assertEquals(true, FuelCostCalculator.fromMaf(points, precioGalonCop)!!.estimado)
    }

    @Test
    fun `fromMaf calcula costo desde litros y precio por galon`() {
        val points = listOf(0L to 14.7, 750_000L to 14.7)
        val result = FuelCostCalculator.fromMaf(points, precioGalonCop)
        val expectedCost = 1.0 / FuelCostCalculator.LITERS_PER_GALLON * precioGalonCop
        assertEquals(expectedCost, result!!.costCop, 0.01)
    }

    @Test
    fun `fromMaf con lista vacia retorna null`() {
        assertNull(FuelCostCalculator.fromMaf(emptyList(), precioGalonCop))
    }

    @Test
    fun `fromMaf con un solo punto retorna null`() {
        assertNull(FuelCostCalculator.fromMaf(listOf(0L to 14.7), precioGalonCop))
    }
}
