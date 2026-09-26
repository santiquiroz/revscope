package com.revscope.core.obd.taller.sesion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoSesionTest {

    private fun evento(id: Long, tipo: TipoEvento, payload: String) =
        EventoTaller(id = id, sesionId = 1, instante = id * 1_000, tipo = tipo, titulo = tipo.name, payloadJson = payload)

    private val lectura = evento(1, TipoEvento.DTC_LECTURA, """{"activos":["P0122"],"pendientes":["P0507"],"permanentes":[]}""")

    @Test
    fun `los códigos salen de la lectura más reciente`() {
        assertEquals(listOf("P0122", "P0507"), EstadoSesion.codigosActuales(listOf(lectura)))
    }

    @Test
    fun `tras un borrado mandan los activos releídos y si la ECU lo rechazó los de antes`() {
        val borrado = evento(
            2, TipoEvento.DTC_BORRADO,
            """{"antes":{"activos":["P0122"]},"despues":{"activos":[]},"rechazadoPorCondiciones":false}""",
        )
        val rechazado = evento(
            3, TipoEvento.DTC_BORRADO,
            """{"antes":{"activos":["P0122"]},"despues":{"activos":["P0122"]},"rechazadoPorCondiciones":true}""",
        )

        assertTrue(EstadoSesion.codigosActuales(listOf(lectura, borrado)).isEmpty())
        assertEquals(listOf("P0122"), EstadoSesion.codigosActuales(listOf(lectura, borrado, rechazado)))
    }

    @Test
    fun `un chequeo que no pudo leer códigos no borra lo que dijo la lectura anterior`() {
        val chequeo = evento(2, TipoEvento.CHEQUEO, """{"metricas":{"dtcs":[],"dtcsLeidos":false,"voltaje":14.1}}""")

        assertEquals(listOf("P0122", "P0507"), EstadoSesion.codigosActuales(listOf(lectura, chequeo)))
        assertEquals(14.1, EstadoSesion.ultimasMetricas(listOf(lectura, chequeo))!!.voltaje!!, 1e-9)
    }

    @Test
    fun `un payload dañado se ignora`() {
        assertTrue(EstadoSesion.codigosActuales(listOf(evento(1, TipoEvento.DTC_LECTURA, "{roto"))).isEmpty())
        assertNull(EstadoSesion.ultimasMetricas(listOf(evento(1, TipoEvento.CHEQUEO, "{roto"))))
    }
}
