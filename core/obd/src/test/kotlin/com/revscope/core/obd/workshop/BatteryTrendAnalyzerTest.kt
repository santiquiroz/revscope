package com.revscope.core.obd.workshop

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryTrendAnalyzerTest {

    @Test
    fun `pocos viajes no alcanzan para diagnosticar`() {
        val result = BatteryTrendAnalyzer.analyze(listOf(14.1, 14.0, 14.2))
        assertEquals(BatteryTrendAnalyzer.Verdict.SIN_DATOS, result.verdict)
    }

    @Test
    fun `carga estable da OK`() {
        val result = BatteryTrendAnalyzer.analyze(listOf(14.1, 14.0, 14.2, 14.1, 14.0, 14.1, 14.2, 14.0))
        assertEquals(BatteryTrendAnalyzer.Verdict.OK, result.verdict)
    }

    @Test
    fun `caida sostenida de voltaje marca degradando`() {
        // recientes ~13.5, viejos ~14.1 → delta -0.6
        val result = BatteryTrendAnalyzer.analyze(listOf(13.5, 13.5, 13.6, 13.4, 14.1, 14.1, 14.2, 14.0))
        assertEquals(BatteryTrendAnalyzer.Verdict.DEGRADANDO, result.verdict)
    }

    @Test
    fun `promedio reciente bajo sin caida marca carga debil`() {
        val result = BatteryTrendAnalyzer.analyze(listOf(13.0, 13.1, 13.0, 13.1, 13.1, 13.0, 13.1, 13.0))
        assertEquals(BatteryTrendAnalyzer.Verdict.CARGA_DEBIL, result.verdict)
    }
}
