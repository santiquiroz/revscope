package com.revscope.core.obd.telemetry.captura

import org.junit.Assert.assertEquals
import org.junit.Test

class VentanaCapturaTest {

    private fun pagina(desde: Long, vararg tMs: Long) = PaginaCaptura(
        muestras = tMs.mapIndexed { i, t -> MuestraCaptura(desde + i, t * 1_000, "49", t.toDouble(), desde + i, 20) },
        seqSiguiente = desde + tMs.size,
        perdidas = 0,
    )

    @Test
    fun `guarda los ultimos 10 s y avanza el cursor`() {
        val ventana = VentanaCaptura()

        ventana.agregar(pagina(0, 0, 5_000, 9_000))
        ventana.agregar(pagina(3, 12_000, 15_500))

        assertEquals(listOf(9_000L, 12_000L, 15_500L), ventana.series().getValue("49").map { it.first })
        assertEquals(5L, ventana.cursor)
    }

    @Test
    fun `reiniciar vacia la ventana y el cursor`() {
        val ventana = VentanaCaptura().apply { agregar(pagina(0, 1, 2)) }

        ventana.reiniciar()

        assertEquals(emptyMap<String, List<Pair<Long, Double>>>(), ventana.series())
        assertEquals(0L, ventana.cursor)
    }
}

class SeleccionPidsTest {

    @Test
    fun `alternar agrega quita y respeta el maximo de 6`() {
        val seis = listOf("0C", "0D", "11", "49", "4A", "5A")

        assertEquals(listOf("49", "4A"), SeleccionPids.alternar(listOf("49"), "4A"))
        assertEquals(listOf("49"), SeleccionPids.alternar(listOf("49", "4A"), "4A"))
        assertEquals(seis, SeleccionPids.alternar(seis, "45"))
    }

    @Test
    fun `csv vacio o invalido vuelve a pedal y mariposa`() {
        assertEquals(listOf("49", "4A", "11"), SeleccionPids.desdeCsv(null))
        assertEquals(listOf("49", "4A", "11"), SeleccionPids.desdeCsv(" , "))
        assertEquals(listOf("0C", "11"), SeleccionPids.desdeCsv("0c, 11,0C"))
        assertEquals("0C,11", SeleccionPids.aCsv(listOf("0C", "11")))
    }
}
