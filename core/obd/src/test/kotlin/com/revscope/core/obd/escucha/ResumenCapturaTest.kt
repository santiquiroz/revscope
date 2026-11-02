package com.revscope.core.obd.escucha

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumenCapturaTest {

    private fun captura(tramas: List<TramaCan>, duracionMs: Long) = CapturaCan(
        id = "1-base",
        etiqueta = "base",
        inicioEpochMs = 1_730_000_000_000L,
        duracionMs = duracionMs,
        protocolo = "6",
        bits29 = false,
        filtro = null,
        silencioso = true,
        desbordes = 0,
        errores = 0,
        tramas = tramas,
    )

    @Test
    fun `calcula hz y bytes que cambian ordenado por id`() {
        val tramas = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x10, 0x00)),
            TramaCan(1000L, 0x7E8, false, listOf(0x10, 0x01)),
            TramaCan(0L, 0x100, false, listOf(0x05)),
            TramaCan(1000L, 0x100, false, listOf(0x05)),
        )

        val resumen = ResumenCaptura.calcular(captura(tramas, 2000L))

        assertEquals(
            listOf(
                ResumenId("100", 2, 1.0, setOf(1), emptyList()),
                ResumenId("7E8", 2, 1.0, setOf(2), listOf(1)),
            ),
            resumen,
        )
    }

    @Test
    fun `duracion cero da hz cero`() {
        val tramas = listOf(TramaCan(0L, 0x7E8, false, listOf(0x01)))

        val resumen = ResumenCaptura.calcular(captura(tramas, 0L))

        assertEquals(listOf(ResumenId("7E8", 1, 0.0, setOf(1), emptyList())), resumen)
    }
}
