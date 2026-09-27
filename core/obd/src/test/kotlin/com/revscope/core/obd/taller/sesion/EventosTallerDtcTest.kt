package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.protocol.DtcServicio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventosTallerDtcTest {

    private val sinNombre: (String) -> String? = { null }

    private fun scan(fallidos: Set<DtcServicio> = emptySet(), activos: List<String> = emptyList()) = DtcScan(
        activos = activos.map { DtcCode(it, DtcMode.Active) },
        pendientes = emptyList(),
        permanentes = emptyList(),
        milEncendida = null,
        conteoSegunEcu = null,
        freezeFrame = null,
        crudo = emptyMap(),
        errores = emptyList(),
        serviciosFallidos = fallidos,
    )

    @Test
    fun `una lectura sin respuesta al 03 no se anota como sin codigos ni OK`() {
        val evento = EventosTaller.lecturaDtc(scan(setOf(DtcServicio.ACTIVOS)), OrigenEvento.APP, sinNombre)

        assertEquals(Veredicto.ATENCION, evento.veredicto)
        assertTrue(evento.resumen.contains("la ECU no respondió a los códigos activos"))
        assertFalse(evento.resumen.contains("Sin códigos"))
    }

    @Test
    fun `una lectura sin respuesta al 0A queda OK y dice lo que si leyo`() {
        val evento = EventosTaller.lecturaDtc(scan(setOf(DtcServicio.PERMANENTES)), OrigenEvento.APP, sinNombre)

        assertEquals(Veredicto.OK, evento.veredicto)
        assertEquals("Sin códigos activos ni pendientes · sin respuesta a permanentes", evento.resumen)
    }

    @Test
    fun `una lectura completa sin codigos conserva su resumen`() {
        val evento = EventosTaller.lecturaDtc(scan(), OrigenEvento.APP, sinNombre)

        assertEquals(Veredicto.OK, evento.veredicto)
        assertEquals("Sin códigos", evento.resumen)
    }

    @Test
    fun `el borrado sin relectura valida no se anota como exitoso`() {
        val borrado = BorradoDtc("44", false, antes = scan(activos = listOf("P0122")), despues = scan(setOf(DtcServicio.ACTIVOS)))

        val evento = EventosTaller.borradoDtc(borrado, OrigenEvento.APP, sinNombre)

        assertEquals(Veredicto.ATENCION, evento.veredicto)
        assertEquals("Antes: P0122 · se envió el borrado, pero no se pudo releer la ECU para confirmarlo", evento.resumen)
    }

    @Test
    fun `el borrado que no llego a enviarse lo dice`() {
        val borrado = BorradoDtc(null, false, antes = scan(activos = listOf("P0122")), despues = scan(setOf(DtcServicio.ACTIVOS)))

        val evento = EventosTaller.borradoDtc(borrado, OrigenEvento.APP, sinNombre)

        assertEquals(Veredicto.ATENCION, evento.veredicto)
        assertEquals("No se pudo enviar el borrado: el adaptador no respondió", evento.resumen)
    }
}
