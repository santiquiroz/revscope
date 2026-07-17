package com.revscope.core.obd.legal

import org.junit.Assert.assertEquals
import org.junit.Test

class CityRulesFormatterTest {

    @Test
    fun `resumen de rotacion semanal lista dias y horario`() {
        val resumen = CityRulesFormatter.resumen(PicoYPlacaEngine.MEDELLIN_2026_S1)
        assertEquals("L:1,7 M:0,3 X:4,6 J:5,9 V:2,8 · 5-20h", resumen)
    }

    @Test
    fun `resumen de date parity incluye motos exentas`() {
        val resumen = CityRulesFormatter.resumen(PicoYPlacaEngine.BOGOTA_2026)
        assertEquals("Impar:6,7,8,9,0 Par:1,2,3,4,5 · 6-21h · motos exentas", resumen)
    }
}
