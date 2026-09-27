package com.revscope.core.obd.diagnostics

import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.protocol.DtcServicio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisoLecturaDtcTest {

    private fun scan(
        fallidos: Set<DtcServicio> = emptySet(),
        enlacePerdido: Boolean = false,
        activos: List<String> = emptyList(),
    ) = DtcScan(
        activos = activos.map { DtcCode(it, DtcMode.Active) },
        pendientes = emptyList(),
        permanentes = emptyList(),
        milEncendida = null,
        conteoSegunEcu = null,
        freezeFrame = null,
        crudo = emptyMap(),
        errores = emptyList(),
        enlacePerdido = enlacePerdido,
        serviciosFallidos = fallidos,
    )

    @Test
    fun `una lectura completa sin codigos lo dice sin aviso`() {
        assertEquals("Sin códigos activos, pendientes ni permanentes", AvisoLecturaDtc.sinCodigos(scan()))
        assertNull(AvisoLecturaDtc.de(scan()))
    }

    @Test
    fun `sin respuesta al 03 no afirma que no haya codigos`() {
        val sinActivos = scan(setOf(DtcServicio.ACTIVOS))

        assertNull(AvisoLecturaDtc.sinCodigos(sinActivos))
        assertEquals(AvisoLecturaDtc.ACTIVOS_SIN_RESPUESTA, AvisoLecturaDtc.de(sinActivos))
    }

    @Test
    fun `sin respuesta a 07 y 0A solo afirma lo leido y nombra lo que falto`() {
        val parcial = scan(setOf(DtcServicio.PERMANENTES, DtcServicio.PENDIENTES))

        assertEquals("Sin códigos activos", AvisoLecturaDtc.sinCodigos(parcial))
        assertEquals(
            "La ECU no respondió a los códigos pendientes (07) y permanentes (0A). Algunas ECU no admiten esas lecturas; " +
                "los activos sí se leyeron.",
            AvisoLecturaDtc.de(parcial),
        )
    }

    @Test
    fun `sin respuesta solo al 0A enumera activos y pendientes`() {
        assertEquals("Sin códigos activos ni pendientes", AvisoLecturaDtc.sinCodigos(scan(setOf(DtcServicio.PERMANENTES))))
    }

    @Test
    fun `con codigos no hay texto de sin codigos y el enlace perdido al final avisa del freeze frame`() {
        val conCodigo = scan(enlacePerdido = true, activos = listOf("P0122"))

        assertNull(AvisoLecturaDtc.sinCodigos(conCodigo))
        assertEquals(AvisoLecturaDtc.ENLACE_PERDIDO_AL_FINAL, AvisoLecturaDtc.de(conCodigo))
    }
}
