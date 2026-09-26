package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.telemetry.captura.LimitesCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoPruebasTest {

    private val ahora = 1_758_848_000_000L

    private fun ctx(vararg lecturas: Pair<String, Double>, soportados: Set<String> = setOf("05", "0C", "0F", "11")) = ContextoPrueba(
        conectado = true,
        lecturas = lecturas.associate { (pid, valor) -> pid to ObdReading(pid, valor, "", ahora) },
        soportado = { it in soportados },
        ahoraMs = ahora,
    )

    private fun muestra(tMs: Long, pid: String, valor: Double) = MuestraCaptura(tMs, tMs * 1_000, pid, valor, tMs, 50)

    @Test
    fun `el catálogo ofrece las cinco pruebas guiadas`() {
        assertEquals(TipoPrueba.entries.toList(), CatalogoPruebas.disponibles)
        TipoPrueba.entries.forEach { assertEquals(it, CatalogoPruebas.definicion(it)!!.tipo) }
    }

    @Test
    fun `la batería lee AT RV en ráfaga con contacto, arranque, mínimo y rpm altas, y el corte en el arranque se analiza`() {
        val d = CatalogoPruebas.bateriaCarga

        assertEquals(FuenteMuestras.VOLTAJE_ADAPTADOR, d.fuente)
        assertEquals(listOf("VBAT"), d.pids)
        assertEquals(listOf("CONTACTO", "ARRANQUE", "MINIMO", "RPM_ALTAS"), d.pasos.map { it.clave })
        assertEquals(
            listOf(ModoPaso.Grabar(10_000), ModoPaso.Grabar(8_000), ModoPaso.Grabar(15_000), ModoPaso.Sostener(10_000)),
            d.pasos.map { it.modo },
        )
        assertEquals(0L, d.pasos[1].descartarInicioMs)
        assertEquals(setOf("ARRANQUE"), d.analizarSiSeCortaEn)
        assertTrue(d.pasos.all { d.pidPrincipal(it) == "VBAT" })
        assertFalse(d.usaAmbiente)
    }

    @Test
    fun `la batería pide el motor apagado y avisa de la farola con el contacto`() {
        val r = CatalogoPruebas.bateriaCarga.precondiciones.map { it.evaluar(ctx("0C" to 0.0)) }

        assertTrue(r.all { it.cumple })
        val farola = r.single { it.texto.contains("farola") }
        assertTrue(farola.aviso)
        assertTrue(farola.texto, farola.texto.contains("AHO"))
        val encendido = CatalogoPruebas.bateriaCarga.precondiciones.map { it.evaluar(ctx("0C" to 1_400.0)) }.filterNot { it.cumple }
        assertTrue(encendido.single().queHacer!!.contains("antes de arrancar"))
    }

    @Test
    fun `el MAP contra la barométrica graba 10 s con el contacto, pide el ambiente y lee el PID 33 si lo hay`() {
        val d = CatalogoPruebas.mapBaro

        assertEquals(FuenteMuestras.PIDS, d.fuente)
        assertEquals(listOf("0B"), d.pids)
        assertEquals(listOf("33"), d.pidsOpcionales)
        assertEquals(listOf(ModoPaso.Grabar(10_000)), d.pasos.map { it.modo })
        assertTrue(d.usaAmbiente)
    }

    @Test
    fun `sin PID 33 el MAP avisa que la referencia saldrá del teléfono o de la altitud estimada`() {
        val sinBaro = CatalogoPruebas.mapBaro.precondiciones.map { it.evaluar(ctx("0C" to 0.0, soportados = setOf("0B"))) }
        val conBaro = CatalogoPruebas.mapBaro.precondiciones.map { it.evaluar(ctx("0C" to 0.0, soportados = setOf("0B", "33"))) }
        val sinMap = CatalogoPruebas.mapBaro.precondiciones.map { it.evaluar(ctx("0C" to 0.0, soportados = emptySet())) }

        assertTrue(sinBaro.all { it.cumple })
        val aviso = sinBaro.single { it.texto.contains("barométrica") }
        assertTrue(aviso.aviso)
        assertTrue(aviso.queHacer!!.plano(), aviso.queHacer!!.plano().contains("estimada, ±3 kPa"))
        assertFalse(conBaro.single { it.texto.contains("barométrica") }.aviso)
        assertEquals(listOf("MAP no disponible en esta ECU (PID 0B)"), sinMap.filterNot { it.cumple }.map { it.texto })
    }

    @Test
    fun `el mínimo graba 45 s sin tocar el acelerador y luego 3 retornos de 8 s`() {
        val d = CatalogoPruebas.minimoRetorno

        assertEquals(listOf("0C", "11", "05"), d.pids)
        assertEquals(listOf("MINIMO", "RETORNO_1", "RETORNO_2", "RETORNO_3"), d.pasos.map { it.clave })
        assertEquals(ModoPaso.Grabar(45_000), d.pasos.first().modo)
        assertTrue(d.pasos.drop(1).all { it.modo == ModoPaso.Grabar(8_000) && it.descartarInicioMs == 0L })
        assertTrue(d.pasos.drop(1).all { it.instruccion.contains("3 000 rpm") })
        assertEquals("0C", d.pidPrincipal(d.pasos.first()))
    }

    @Test
    fun `el arranque en frío termina el arranque al prender y el calentamiento a los 60 grados`() {
        val d = CatalogoPruebas.arranqueFrio
        val (contacto, arranque, calentamiento) = d.pasos

        assertEquals(ModoPaso.Grabar(10_000), contacto.modo)
        assertEquals(ModoPaso.Accion(15_000), arranque.modo)
        assertEquals(ModoPaso.GrabarHasta(600_000), calentamiento.modo)
        assertEquals(listOf("05", "0C", "05"), d.pasos.map { d.pidPrincipal(it) })
        assertEquals(listOf("11", "46"), d.pidsOpcionales)
        assertTrue(d.duracionMaximaMs <= LimitesCaptura.MAX_DURACION_MS)

        val prendio = (0L..2_100L step 100).map { muestra(it, "0C", 1_500.0) }
        assertTrue(checkNotNull(arranque.terminarCuando).cumplido(prendio))
        assertFalse(checkNotNull(calentamiento.terminarCuando).cumplido(listOf(muestra(0, "05", 59.0))))
        assertTrue(checkNotNull(calentamiento.terminarCuando).cumplido(listOf(muestra(0, "05", 59.0), muestra(100, "05", 60.0))))
    }

    @Test
    fun `el mínimo pide el motor encendido y la caja en neutro confirmada por el técnico`() {
        val apagado = CatalogoPruebas.minimoRetorno.precondiciones.map { it.evaluar(ctx("0C" to 0.0)) }
        val encendido = CatalogoPruebas.minimoRetorno.precondiciones.map { it.evaluar(ctx("0C" to 1_454.0)) }

        assertEquals(listOf("Motor apagado o a punto de apagarse (0 rpm)"), apagado.filterNot { it.cumple }.map { it.texto })
        assertTrue(encendido.all { it.cumple })
        val neutro = encendido.single { it.texto.contains("neutro") }
        assertTrue(neutro.aviso)
        assertNotNull(neutro.queHacer)
    }

    @Test
    fun `sin lectura de RPM el mínimo no arranca`() {
        val r = Precondicion.MotorEncendido().evaluar(ctx())

        assertFalse(r.cumple)
        assertEquals("Enciende el motor y déjalo en mínimo sin tocar el acelerador", r.queHacer)
    }

    @Test
    fun `el motor tibio no impide el arranque en frío pero avisa que saldrá como tibio`() {
        val frio = Precondicion.MotorFrio.evaluar(ctx("05" to 24.0, "0F" to 23.0))
        val tibio = Precondicion.MotorFrio.evaluar(ctx("05" to 32.0, "0F" to 27.0))
        val sinDatos = Precondicion.MotorFrio.evaluar(ctx())

        assertEquals("Motor frío (motor 24 °C, aire 23 °C)", frio.texto.plano())
        assertFalse(frio.aviso)
        assertTrue(tibio.cumple)
        assertTrue(tibio.aviso)
        assertTrue(tibio.texto, tibio.texto.contains("arranque tibio"))
        assertTrue(sinDatos.cumple && sinDatos.aviso)
    }

    @Test
    fun `el arranque en frío pide el motor apagado con su propio motivo`() {
        val r = CatalogoPruebas.arranqueFrio.precondiciones.map { it.evaluar(ctx("0C" to 1_200.0)) }.filterNot { it.cumple }

        assertEquals(listOf("Motor encendido (1200 rpm)"), r.map { it.texto })
        assertTrue(r.single().queHacer!!.contains("antes de arrancar"))
    }
}
