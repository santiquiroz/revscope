package com.revscope.core.obd.taller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReductorSerieTest {

    @Test
    fun `una serie corta queda igual`() {
        val serie = List(10) { it.toDouble() }

        assertSame(serie, ReductorSerie.reducir(serie) { it })
    }

    @Test
    fun `una serie larga queda en el máximo de puntos y en orden de tiempo`() {
        val serie = List(5_000) { it to (it % 97).toDouble() }

        val reducida = ReductorSerie.reducir(serie, maximo = 400) { it.second }

        assertTrue(reducida.size <= 400)
        assertEquals(reducida.sortedBy { it.first }, reducida)
    }

    @Test
    fun `una caída de una sola muestra sobrevive a la reducción`() {
        val serie = List(4_000) { i -> i to if (i == 2_345) 0.1 else 2.4 }

        val reducida = ReductorSerie.reducir(serie, maximo = 400) { it.second }

        assertTrue(reducida.any { it.first == 2_345 && it.second == 0.1 })
    }
}
