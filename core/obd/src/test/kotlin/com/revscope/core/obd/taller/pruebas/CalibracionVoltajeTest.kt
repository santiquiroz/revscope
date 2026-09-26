package com.revscope.core.obd.taller.pruebas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibracionVoltajeTest {

    @Test
    fun `el desfase es lo del multímetro en los bornes menos lo del adaptador`() {
        val d = CalibracionVoltaje.desde(multimetroV = 12.62, adaptadorV = 12.4)!!

        assertEquals(0.22, d.voltios, 1e-9)
        assertEquals(DesfaseVoltaje.ORIGEN_MULTIMETRO, d.origen)
        assertTrue(d.calibrado)
        assertEquals(12.62, d.corregir(12.4), 1e-9)
    }

    @Test
    fun `sin alguna de las dos lecturas, fuera de rango o con más de 1 V de diferencia no calibra`() {
        assertNull(CalibracionVoltaje.desde(12.6, null))
        assertNull(CalibracionVoltaje.desde(null, 12.4))
        assertNull(CalibracionVoltaje.desde(1.26, 12.4))
        assertNull(CalibracionVoltaje.desde(13.6, 12.4))
    }

    @Test
    fun `lee la coma decimal y descarta lo que no es un voltaje de batería`() {
        assertEquals(12.6, CalibracionVoltaje.leer(" 12,6 ")!!, 1e-9)
        assertEquals(12.6, CalibracionVoltaje.leer("12.6")!!, 1e-9)
        assertNull(CalibracionVoltaje.leer("126"))
        assertNull(CalibracionVoltaje.leer("abc"))
    }

    @Test
    fun `el texto dice el desfase con su origen o que AT RV está sin calibrar`() {
        assertEquals("+0,22 V · Calibrado con el multímetro", CalibracionVoltaje.texto(DesfaseVoltaje.medido(12.62, 12.4)).plano())
        assertEquals("Sin calibrar: AT RV mide con ±0,1-0,2 V (típico)", CalibracionVoltaje.texto(DesfaseVoltaje.SIN_CALIBRAR).plano())
        assertFalse(DesfaseVoltaje.SIN_CALIBRAR.calibrado)
    }

    @Test
    fun `la altitud del último fix vale si tiene menos de una hora`() {
        val ahora = 1_790_000_000_000L

        assertEquals(1_480.0, AltitudVigente.de(1_480.0, ahora - 30 * 60_000L, ahora)!!, 1e-9)
        assertNull(AltitudVigente.de(1_480.0, ahora - 2 * 60 * 60_000L, ahora))
        assertNull(AltitudVigente.de(null, ahora, ahora))
    }
}
