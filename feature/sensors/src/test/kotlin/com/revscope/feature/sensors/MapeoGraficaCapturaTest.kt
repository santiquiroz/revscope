package com.revscope.feature.sensors

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.designsystem.EstiloLinea
import com.revscope.core.obd.taller.grafica.EstadoVref
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapeoGraficaCapturaTest {

    private val tipica = EstadoVref(ReferenciaVoltaje.TIPICA, null, null)

    private fun entrada(
        unidad: UnidadPosicion = UnidadPosicion.PORCENTAJE,
        ventanaMs: Long = 10_000,
        pausada: Boolean = false,
        series: Map<String, List<Pair<Long, Double>>> = CapturaBenelli.series(),
    ) = EntradaGraficaCaptura(
        series = series,
        nombre = CapturaBenelli::nombre,
        unidadPid = CapturaBenelli::unidad,
        hz = mapOf("11" to 10.4, "0C" to 9.8),
        unidad = unidad,
        referencia = tipica,
        ventanaMs = ventanaMs,
        pausada = pausada,
        bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList()),
    )

    private fun posicion(ui: GraficaCapturaUi) = ui.paneles.first().modelo

    @Test
    fun `en porcentaje el tps va en su valor de la ECU con los voltios al lado`() {
        val ui = MapeoGraficaCaptura.de(entrada())

        assertTrue(ui.hayPosicion)
        val tps = ui.valores.first { it.pid == "11" }
        assertEquals("9,4 %", tps.valor)
        assertEquals("0,47 V", tps.secundario)
        assertEquals("10,4 Hz", tps.hz)
        assertEquals(9.41, posicion(ui).series.single().puntos.last().y, 0.01)
    }

    @Test
    fun `en voltios convierte solo los pids de posicion y deja las rpm en su unidad`() {
        val ui = MapeoGraficaCaptura.de(entrada(unidad = UnidadPosicion.VOLTIOS))

        assertEquals("0,47 V", ui.valores.first { it.pid == "11" }.valor)
        assertEquals("9,4 %", ui.valores.first { it.pid == "11" }.secundario)
        assertEquals("2010 rpm", ui.valores.first { it.pid == "0C" }.valor)
        assertNull(ui.valores.first { it.pid == "0C" }.secundario)
        assertEquals(0.47, posicion(ui).series.single().puntos.last().y, 0.005)
        assertEquals(2_010.0, ui.paneles[1].modelo.series.single().puntos.last().y, 1e-9)
    }

    @Test
    fun `las rpm van en su propio panel para no aplastar la posicion`() {
        val ui = MapeoGraficaCaptura.de(entrada())

        assertEquals(listOf("Posición, en %", "RPM"), ui.paneles.map { it.titulo })
        assertNull(ui.paneles[1].modelo.rangoY)
        assertTrue(ui.paneles[1].modelo.bandas.isEmpty())
    }

    @Test
    fun `las bandas del tps se sombrean en la unidad elegida con su origen en la leyenda`() {
        val enV = MapeoGraficaCaptura.de(entrada(unidad = UnidadPosicion.VOLTIOS))
        val enPct = MapeoGraficaCaptura.de(entrada())

        assertEquals(0.3, posicion(enV).bandas.first().desde, 1e-9)
        assertEquals(6.0, posicion(enPct).bandas.first().desde, 1e-9)
        assertTrue(enV.paneles.first().leyenda.any { it.startsWith("Banda Cerrado: 0,3–1,0") && it.endsWith("Típico (editable)") })
        assertEquals(0.0..5.0, posicion(enV).rangoY)
        assertEquals(0.0..100.0, posicion(enPct).rangoY)
    }

    @Test
    fun `varias posiciones comparten panel con trazos distintos y la leyenda los nombra`() {
        val pedal = CapturaBenelli.tps().map { (t, v) -> t to v * 2 }
        val ui = MapeoGraficaCaptura.de(entrada(series = mapOf("11" to CapturaBenelli.tps(), "49" to pedal)))

        val panel = ui.paneles.single()
        assertEquals(listOf(EstiloLinea.CONTINUA, EstiloLinea.DISCONTINUA), panel.modelo.series.map { it.estilo })
        assertEquals(listOf("11", "49"), panel.modelo.series.map { it.etiqueta })
        assertEquals("11 Posición de la mariposa: trazo continuo", panel.leyenda[0])
        assertEquals("49 Posición del pedal D: trazo discontinuo", panel.leyenda[1])
    }

    @Test
    fun `la ventana de 10 o 30 s recorta la serie y el eje`() {
        val diez = posicion(MapeoGraficaCaptura.de(entrada(ventanaMs = 10_000)))
        val treinta = posicion(MapeoGraficaCaptura.de(entrada(ventanaMs = 30_000)))

        assertEquals(-10.0..0.0, diez.rangoX)
        assertEquals(-30.0..0.0, treinta.rangoX)
        assertTrue(diez.series.single().puntos.all { it.x >= -10.0 })
        assertTrue(treinta.series.single().puntos.size > diez.series.single().puntos.size)
    }

    @Test
    fun `el resumen accesible dice valor, voltios y tendencia, y si la grafica esta en pausa`() {
        val ui = MapeoGraficaCaptura.de(entrada(pausada = true))

        val tps = ui.paneles[0].modelo.descripcion
        assertTrue(tps, tps.startsWith("Gráfica en pausa, la captura sigue. Últimos 10 s."))
        assertTrue(tps, tps.contains("11 Posición de la mariposa: 9,4 % (0,47 V), estable"))
        assertTrue(ui.paneles[1].modelo.descripcion.contains("0C RPM: 2010 rpm, subiendo"))
    }

    @Test
    fun `sin pids de posicion no hay conmutador ni bandas`() {
        val ui = MapeoGraficaCaptura.de(entrada(series = mapOf("0C" to CapturaBenelli.rpm())))

        assertFalse(ui.hayPosicion)
        assertTrue(ui.paneles.single().modelo.bandas.isEmpty())
    }

    @Test
    fun `sin muestras no hay datos que dibujar`() {
        val ui = MapeoGraficaCaptura.de(entrada(series = emptyMap()))

        assertFalse(ui.tieneDatos)
        assertTrue(ui.valores.isEmpty())
    }
}
