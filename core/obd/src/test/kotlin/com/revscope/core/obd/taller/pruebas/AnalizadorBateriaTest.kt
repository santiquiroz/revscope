package com.revscope.core.obd.taller.pruebas

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos
import com.revscope.core.obd.taller.pruebas.BateriaSintetica.Perfil
import com.revscope.core.obd.taller.pruebas.BateriaSintetica.arranque
import com.revscope.core.obd.taller.pruebas.BateriaSintetica.arranqueConHueco
import com.revscope.core.obd.taller.pruebas.BateriaSintetica.arranqueQueApagaElAdaptador
import com.revscope.core.obd.taller.pruebas.BateriaSintetica.datos
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalizadorBateriaTest {

    private val moto = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())
    private val carro = ResolutorBandas.resolverTodas(VehicleType.CAR, emptyList())

    private fun resultado(p: Perfil, bandas: Map<String, BandaReferencia> = moto) = AnalizadorBateria.analizar(datos(p), bandas).plano()

    private fun comprobacion(r: ResultadoPrueba, clave: String) = (r.detalle as AnalisisBateria).comprobaciones.single { it.clave == clave }

    // El caso de la Benelli: 12,2 V con el contacto puesto y la farola encendida, y carga de 14,1-14,3 V.
    private val benelli = Perfil(contacto = { 12.2 }, arranque = arranque(reposo = 12.2, valle = 10.6), minimo = { 13.4 })

    @Test
    fun `12,2 V en contacto con la farola encendida cumple la banda con farola`() {
        val r = resultado(benelli)
        val c = comprobacion(r, ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V)

        assertTrue(c.cumple)
        assertEquals(12.2, c.valor!!, 1e-9)
        assertTrue((r.detalle as AnalisisBateria).conFarola)
        assertTrue(r.interpretacion, r.interpretacion.contains("farola"))
        assertTrue(r.interpretacion, r.interpretacion.contains("AHO"))
    }

    @Test
    fun `la carga de 14,1-14,3 V a rpm altas cumple la banda de carga y el caso sale normal`() {
        val r = resultado(benelli)
        val c = comprobacion(r, ClavesBanda.CARGA_V)

        assertTrue(c.cumple)
        assertEquals(14.2, c.valor!!, 0.01)
        assertEquals(PatronBateria.NORMAL, (r.detalle as AnalisisBateria).patron)
        assertEquals(Veredicto.OK, r.veredicto)
        assertTrue(r.interpretacion, r.interpretacion.contains("13,5–14,5 V"))
        assertTrue(r.interpretacion, r.interpretacion.contains("Típico (editable)"))
    }

    @Test
    fun `en un carro 12,2 V en contacto está por debajo de la banda sin cargas`() {
        val r = resultado(benelli.copy(contexto = ContextoAnalisis(tipoVehiculo = VehicleType.CAR)), carro)
        val c = comprobacion(r, ClavesBanda.BATERIA_CONTACTO_V)

        assertFalse(c.cumple)
        assertEquals(PatronBateria.BATERIA_BAJA, (r.detalle as AnalisisBateria).patron)
        assertEquals(Veredicto.ATENCION, r.veredicto)
    }

    @Test
    fun `un valle de 8,9 V al arrancar no cumple y apunta a batería o conexiones`() {
        val r = resultado(Perfil(arranque = arranque(valle = 8.9)))
        val a = r.detalle as AnalisisBateria
        val c = comprobacion(r, ClavesBanda.ARRANQUE_MIN_V)

        assertEquals(8.9, a.arranque!!.valleV!!, 1e-9)
        assertFalse(c.cumple)
        assertEquals(PosicionEnBanda.BAJO, c.posicion)
        assertEquals(PatronBateria.ARRANQUE_BAJO, a.patron)
        assertEquals(Veredicto.FALLA, r.veredicto)
        assertTrue(r.titulo, r.titulo.contains("8,9 V"))
        assertTrue(r.interpretacion, r.interpretacion.contains("≥ 9,6 V"))
        assertTrue(r.interpretacion, r.interpretacion.contains("compatible con", ignoreCase = true))
        assertTrue(r.siguientePaso!!, r.siguientePaso!!.contains("multímetro"))
    }

    @Test
    fun `si se pierde el enlace en el arranque es un hallazgo de adaptador reiniciado y no un error`() {
        val p = Perfil(
            arranque = arranqueQueApagaElAdaptador(),
            arranqueMs = 4_000,
            minimo = null,
            rpmAltas = null,
            contexto = ContextoAnalisis(enlacePerdidoEn = Pasos.ARRANQUE),
        )
        val r = resultado(p)
        val a = r.detalle as AnalisisBateria

        assertTrue(a.arranque!!.adaptadorReiniciado)
        assertTrue(a.arranque!!.enlacePerdido)
        assertEquals(PatronBateria.ADAPTADOR_REINICIADO, a.patron)
        assertEquals(Veredicto.ATENCION, r.veredicto)
        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("adaptador se reinició") })
        assertTrue(r.interpretacion, r.interpretacion.contains("8 V"))
        assertFalse(comprobacion(r, AnalizadorBateria.CLAVE_ADAPTADOR_AGUANTO).cumple)
    }

    @Test
    fun `un hueco de 1,5 s sin respuesta en el arranque también es un reinicio del adaptador`() {
        val r = resultado(Perfil(arranque = arranqueConHueco()))
        val a = r.detalle as AnalisisBateria

        assertTrue(a.arranque!!.adaptadorReiniciado)
        assertFalse(a.arranque!!.enlacePerdido)
        assertEquals(1.5, a.arranque!!.sinRespuestaMs / 1_000.0, 0.06)
        assertEquals(PatronBateria.ADAPTADOR_REINICIADO, a.patron)
    }

    @Test
    fun `15,3 V con el motor en marcha es sobrecarga`() {
        val r = resultado(Perfil(minimo = { 14.8 }, rpmAltas = { 15.3 }))
        val a = r.detalle as AnalisisBateria

        assertEquals(PatronBateria.SOBRECARGA, a.patron)
        assertEquals(Veredicto.FALLA, r.veredicto)
        assertFalse(comprobacion(r, ClavesBanda.SOBRECARGA_MAX_V).cumple)
        assertTrue(r.titulo, r.titulo.contains("15,3 V"))
        assertTrue(r.interpretacion, r.interpretacion.contains("regulador"))
    }

    @Test
    fun `sin carga a rpm altas es una falla de carga`() {
        val r = resultado(Perfil(minimo = { 12.3 }, rpmAltas = { 12.4 }))

        assertEquals(PatronBateria.NO_CARGA, (r.detalle as AnalisisBateria).patron)
        assertEquals(Veredicto.FALLA, r.veredicto)
        assertFalse(comprobacion(r, AnalizadorBateria.CLAVE_CARGA_EN_MINIMO).cumple)
    }

    @Test
    fun `el desfase calibrado con el multímetro se suma a cada lectura y se cita`() {
        val desfase = DesfaseVoltaje.medido(multimetroV = 12.4, adaptadorV = 12.2)
        val r = resultado(benelli.copy(contacto = { 12.0 }, contexto = ContextoAnalisis(desfaseVoltaje = desfase)))

        assertEquals(12.2, comprobacion(r, ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V).valor!!, 0.01)
        assertTrue(comprobacion(r, ClavesBanda.BATERIA_CONTACTO_CON_LUCES_V).cumple)
        assertTrue(r.interpretacion, r.interpretacion.contains("+0,20 V"))
        assertTrue(r.interpretacion, r.interpretacion.contains("Calibrado con el multímetro"))
    }

    @Test
    fun `sin calibrar se dice cuánto puede errar AT RV`() {
        val r = resultado(benelli)

        assertTrue(r.interpretacion, r.interpretacion.contains("AT RV"))
        assertTrue(r.interpretacion, r.interpretacion.contains("±0,1-0,2 V"))
    }

    @Test
    fun `una banda del modelo con fuente gana a la típica y se cita`() {
        val fuente = BandaReferencia(ClavesBanda.CARGA_V, 13.0, 14.0, "V", OrigenBanda.FUENTE, "Manual de servicio")
        val bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, listOf(fuente))
        val r = resultado(benelli, bandas)

        assertFalse(comprobacion(r, ClavesBanda.CARGA_V).cumple)
        assertEquals(PatronBateria.CARGA_ALTA, (r.detalle as AnalisisBateria).patron)
        assertTrue(r.interpretacion, r.interpretacion.contains("Fuente: Manual de servicio"))
    }

    @Test
    fun `si no se ve la caída del arranque lo dice sin inventar un valle`() {
        val r = resultado(Perfil(arranque = { 12.6 }))
        val a = r.detalle as AnalisisBateria

        assertFalse(a.arranque!!.visto)
        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("No se vio la caída del arranque") })
    }

    @Test
    fun `con menos de 5 lecturas por segundo el valle sale con baja confianza`() {
        val r = resultado(Perfil(hz = 3))

        assertTrue(r.bajaConfianza)
        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("puede ser más profundo") })
    }

    @Test
    fun `sin lecturas de voltaje no hay datos`() {
        val r = resultado(Perfil(contacto = { null }, arranque = { null }, minimo = { null }, rpmAltas = { null }))

        assertEquals(PatronBateria.SIN_DATOS, (r.detalle as AnalisisBateria).patron)
        assertNull((r.detalle as AnalisisBateria).contacto)
    }

    @Test
    fun `carga a rpm altas pero no en mínimo es una atención`() {
        val r = resultado(benelli.copy(minimo = { 12.1 }))

        assertEquals(PatronBateria.SIN_CARGA_EN_MINIMO, (r.detalle as AnalisisBateria).patron)
        assertEquals(Veredicto.ATENCION, r.veredicto)
    }

    @Test
    fun `el detalle guarda cada tramo, el desfase y el patrón`() {
        val json = JSONObject(AnalizadorBateria.analizar(datos(Perfil(arranque = arranque(valle = 8.9))), moto).detalle.json().toString())

        assertEquals("ARRANQUE_BAJO", json.getString("patron"))
        assertEquals(8.9, json.getJSONObject("arranque").getDouble("valleV"), 1e-9)
        assertEquals(12.6, json.getJSONObject("contacto").getDouble("mediaV"), 1e-9)
        assertEquals(0.0, json.getJSONObject("desfase").getDouble("voltios"), 1e-9)
        assertTrue(json.getBoolean("conFarola"))
    }
}
