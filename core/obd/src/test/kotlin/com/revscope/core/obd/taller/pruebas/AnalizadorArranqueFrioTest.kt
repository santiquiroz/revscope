package com.revscope.core.obd.taller.pruebas

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.Perfil
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.calentar
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.conSalto
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.datos
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.dosIntentos
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.minimoRapido
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.seApagaA
import com.revscope.core.obd.taller.pruebas.ArranqueSintetico.soloMotorDeArranque
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalizadorArranqueFrioTest {

    private val moto = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())

    private fun resultado(p: Perfil) = AnalizadorArranqueFrio.analizar(datos(p), moto)

    private fun analisis(p: Perfil) = resultado(p).detalle as AnalisisArranqueFrio

    @Test
    fun `motor 32 grados y aire 27 es un arranque tibio y así se etiqueta`() {
        val r = resultado(Perfil(ectContacto = 32.0, iatContacto = 27.0, ectCalentamiento = calentar(desde = 32.0)))
        val a = r.detalle as AnalisisArranqueFrio

        assertEquals(CondicionMotor.TIBIO, a.plausibilidad.condicion)
        assertEquals(5.0, a.plausibilidad.diferenciaC!!, 1e-9)
        assertTrue(r.titulo, r.titulo.startsWith("Arranque tibio"))
        assertTrue(r.interpretacion, r.interpretacion.contains("32 °C"))
        assertTrue(r.interpretacion, r.interpretacion.contains("27 °C"))
        assertTrue(r.interpretacion, r.interpretacion.contains("Típico (editable)"))
    }

    @Test
    fun `motor y aire a 1 grado de diferencia es un arranque en frío`() {
        val r = resultado(Perfil())

        assertEquals(CondicionMotor.FRIO, (r.detalle as AnalisisArranqueFrio).plausibilidad.condicion)
        assertTrue(r.titulo, r.titulo.startsWith("Arranque en frío"))
    }

    @Test
    fun `un arranque con 2 intentos los cuenta y mide el tiempo hasta el que prendió`() {
        val r = resultado(Perfil(rpmArranque = dosIntentos()))
        val arranque = (r.detalle as AnalisisArranqueFrio).arranque

        assertTrue(arranque.arranco)
        assertEquals(2, arranque.intentos)
        assertEquals(3.5, arranque.tiempoS!!, 0.01)
        assertEquals(PatronArranque.VARIOS_INTENTOS, (r.detalle as AnalisisArranqueFrio).patron)
        assertEquals(Veredicto.ATENCION, r.veredicto)
        assertTrue(r.titulo, r.titulo.contains("2.º intento"))
    }

    @Test
    fun `un salto de 8 grados de la ECT en medio segundo es un hallazgo`() {
        val r = resultado(Perfil(ectCalentamiento = conSalto(calentar(desde = 24.0))))
        val a = r.detalle as AnalisisArranqueFrio

        assertEquals(1, a.saltosEct.size)
        assertEquals(8.0, a.saltosEct.single().delta, 0.2)
        assertTrue(a.saltosEct.single().duracionMs <= 600)
        assertEquals(PatronArranque.SALTOS_ECT, a.patron)
        assertEquals(Veredicto.FALLA, r.veredicto)
        val hallazgo = r.hallazgos.single { it.contains("saltó") }
        assertTrue(hallazgo, hallazgo.contains("8 °C"))
        assertTrue(r.interpretacion, r.interpretacion.contains("P0119"))
        assertTrue(r.siguientePaso!!.contains("plantilla ECT"))
    }

    @Test
    fun `un arranque en frío sano está OK con el perfil del mínimo rápido bajando`() {
        val r = resultado(Perfil())
        val a = r.detalle as AnalisisArranqueFrio

        assertEquals(PatronArranque.NORMAL, a.patron)
        assertEquals(Veredicto.OK, r.veredicto)
        assertEquals(1, a.arranque.intentos)
        assertEquals(1.0, a.arranque.tiempoS!!, 0.01)
        assertEquals(0, a.apagones)
        assertTrue(a.saltosEct.isEmpty())
        assertTrue(a.perfil!!.baja!!)
        assertEquals(1_800.0, a.perfil!!.rpmInicio, 30.0)
        assertEquals(1_500.0, a.perfil!!.rpmFin, 30.0)
        // 24 → 62 °C en 120 s: 60 °C a los ≈113,7 s del calentamiento, 118,7 s después de prender.
        assertEquals(118.7, a.tiempoHasta60S!!, 0.2)
        assertTrue(r.hallazgos.isEmpty())
    }

    @Test
    fun `si solo gira el motor de arranque no arrancó y es una falla`() {
        val r = resultado(Perfil(rpmArranque = soloMotorDeArranque(), rpmCalentamiento = { 0.0 }, ectCalentamiento = { 24.0 }))
        val a = r.detalle as AnalisisArranqueFrio

        assertFalse(a.arranque.arranco)
        assertEquals(PatronArranque.NO_ARRANCO, a.patron)
        assertEquals(Veredicto.FALLA, r.veredicto)
        assertNull(a.arranque.tiempoS)
    }

    @Test
    fun `un apagón después de arrancar es una falla`() {
        val a = analisis(Perfil(rpmCalentamiento = seApagaA(30.0)))

        assertEquals(1, a.apagones)
        assertEquals(PatronArranque.SE_APAGA, a.patron)
    }

    @Test
    fun `si el mínimo rápido sube mientras calienta se marca para revisar`() {
        val r = resultado(Perfil(rpmCalentamiento = minimoRapido(desde = 1_400.0, hasta = 1_900.0)))
        val a = r.detalle as AnalisisArranqueFrio

        assertFalse(a.perfil!!.baja!!)
        assertEquals(PatronArranque.IRREGULAR, a.patron)
        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("mínimo rápido subió") })
    }

    @Test
    fun `el motor más frío que el aire tras el reposo es implausible`() {
        val r = resultado(Perfil(ectContacto = 15.0, iatContacto = 27.0, ectCalentamiento = calentar(desde = 15.0)))

        assertEquals(PatronArranque.IRREGULAR, (r.detalle as AnalisisArranqueFrio).patron)
        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("menos que el aire") })
    }

    @Test
    fun `el aire lejos del ambiente con el motor frío se señala`() {
        val r = resultado(Perfil(ambiente = 15.0))

        assertTrue(r.hallazgos.toString(), r.hallazgos.any { it.contains("ambiente") })
    }

    @Test
    fun `sin temperatura del aire no se puede decir si estaba frío`() {
        val a = analisis(Perfil(iatContacto = null))

        assertEquals(CondicionMotor.SIN_DATO, a.plausibilidad.condicion)
    }

    @Test
    fun `la interpretación lleva la nota de los motores refrigerados por aire`() {
        assertTrue(resultado(Perfil()).interpretacion.contains("refrigeradas por aire"))
    }

    @Test
    fun `el criterio del paso de arranque pide RPM sobre el umbral sostenidas 2 s`() {
        fun rpm(desdeS: Double, hastaS: Double, valor: Double) = (0 until ((hastaS - desdeS) * 10).toInt()).map { i ->
            MuestraCaptura(i.toLong(), ((desdeS + i / 10.0) * 1_000_000).toLong(), AnalizadorArranqueFrio.PID_RPM, valor, i.toLong(), 50)
        }

        assertTrue(AnalizadorArranqueFrio.arrancoSostenido(rpm(0.0, 2.2, 1_500.0)))
        assertFalse(AnalizadorArranqueFrio.arrancoSostenido(rpm(0.0, 1.5, 1_500.0)))
        assertFalse(AnalizadorArranqueFrio.arrancoSostenido(rpm(0.0, 4.0, 300.0)))
        assertFalse(AnalizadorArranqueFrio.arrancoSostenido(rpm(0.0, 3.0, 1_500.0) + rpm(3.0, 3.5, 0.0)))
    }

    @Test
    fun `las medidas dicen la condición, los intentos y el calentamiento`() {
        val a = analisis(Perfil(ectContacto = 32.0, iatContacto = 27.0, rpmArranque = dosIntentos(), ectCalentamiento = calentar(desde = 32.0)))
        val medidas = TextosArranqueFrio.medidas(a)

        assertTrue(medidas.toString(), "En contacto: motor 32 °C, aire 27 °C · tibio" in medidas)
        assertTrue(medidas.toString(), "Arranque: 2 intentos, 3,5 s hasta prender" in medidas)
        assertTrue(medidas.toString(), "Saltos de la temperatura del motor: ninguno" in medidas)
    }

    @Test
    fun `el detalle en JSON lleva la condición, los intentos y los saltos`() {
        val json = resultado(
            Perfil(ectContacto = 32.0, iatContacto = 27.0, rpmArranque = dosIntentos(), ectCalentamiento = calentar(desde = 32.0)),
        ).detalle.json()

        assertEquals("TIBIO", json.getJSONObject("plausibilidad").getString("condicion"))
        assertEquals(2, json.getJSONObject("arranque").getInt("intentos"))
        assertEquals(0, json.getJSONArray("saltosEct").length())
    }
}
