package com.revscope.core.obd.diagnostics.uds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserUdsTest {
    @Test
    fun interpretaTramasSimplesConHeaderEspaciadoYCompacto() {
        val tramas = ParserUds.tramas("7E803410D00\r7E8 03 41 0D 00", conHeaders = true)

        assertEquals(2, tramas.size)
        assertEquals(TramaRespuesta("7E8", listOf(0x41, 0x0D, 0x00)), tramas[0])
        assertEquals(tramas[0], tramas[1])
    }

    @Test
    fun reensamblaVinIsoTpConHeaders() {
        val tramas = ParserUds.tramas(
            "7E81014490201314847\r7E821434D3832363333\r7E82241303034333532",
            conHeaders = true,
        )

        assertEquals(1, tramas.size)
        assertEquals("7E8", tramas.single().header)
        assertEquals(20, tramas.single().payload.size)
        assertEquals(
            listOf(
                0x49, 0x02, 0x01, 0x31, 0x48, 0x47, 0x43, 0x4D, 0x38, 0x32,
                0x36, 0x33, 0x33, 0x41, 0x30, 0x30, 0x34, 0x33, 0x35, 0x32,
            ),
            tramas.single().payload,
        )
    }

    @Test
    fun mantieneRespuestasDeDosHeaders() {
        val respuestas = ParserUds.interpretar(
            "7E803410D00\r7E903410D01",
            servicioPedido = 0x01,
            conHeaders = true,
        )

        assertEquals(listOf("7E8", "7E9"), respuestas.map { it.header })
        assertTrue(respuestas.all { it is RespuestaUds.Positiva })
    }

    @Test
    fun interpretaNrcYEliminaPendienteSoloSiLuegoLlegaRespuestaDelMismoHeader() {
        val negativa = ParserUds.interpretar("7E8037F2231", 0x22, true).single()
        assertEquals(RespuestaUds.Negativa("7E8", 0x22, 0x31, "fuera de rango (DID/rutina inexistente)"), negativa)

        val raw = "7E8037F2278\r7E80362F190"
        assertEquals(2, ParserUds.tramas(raw, conHeaders = true).size)
        val final = ParserUds.interpretar(
            raw,
            servicioPedido = 0x22,
            conHeaders = true,
        )
        assertEquals(1, final.size)
        assertEquals(RespuestaUds.Positiva("7E8", 0x22, listOf(0xF1, 0x90)), final.single())

        val pendiente = ParserUds.interpretar("7E8037F2278", 0x22, true).single()
        assertEquals(RespuestaUds.Negativa("7E8", 0x22, 0x78, "respuesta pendiente"), pendiente)
    }

    @Test
    fun convierteNoDataEnSinRespuesta() {
        assertEquals(
            RespuestaUds.SinRespuesta(null, "NO DATA"),
            ParserUds.interpretar("NO DATA\r>", 0x22, true).single(),
        )
    }

    @Test
    fun interpretaRespuestaSinHeadersYReensamblaBloqueIndexado() {
        val simple = ParserUds.tramas("410D00", conHeaders = false).single()
        assertEquals(TramaRespuesta(null, listOf(0x41, 0x0D, 0x00)), simple)

        val multi = ParserUds.tramas(
            "014\r2:41303034333532\r0:490201314847\r1:434D3832363333",
            conHeaders = false,
        ).single()
        assertEquals(
            listOf(
                0x49, 0x02, 0x01, 0x31, 0x48, 0x47, 0x43, 0x4D, 0x38, 0x32,
                0x36, 0x33, 0x33, 0x41, 0x30, 0x30, 0x34, 0x33, 0x35, 0x32,
            ),
            multi.payload,
        )
        assertEquals(20, multi.payload.size)
        assertEquals(0x49, multi.payload.first())
        assertEquals(0x32, multi.payload.last())
    }

    @Test
    fun eliminaPrefijoSearchingYPrompt() {
        val respuesta = ParserUds.interpretar("SEARCHING...7E80362F190>", 0x22, true).single()

        assertEquals(RespuestaUds.Positiva("7E8", 0x22, listOf(0xF1, 0x90)), respuesta)
    }

    @Test
    fun formateaBytesEnHexadecimal() {
        assertEquals("F1 90", ParserUds.hex(listOf(0xF1, 0x90)))
        assertEquals("", ParserUds.hex(emptyList()))
    }
}
