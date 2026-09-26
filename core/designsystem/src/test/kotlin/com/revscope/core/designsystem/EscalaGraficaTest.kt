package com.revscope.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalaGraficaTest {

    @Test
    fun `las marcas caen en pasos redondos dentro del rango`() {
        assertEquals(listOf(0.0, 0.5, 1.0), EscalaGrafica.marcas(-0.05..1.05))
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0), EscalaGrafica.marcas(0.0..20.0))
        assertEquals(listOf(1_600.0, 1_800.0, 2_000.0), EscalaGrafica.marcas(1_454.0..2_010.0))
    }

    @Test
    fun `el texto del eje usa coma y los decimales del paso`() {
        assertEquals("0,5", EscalaGrafica.texto(0.5, 0.5))
        assertEquals("0,25", EscalaGrafica.texto(0.25, 0.05))
        assertEquals("20", EscalaGrafica.texto(20.0, 5.0))
    }

    @Test
    fun `el rango vertical incluye las bandas aunque la serie quede por debajo`() {
        val m = ModeloGrafica(
            series = listOf(SerieGrafica("11", listOf(PuntoGrafica(0.0, 0.12), PuntoGrafica(1.0, 0.89)))),
            descripcion = "",
            bandas = listOf(BandaGrafica("Fondo", 3.8, 4.8)),
        )

        val rango = EscalaGrafica.rangoY(m)

        assertTrue(rango.start < 0.12)
        assertTrue(rango.endInclusive > 4.8)
    }

    @Test
    fun `un rango fijo manda y una serie plana no deja el eje sin alto`() {
        val plana = ModeloGrafica(listOf(SerieGrafica("11", listOf(PuntoGrafica(0.0, 2.35), PuntoGrafica(5.0, 2.35)))), "")

        assertEquals(0.0..100.0, EscalaGrafica.rangoY(plana.copy(rangoY = 0.0..100.0)))
        assertTrue(EscalaGrafica.rangoY(plana).let { it.endInclusive - it.start } > 0.0)
        assertEquals(0.0..5.0, EscalaGrafica.rangoX(plana))
    }

    @Test
    fun `los estilos de linea se reparten en orden`() {
        assertEquals(
            listOf(EstiloLinea.CONTINUA, EstiloLinea.DISCONTINUA, EstiloLinea.PUNTEADA, EstiloLinea.CONTINUA),
            (0..3).map(::estiloDeSerie),
        )
    }
}
