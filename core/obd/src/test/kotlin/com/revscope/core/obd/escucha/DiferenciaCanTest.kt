package com.revscope.core.obd.escucha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiferenciaCanTest {

    @Test
    fun `contador que varia en la base se ignora a nivel de byte`() {
        val base = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x00, 0x10)),
            TramaCan(10L, 0x7E8, false, listOf(0x01, 0x10)),
            TramaCan(20L, 0x7E8, false, listOf(0x02, 0x10)),
        )
        val accion = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x03, 0x10)),
            TramaCan(10L, 0x7E8, false, listOf(0x04, 0x10)),
        )

        val diferencia = DiferenciaCan.calcular(base, accion)

        assertTrue("byte contador no debe aparecer: ${diferencia.bytes}", diferencia.bytes.isEmpty())
    }

    @Test
    fun `byte fijo que cambia aparece con sus valores nuevos`() {
        val base = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x10)),
            TramaCan(10L, 0x7E8, false, listOf(0x10)),
        )
        val accion = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x20)),
            TramaCan(10L, 0x7E8, false, listOf(0x30)),
        )

        val diferencia = DiferenciaCan.calcular(base, accion)

        assertEquals(listOf(ByteCambiado("7E8", 0, 0x10, listOf(0x20, 0x30))), diferencia.bytes)
        assertTrue(diferencia.bits.isEmpty())
    }

    @Test
    fun `indicador empaquetado junto a un contador aparece en bits`() {
        val base = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x00)),
            TramaCan(10L, 0x7E8, false, listOf(0x01)),
            TramaCan(20L, 0x7E8, false, listOf(0x02)),
        )
        val accion = listOf(TramaCan(0L, 0x7E8, false, listOf(0x83)))

        val diferencia = DiferenciaCan.calcular(base, accion)

        assertTrue(diferencia.bytes.isEmpty())
        assertEquals(listOf(BitCambiado("7E8", 0, 7, 0)), diferencia.bits)
    }

    @Test
    fun `id nuevo e id ausente se listan ordenados`() {
        val base = listOf(
            TramaCan(0L, 0x100, false, listOf(0x01)),
            TramaCan(0L, 0x7E8, false, listOf(0x01)),
        )
        val accion = listOf(
            TramaCan(0L, 0x200, false, listOf(0x01)),
            TramaCan(0L, 0x7E8, false, listOf(0x01)),
        )

        val diferencia = DiferenciaCan.calcular(base, accion)

        assertEquals(listOf("200"), diferencia.idsNuevos)
        assertEquals(listOf("100"), diferencia.idsAusentes)
    }
}
