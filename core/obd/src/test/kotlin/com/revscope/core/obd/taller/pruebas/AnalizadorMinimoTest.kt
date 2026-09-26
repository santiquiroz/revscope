package com.revscope.core.obd.taller.pruebas

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.pruebas.MinimoSintetico.caida
import com.revscope.core.obd.taller.pruebas.MinimoSintetico.datos
import com.revscope.core.obd.taller.pruebas.MinimoSintetico.estable
import com.revscope.core.obd.taller.pruebas.MinimoSintetico.oscilante
import com.revscope.core.obd.taller.pruebas.MinimoSintetico.retornoQueSeApaga
import com.revscope.core.obd.taller.pruebas.MinimoSintetico.retornoSano
import com.revscope.core.obd.taller.pruebas.MinimoSintetico.sinAcelerar
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.Veredicto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalizadorMinimoTest {

    private val moto = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())

    private val sanos = List(3) { retornoSano() }

    // Caso Benelli TNT 150i (25-sep-2026): el mínimo cae de 2 010 a 1 454 rpm en 9 s con el TPS en 2,7-3,1 %
    // y se apaga 2 de las 3 veces que se suelta el acelerador.
    private val benelli = datos(
        minimo = caida(),
        retornos = listOf(retornoQueSeApaga(), retornoSano(minimo = 1_454.0), retornoQueSeApaga()),
        minimoMs = 9_000,
        tpsPct = 2.75,
        ectC = 32.0,
        descartarMinimoMs = 0,
    )

    private fun analisis(d: DatosPrueba, bandas: Map<String, BandaReferencia> = moto) =
        AnalizadorMinimo.analizar(d, bandas).detalle as AnalisisMinimo

    @Test
    fun `la caída de 2 010 a 1 454 rpm en 9 s da una deriva de -62 rpm por segundo y un mínimo inestable`() {
        val m = analisis(benelli).minimo!!

        assertEquals(-61.8, m.derivaRpmS, 0.1)
        assertEquals(2_010.0, m.inicioRpm, 0.5)
        assertEquals(1_454.0, m.finRpm, 0.5)
        assertEquals(9.0, m.duracionS, 0.01)
        assertTrue(m.inestable)
        assertTrue(analisis(benelli).comprobacion(ClavesBanda.MINIMO_DERIVA_MAX)!!.posicion == PosicionEnBanda.ALTO)
    }

    @Test
    fun `el caso Benelli se apaga 2 de 3 veces al soltar y es una falla`() {
        val resultado = AnalizadorMinimo.analizar(benelli, moto)
        val a = resultado.detalle as AnalisisMinimo

        assertEquals(PatronMinimo.SE_APAGA, a.patron)
        assertEquals(Veredicto.FALLA, resultado.veredicto)
        assertEquals(3, a.retornos.size)
        assertEquals(2, a.retornos.count { it.seApago })
        assertEquals(2, a.apagonesAlSoltar)
        assertEquals("Se apagó 2 de 3 veces al soltar el acelerador", resultado.titulo)
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("cayó de 2010 a 1454 rpm en 9 s (-62 rpm/s)"))
        assertTrue(resultado.hallazgos.toString(), resultado.hallazgos.any { it.contains("inestable") })
    }

    @Test
    fun `el TPS en ralentí por debajo de la banda de cerrado queda como hallazgo`() {
        val resultado = AnalizadorMinimo.analizar(benelli, moto)
        val tps = (resultado.detalle as AnalisisMinimo).tpsRalenti!!

        assertEquals(PosicionEnBanda.BAJO, tps.posicion)
        assertEquals(0.14, tps.minV, 0.005)
        assertEquals(0.16, tps.maxV, 0.005)
        val hallazgo = resultado.hallazgos.single { it.startsWith("TPS en ralentí") }
        assertTrue(hallazgo, hallazgo.contains("0,14-0,16 V"))
        assertTrue(hallazgo, hallazgo.contains("por debajo de la banda de cerrado"))
        assertTrue(hallazgo, hallazgo.contains("Típico (editable)"))
        assertTrue(hallazgo, hallazgo.contains("posición de ralentí"))
    }

    @Test
    fun `solo el TPS fuera de banda con un mínimo sano pide atención y sugiere el barrido`() {
        val resultado = AnalizadorMinimo.analizar(datos(estable(), sanos, tpsPct = 2.75), moto)

        assertEquals(PatronMinimo.TPS_RALENTI_FUERA, (resultado.detalle as AnalisisMinimo).patron)
        assertEquals(Veredicto.ATENCION, resultado.veredicto)
        assertTrue(resultado.siguientePaso!!.contains("barrido del TPS"))
    }

    @Test
    fun `un mínimo estable sintético con retornos sanos está OK`() {
        val resultado = AnalizadorMinimo.analizar(datos(estable(), sanos), moto)
        val a = resultado.detalle as AnalisisMinimo
        val m = a.minimo!!

        assertEquals(PatronMinimo.NORMAL, a.patron)
        assertEquals(Veredicto.OK, resultado.veredicto)
        assertEquals(1_400.0, m.mediaRpm, 2.0)
        assertTrue(m.desviacionRpm < 15.0)
        assertTrue(kotlin.math.abs(m.derivaRpmS) < 1.0)
        assertNull(m.oscilacion)
        assertFalse(m.inestable)
        assertEquals(0, a.apagonesAlSoltar)
        assertTrue(resultado.hallazgos.isEmpty())
        assertTrue(a.comprobaciones.all { it.cumple })
        assertNull(resultado.siguientePaso)
    }

    @Test
    fun `cada retorno mide el valle contra el mínimo y el tiempo hasta quedar en más o menos 10 %`() {
        val r = analisis(datos(estable(amplitud = 0.0), sanos)).retornos.first()

        assertTrue(r.acelerada)
        assertEquals(3_000.0, r.picoRpm, 1.0)
        assertEquals(1_200.0, r.valleRpm!!, 1.0)
        assertEquals(0.857, r.valleFraccion!!, 0.005)
        // Suelta a los 1,5 s; entra en ±10 % de 1 400 (≥ 1 260) a los 2,5 s y ya no sale.
        assertEquals(1.0, r.tiempoS!!, 0.11)
        assertFalse(r.seApago)
    }

    @Test
    fun `un valle bajo el 70 % del mínimo sin apagarse pide atención`() {
        val resultado = AnalizadorMinimo.analizar(datos(estable(), List(3) { retornoSano(valle = 800.0) }), moto)

        assertEquals(PatronMinimo.RETORNO_BAJO, (resultado.detalle as AnalisisMinimo).patron)
        assertEquals(Veredicto.ATENCION, resultado.veredicto)
    }

    @Test
    fun `una oscilación de 150 rpm cada 4 s es un mínimo inestable con su periodo`() {
        val a = analisis(datos(oscilante(), sanos))
        val osc = a.minimo!!.oscilacion!!

        assertEquals(PatronMinimo.INESTABLE, a.patron)
        assertEquals(4.0, osc.periodoS!!, 0.3)
        assertTrue(osc.amplitudRpm > 140.0)
    }

    @Test
    fun `un mínimo fuera de la banda del vehículo pide atención y cita la banda`() {
        val carro = ResolutorBandas.resolverTodas(VehicleType.CAR, emptyList())
        val resultado = AnalizadorMinimo.analizar(datos(estable(), sanos), carro)

        assertEquals(PatronMinimo.FUERA_DE_BANDA, (resultado.detalle as AnalisisMinimo).patron)
        assertTrue(resultado.interpretacion, resultado.interpretacion.contains("600–900 rpm"))
    }

    @Test
    fun `la banda del usuario gana a la típica`() {
        val propia = BandaReferencia(ClavesBanda.MINIMO_RPM, 1_300.0, 1_500.0, "rpm", OrigenBanda.USUARIO)
        val bandas = ResolutorBandas.resolverTodas(VehicleType.CAR, listOf(propia))

        val resultado = AnalizadorMinimo.analizar(datos(estable(), sanos), bandas)

        assertEquals(Veredicto.OK, resultado.veredicto)
    }

    @Test
    fun `un retorno sin acelerada no cuenta y se avisa`() {
        val resultado = AnalizadorMinimo.analizar(datos(estable(), listOf(retornoSano(), sinAcelerar(), retornoSano())), moto)
        val a = resultado.detalle as AnalisisMinimo

        assertFalse(a.retornos[1].acelerada)
        assertTrue(resultado.hallazgos.toString(), resultado.hallazgos.any { it.contains("Retorno 2") })
    }

    @Test
    fun `sin RPM en el mínimo no hay veredicto y se pide repetir`() {
        val sinRpm = DatosPrueba(TipoPrueba.MINIMO_RETORNO, emptyList(), listOf(SegmentoPaso(AnalizadorMinimo.Pasos.MINIMO, 0, 45_000)))

        val resultado = AnalizadorMinimo.analizar(sinRpm, moto)

        assertEquals(Veredicto.ATENCION, resultado.veredicto)
        assertEquals(PatronMinimo.SIN_DATOS, (resultado.detalle as AnalisisMinimo).patron)
    }

    @Test
    fun `las medidas del caso Benelli dan la deriva y cada retorno en una línea`() {
        val medidas = TextosMinimo.medidas(analisis(benelli))

        assertTrue(medidas.toString(), "Deriva: -61,8 rpm/s (de 2010 a 1454 rpm)" in medidas)
        assertTrue(medidas.toString(), "TPS en ralentí: 0,14-0,16 V (media 0,15 V)" in medidas)
        assertTrue(medidas.toString(), "Retorno 1: pico 3000 rpm y se apagó al soltar" in medidas)
        assertTrue(medidas.toString(), medidas.any { it.startsWith("Retorno 2: pico 3000 rpm, valle 1200 rpm") })
    }

    @Test
    fun `el detalle en JSON lleva el patrón, la deriva y cada retorno`() {
        val json = AnalizadorMinimo.analizar(benelli, moto).detalle.json()

        assertEquals("SE_APAGA", json.getString("patron"))
        assertEquals(-61.8, json.getJSONObject("minimo").getDouble("derivaRpmS"), 0.1)
        assertEquals(3, json.getJSONArray("retornos").length())
        assertTrue(json.getJSONArray("retornos").getJSONObject(0).getBoolean("seApago"))
    }
}
