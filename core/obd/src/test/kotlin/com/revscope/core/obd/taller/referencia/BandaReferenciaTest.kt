package com.revscope.core.obd.taller.referencia

import org.junit.Assert.assertEquals
import org.junit.Test

class BandaReferenciaTest {

    private val cerrado = BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.3, 1.0, "V", OrigenBanda.TIPICO)

    @Test
    fun `clasifica bajo, dentro y alto con los límites incluidos`() {
        assertEquals(PosicionEnBanda.BAJO, cerrado.clasificar(0.12))
        assertEquals(PosicionEnBanda.DENTRO, cerrado.clasificar(0.3))
        assertEquals(PosicionEnBanda.DENTRO, cerrado.clasificar(1.0))
        assertEquals(PosicionEnBanda.ALTO, cerrado.clasificar(1.4))
    }

    @Test
    fun `banda abierta por arriba solo marca bajo`() {
        val arranque = BandaReferencia(ClavesBanda.ARRANQUE_MIN_V, 9.6, null, "V", OrigenBanda.TIPICO)

        assertEquals(PosicionEnBanda.BAJO, arranque.clasificar(8.9))
        assertEquals(PosicionEnBanda.DENTRO, arranque.clasificar(14.0))
    }

    @Test
    fun `la etiqueta dice el origen y cita la fuente`() {
        assertEquals("Típico (editable)", cerrado.etiquetaOrigen)
        assertEquals("Editado por ti", cerrado.copy(origen = OrigenBanda.USUARIO).etiquetaOrigen)
        assertEquals(
            "Fuente: Manual de servicio",
            cerrado.copy(origen = OrigenBanda.FUENTE, fuente = "Manual de servicio").etiquetaOrigen,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una banda con fuente exige citarla`() {
        BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.5, 0.7, "V", OrigenBanda.FUENTE, fuente = " ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una banda sin límites no es válida`() {
        BandaReferencia(ClavesBanda.TPS_CERRADO_V, null, null, "V", OrigenBanda.TIPICO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una banda con el mínimo mayor que el máximo no es válida`() {
        BandaReferencia(ClavesBanda.TPS_CERRADO_V, 1.0, 0.3, "V", OrigenBanda.TIPICO)
    }
}
