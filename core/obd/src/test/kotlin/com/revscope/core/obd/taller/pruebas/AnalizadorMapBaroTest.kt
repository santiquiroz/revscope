package com.revscope.core.obd.taller.pruebas

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.pruebas.AnalizadorMapBaro.Pasos
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalizadorMapBaroTest {

    private val moto = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())

    private fun datos(map: (Int) -> Double?, baroEcu: Double? = null, ambiente: LecturasAmbiente? = null): DatosPrueba {
        val muestras = (0 until 100).flatMap { i ->
            val tMicros = i * 100_000L
            listOfNotNull(
                map(i)?.let { MuestraCaptura(0, tMicros, AnalizadorMapBaro.PID_MAP, it, i.toLong(), 60) },
                baroEcu?.let { MuestraCaptura(0, tMicros, AnalizadorMapBaro.PID_BARO, it, i.toLong(), 60) },
            )
        }
        return DatosPrueba(
            TipoPrueba.MAP_BARO,
            muestras,
            listOf(SegmentoPaso(Pasos.CONTACTO, 0, 10_000, 1_000)),
            contexto = ContextoAnalisis(ambiente = ambiente),
        )
    }

    private fun resultado(d: DatosPrueba) = AnalizadorMapBaro.analizar(d, moto).plano()

    @Test
    fun `70 kPa contra 84,6 estimada por la altitud queda fuera de banda aun con la incertidumbre`() {
        val r = resultado(datos({ 70.0 }, ambiente = LecturasAmbiente(altitudGpsM = 1_500.0)))
        val a = r.detalle as AnalisisMapBaro

        assertEquals(PatronMapBaro.MAP_BAJO, a.patron)
        assertEquals(Veredicto.FALLA, r.veredicto)
        assertEquals(OrigenBarometrica.ALTITUD_GPS, a.referencia!!.origen)
        assertEquals(-14.6, a.deltaKpa!!, 0.05)
        assertEquals(6.0, a.toleranciaKpa!!, 1e-9)
        assertEquals(3_000.0, a.altitudEquivalenteM!!, 25.0)
        val c = a.comprobaciones.single { it.clave == ClavesBanda.MAP_KOEO_VS_BARO_KPA }
        assertFalse(c.cumple)
        assertEquals(PosicionEnBanda.ALTO, c.posicion)
        assertTrue(r.interpretacion, r.interpretacion.contains("estimada"))
        assertTrue(r.interpretacion, r.interpretacion.contains("±3 kPa"))
        assertTrue(r.interpretacion, r.interpretacion.contains("3 000 m") || r.interpretacion.contains("3000 m"))
        assertTrue(r.interpretacion, r.interpretacion.contains("Típico (editable)"))
        assertTrue(r.interpretacion, r.interpretacion.contains("compatible con"))
    }

    @Test
    fun `el MAP igual al barómetro del teléfono está bien`() {
        val r = resultado(datos({ 84.0 }, ambiente = LecturasAmbiente(presionTelefonoHpa = 845.0)))
        val a = r.detalle as AnalisisMapBaro

        assertEquals(PatronMapBaro.NORMAL, a.patron)
        assertEquals(Veredicto.OK, r.veredicto)
        assertEquals(OrigenBarometrica.TELEFONO, a.referencia!!.origen)
        assertTrue(a.comprobaciones.single { it.clave == ClavesBanda.MAP_KOEO_VS_BARO_KPA }.cumple)
    }

    @Test
    fun `una diferencia que solo cabe por la incertidumbre de la estimación no es concluyente`() {
        val r = resultado(datos({ 88.5 }, ambiente = LecturasAmbiente(altitudGpsM = 1_500.0)))
        val a = r.detalle as AnalisisMapBaro

        assertEquals(PatronMapBaro.NO_CONCLUYENTE, a.patron)
        assertEquals(Veredicto.ATENCION, r.veredicto)
        assertTrue(r.siguientePaso!!, r.siguientePaso!!.contains("barómetro"))
    }

    @Test
    fun `un MAP por encima de la barométrica apunta a señal alta`() {
        val r = resultado(datos({ 100.0 }, ambiente = LecturasAmbiente(presionTelefonoHpa = 845.0)))

        assertEquals(PatronMapBaro.MAP_ALTO, (r.detalle as AnalisisMapBaro).patron)
        assertEquals(Veredicto.FALLA, r.veredicto)
        assertTrue(r.interpretacion, r.interpretacion.contains("P0108"))
    }

    @Test
    fun `con el PID 33 como referencia avisa que la ECU puede calcularla con el mismo MAP y contrasta con el teléfono`() {
        val r = resultado(datos({ 75.0 }, baroEcu = 75.0, ambiente = LecturasAmbiente(presionTelefonoHpa = 846.0)))
        val a = r.detalle as AnalisisMapBaro

        assertEquals(OrigenBarometrica.ECU, a.referencia!!.origen)
        assertEquals(OrigenBarometrica.TELEFONO, a.alternativa!!.origen)
        assertTrue(r.interpretacion, r.interpretacion.contains("mismo sensor MAP"))
        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("barómetro del teléfono") })
        assertEquals(PatronMapBaro.NO_CONCLUYENTE, a.patron)
        assertTrue(r.interpretacion, r.interpretacion.contains("pero no con el barómetro del teléfono"))
        assertTrue(r.siguientePaso!!, r.siguientePaso!!.contains("plantilla MAP"))
    }

    @Test
    fun `con el PID 33 y el teléfono de acuerdo el MAP está bien`() {
        val r = resultado(datos({ 84.0 }, baroEcu = 84.0, ambiente = LecturasAmbiente(presionTelefonoHpa = 845.0)))

        assertEquals(PatronMapBaro.NORMAL, (r.detalle as AnalisisMapBaro).patron)
        assertTrue(r.hallazgos.toString(), r.hallazgos.isEmpty())
    }

    @Test
    fun `sin MAP no hay datos`() {
        val r = resultado(datos({ null }, ambiente = LecturasAmbiente(altitudGpsM = 1_500.0)))

        assertEquals(PatronMapBaro.SIN_DATOS, (r.detalle as AnalisisMapBaro).patron)
        assertEquals(Veredicto.ATENCION, r.veredicto)
    }

    @Test
    fun `sin ninguna referencia informa el MAP y su altitud equivalente y pide una fuente`() {
        val r = resultado(datos({ 70.0 }))
        val a = r.detalle as AnalisisMapBaro

        assertEquals(PatronMapBaro.SIN_REFERENCIA, a.patron)
        assertEquals(Veredicto.ATENCION, r.veredicto)
        assertNull(a.deltaKpa)
        assertTrue(r.interpretacion, r.interpretacion.contains("70 kPa"))
        assertTrue(r.siguientePaso!!, r.siguientePaso!!.contains("ubicación"))
    }

    @Test
    fun `un MAP que se mueve con el motor apagado es un hallazgo`() {
        val r = resultado(datos({ i -> if (i % 10 < 5) 82.0 else 87.0 }, ambiente = LecturasAmbiente(presionTelefonoHpa = 845.0)))

        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("varió") })
    }

    @Test
    fun `el detalle guarda el MAP, la referencia con su origen y la diferencia`() {
        val json = JSONObject(AnalizadorMapBaro.analizar(datos({ 70.0 }, ambiente = LecturasAmbiente(altitudGpsM = 1_500.0)), moto).detalle.json().toString())

        assertEquals("MAP_BAJO", json.getString("patron"))
        assertEquals(70.0, json.getDouble("mapKpa"), 1e-9)
        assertEquals("ALTITUD_GPS", json.getJSONObject("referencia").getString("origen"))
        assertEquals(-14.6, json.getDouble("deltaKpa"), 0.05)
    }
}
