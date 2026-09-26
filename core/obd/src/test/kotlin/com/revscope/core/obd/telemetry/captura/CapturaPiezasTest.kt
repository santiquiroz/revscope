package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastCaptureBufferTest {

    private fun lleno(n: Int, capacidad: Int = 10) = FastCaptureBuffer(listOf("49", "4A"), capacidad).apply {
        repeat(n) { i -> agregar(i * 1_000L, if (i % 2 == 0) "49" else "4A", i.toDouble(), lote = i / 2L, latenciaMs = 20) }
    }

    @Test
    fun `seq es monotonico y el cursor pagina sin duplicar ni perder`() {
        val buffer = lleno(8)

        val p1 = buffer.leerDesde(0, max = 5)
        val p2 = buffer.leerDesde(p1.seqSiguiente, max = 5)

        assertEquals((0L..4L).toList(), p1.muestras.map { it.seq })
        assertEquals((5L..7L).toList(), p2.muestras.map { it.seq })
        assertEquals(8L, p2.seqSiguiente)
        assertEquals(0L, p1.perdidas + p2.perdidas)
    }

    @Test
    fun `anillo lleno sobrescribe lo mas viejo y un cursor fuera informa perdidas`() {
        val buffer = lleno(15)

        val pagina = buffer.leerDesde(2, max = 100)

        assertEquals(3L, pagina.perdidas)
        assertEquals(5L, pagina.muestras.first().seq)
        assertEquals(14.0, pagina.muestras.last().valor, 0.0)
        assertEquals(15L, pagina.seqSiguiente)
    }

    @Test
    fun `filtro por pid`() {
        val pagina = lleno(6).leerDesde(0, max = 100, filtro = setOf("4A"))

        assertEquals(listOf(1.0, 3.0, 5.0), pagina.muestras.map { it.valor })
        assertEquals(6L, pagina.seqSiguiente)
    }

    @Test
    fun `pids ajenos a la captura se ignoran`() {
        val buffer = lleno(0).apply { agregar(0, "0C", 900.0, 0, 20) }

        assertEquals(0L, buffer.total)
    }
}

class RateMeterTest {

    @Test
    fun `hz por pid y peticiones por segundo en la ventana`() {
        val meter = RateMeter(esCan = true)
        (0 until 60).forEach { i ->
            val t = i * 50L
            meter.registrarPeticion(t, 20.0, null)
            meter.registrarMuestra("49", t)
        }

        val stats = meter.estadisticas(3_000)

        assertEquals(20.0, stats.hzPorPid.getValue("49"), 0.5)
        assertEquals(20.0, stats.peticionesPorS, 0.5)
    }

    @Test
    fun `p50 y p95 de latencia`() {
        val meter = RateMeter(esCan = true)
        (1..100).forEach { meter.registrarPeticion(it.toLong(), it.toDouble(), null) }

        val stats = meter.estadisticas(100)

        assertEquals(51.0, stats.latenciaP50Ms!!, 1.0)
        assertEquals(95.0, stats.latenciaP95Ms!!, 1.0)
    }

    @Test
    fun `ratio de errores acumulado y limitado por`() {
        val meter = RateMeter(esCan = true)
        repeat(7) { meter.registrarPeticion(it * 10L, 30.0, null) }
        repeat(3) { meter.registrarPeticion(100L + it, 30.0, FallaLote.BUFFER_FULL) }

        val stats = meter.estadisticas(200)

        assertEquals(0.3, stats.ratioErrores, 0.001)
        assertEquals(10, stats.peticionesTotales)
        assertEquals("adaptador saturado", stats.limitadoPor)
    }

    @Test
    fun `k-line y latencia alta explican el limite`() {
        val kLine = RateMeter(esCan = false).apply { registrarPeticion(0, 30.0, null) }
        val lento = RateMeter(esCan = true).apply { registrarPeticion(0, 120.0, null) }
        val rapido = RateMeter(esCan = true).apply { registrarPeticion(0, 30.0, null) }

        assertEquals("protocolo lento", kLine.estadisticas(10).limitadoPor)
        assertEquals("adaptador/ECU", lento.estadisticas(10).limitadoPor)
        assertNull(rapido.estadisticas(10).limitadoPor)
    }
}

class CaptureSafeguardsTest {

    private fun estado(
        bateria: Int? = 80,
        cargando: Boolean = false,
        termico: Int? = 0,
        transcurrido: Long = 10_000,
        ratio: Double = 0.0,
        peticiones: Int = 100,
    ) = EstadoDispositivo(bateria, cargando, termico, transcurrido, ratio, peticiones)

    private fun decidir(e: EstadoDispositivo) = CaptureSafeguards.decidir(e, maxDuracionMs = 300_000)

    @Test
    fun `sin problemas continua`() {
        assertEquals(DecisionSalvaguarda.Continuar, decidir(estado()))
    }

    @Test
    fun `el tiempo maximo manda sobre todo`() {
        assertEquals(DecisionSalvaguarda.Detener("tiempo máximo"), decidir(estado(transcurrido = 300_000, bateria = 5)))
    }

    @Test
    fun `bateria bajo 15 sin cargar detiene y cargando no`() {
        assertTrue(decidir(estado(bateria = 14)) is DecisionSalvaguarda.Detener)
        assertEquals(DecisionSalvaguarda.Continuar, decidir(estado(bateria = 14, cargando = true)))
    }

    @Test
    fun `termico severo detiene y moderado limita a 10 hz`() {
        assertTrue(decidir(estado(termico = 3)) is DecisionSalvaguarda.Detener)
        assertEquals(DecisionSalvaguarda.Limitar(10), decidir(estado(termico = 2)))
    }

    @Test
    fun `bateria bajo 30 sin cargar limita`() {
        assertEquals(DecisionSalvaguarda.Limitar(10), decidir(estado(bateria = 25)))
    }

    @Test
    fun `adaptador con muchos errores detiene solo con muestra suficiente`() {
        assertTrue(decidir(estado(ratio = 0.5, peticiones = 60)) is DecisionSalvaguarda.Detener)
        assertEquals(DecisionSalvaguarda.Continuar, decidir(estado(ratio = 0.5, peticiones = 20)))
    }

    @Test
    fun `detener gana sobre limitar`() {
        assertTrue(decidir(estado(termico = 2, ratio = 0.5, peticiones = 60)) is DecisionSalvaguarda.Detener)
    }

    @Test
    fun `sin datos de bateria ni termico continua`() {
        assertEquals(DecisionSalvaguarda.Continuar, decidir(estado(bateria = null, termico = null)))
    }
}

class CapturaCsvTest {

    private val registry = PidRegistry(TestPids.load())
    private val info = InfoAdaptador("Android-Vlink", "ELM327 v2.2", "6", esCan = true)
    private val inicio = 1_790_000_102_345L

    @Test
    fun `cabecera y linea de metadatos exactas`() {
        val meta = CapturaCsv.metadatos(info, listOf("49", "4A", "11"), inicio, setOf(TecnicaCaptura.MULTI_PID, TecnicaCaptura.SUFIJO_1))

        val lineas = CapturaCsv.lineasIniciales(meta)

        assertTrue(lineas[0].startsWith("# revscope-captura v1; adaptador=Android-Vlink; elm=ELM327 v2.2; protocolo=6; pids=49,4A,11; inicio="))
        assertTrue(lineas[0].endsWith("; tecnicas=multipid,sufijo1"))
        assertEquals("epoch_ms,t_ms,pid,nombre,valor,unidad,lote,latencia_ms", lineas[1])
    }

    @Test
    fun `fila larga con epoch y t en ms enteros y punto decimal`() {
        val muestra = MuestraCaptura(seq = 0, tMicros = 1_500_700, pid = "49", valor = 14.901960784, lote = 3, latenciaMs = 31)

        val linea = CapturaCsv.lineaLarga(muestra, inicio, registry.getDefinition("49"))

        assertEquals("1790000103845,1500,49,Pedal acelerador D,14.902,%,3,31", linea)
    }

    @Test
    fun `formato ancho una fila por lote con celdas vacias`() {
        val muestras = listOf(
            MuestraCaptura(0, 10_000, "49", 14.9, 1, 20),
            MuestraCaptura(1, 10_000, "4A", 7.5, 1, 20),
            MuestraCaptura(2, 40_000, "49", 15.0, 2, 20),
        )

        val filas = CapturaCsv.filasAnchas(muestras, listOf("49", "4A", "11"), inicio)

        assertEquals(listOf("epoch_ms", "t_ms", "49", "4A", "11"), CapturaCsv.cabeceraAncha(listOf("49", "4A", "11")))
        assertEquals(listOf<Any?>(inicio + 10, 10L, 14.9, 7.5, null), filas[0])
        assertEquals(listOf<Any?>(inicio + 40, 40L, 15.0, null, null), filas[1])
    }
}
