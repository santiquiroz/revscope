package com.revscope.core.obd.taller.pruebas

import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuenteBarometricaTest {

    private val es = XmlStringResolver.es(com.revscope.core.obd.R::class.java, resDirs = RES)

    @Test
    fun `a 1 500 m la atmósfera estándar da unos 84,6 kPa`() {
        assertEquals(84.6, FuenteBarometrica.presionPorAltitud(1_500.0), 0.05)
    }

    @Test
    fun `al nivel del mar la atmósfera estándar da 101,325 kPa`() {
        assertEquals(101.325, FuenteBarometrica.presionPorAltitud(0.0), 1e-9)
    }

    @Test
    fun `70 kPa equivalen a unos 3 000 m`() {
        assertEquals(3_000.0, FuenteBarometrica.altitudPorPresion(70.0), 25.0)
    }

    @Test
    fun `la altitud y la presión son inversas`() {
        val kPa = FuenteBarometrica.presionPorAltitud(2_600.0)

        assertEquals(2_600.0, FuenteBarometrica.altitudPorPresion(kPa), 0.01)
    }

    @Test
    fun `el PID 33 de la ECU gana al barómetro del teléfono y a la altitud`() {
        val ref = FuenteBarometrica.elegir(ecuKpa = 75.0, ambiente = LecturasAmbiente(presionTelefonoHpa = 752.0, altitudGpsM = 2_500.0))!!

        assertEquals(OrigenBarometrica.ECU, ref.origen)
        assertEquals(75.0, ref.kPa, 1e-9)
    }

    @Test
    fun `sin PID 33 el barómetro del teléfono gana a la altitud y pasa de hPa a kPa`() {
        val ref = FuenteBarometrica.elegir(ecuKpa = null, ambiente = LecturasAmbiente(presionTelefonoHpa = 752.0, altitudGpsM = 2_500.0))!!

        assertEquals(OrigenBarometrica.TELEFONO, ref.origen)
        assertEquals(75.2, ref.kPa, 1e-9)
    }

    @Test
    fun `solo con la altitud GPS la referencia sale estimada y con incertidumbre de 3 kPa`() {
        val ref = FuenteBarometrica.elegir(ecuKpa = null, ambiente = LecturasAmbiente(altitudGpsM = 1_500.0))!!

        assertEquals(OrigenBarometrica.ALTITUD_GPS, ref.origen)
        assertEquals(84.6, ref.kPa, 0.05)
        assertEquals(3.0, ref.incertidumbreKpa, 1e-9)
        val etiqueta = ref.etiqueta(es)
        assertTrue(etiqueta, etiqueta.plano().contains("estimada"))
        assertTrue(etiqueta, etiqueta.plano().contains("±3 kPa"))
        assertEquals("Presión estimada por la altitud GPS (±3 kPa, por clima y altitud GPS)", etiqueta.plano())
    }

    @Test
    fun `las etiquetas de la fuente salen en español y en inglés`() {
        val ref = ReferenciaBarometrica(75.2, OrigenBarometrica.TELEFONO, FuenteBarometrica.INCERTIDUMBRE_TELEFONO_KPA)
        val en = XmlStringResolver.en(com.revscope.core.obd.R::class.java, resDirs = RES)

        assertEquals("el barómetro del teléfono (±0,5 kPa, típico)", ref.enFrase(es).plano())
        assertEquals("Phone barometer (±0.5 kPa, typical)", ref.etiqueta(en).plano())
        assertEquals("the pressure estimated from GPS altitude", OrigenBarometrica.ALTITUD_GPS.enFrase(en))
    }

    @Test
    fun `sin ninguna fuente no hay referencia`() {
        assertNull(FuenteBarometrica.elegir(ecuKpa = null, ambiente = null))
        assertNull(FuenteBarometrica.elegir(ecuKpa = null, ambiente = LecturasAmbiente()))
    }

    @Test
    fun `un PID 33 imposible o un barómetro fuera de rango se saltan`() {
        val ref = FuenteBarometrica.elegir(ecuKpa = 0.0, ambiente = LecturasAmbiente(presionTelefonoHpa = 0.0, altitudGpsM = 1_500.0))!!

        assertEquals(OrigenBarometrica.ALTITUD_GPS, ref.origen)
    }

    @Test
    fun `las disponibles salen en orden de prioridad para contrastar la primera con la siguiente`() {
        val todas = FuenteBarometrica.disponibles(ecuKpa = 75.0, ambiente = LecturasAmbiente(presionTelefonoHpa = 752.0, altitudGpsM = 2_500.0))

        assertEquals(listOf(OrigenBarometrica.ECU, OrigenBarometrica.TELEFONO, OrigenBarometrica.ALTITUD_GPS), todas.map { it.origen })
    }

    private companion object {
        val RES = listOf("src/main/res", "../common/src/main/res")
    }
}
