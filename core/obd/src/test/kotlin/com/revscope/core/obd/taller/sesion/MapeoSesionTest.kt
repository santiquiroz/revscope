package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.DiagEventEntity
import com.revscope.core.data.db.entities.DiagSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MapeoSesionTest {

    @Test
    fun `síntomas desconocidos de otra versión se ignoran sin perder los conocidos`() {
        val entidad = DiagSessionEntity(startedAt = 1, title = "t", symptomTags = "SE_APAGA_AL_SOLTAR,FUTURO, ")

        assertEquals(setOf(Sintoma.SE_APAGA_AL_SOLTAR), entidad.aSesion().sintomas)
    }

    @Test
    fun `tipo y veredicto desconocidos caen en nota e informativo`() {
        val entidad = DiagEventEntity(sessionId = 1, timestamp = 1, type = "FUTURO", title = "t", verdict = "RARO", source = "OTRO")

        val evento = entidad.aEvento()

        assertEquals(TipoEvento.NOTA, evento.tipo)
        assertEquals(Veredicto.INFO, evento.veredicto)
        assertEquals(OrigenEvento.APP, evento.origen)
    }

    @Test
    fun `los conjuntos se guardan ordenados para que el texto sea estable`() {
        val sesion = SesionTaller(
            vehiculoId = 1,
            inicio = 1,
            titulo = "t",
            sintomas = setOf(Sintoma.TIRONES, Sintoma.MIL_ENCENDIDA),
            pasosMarcados = setOf("P0122#3", "P0122#1"),
        )

        val entidad = sesion.aEntidad()

        assertEquals("MIL_ENCENDIDA,TIRONES", entidad.symptomTags)
        assertEquals("P0122#1,P0122#3", entidad.checkedSteps)
        assertEquals(sesion, entidad.aSesion())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una clave de paso con coma se rechaza en vez de partirse en dos`() {
        SesionTaller(vehiculoId = 1, inicio = 1, titulo = "t", pasosMarcados = setOf("P0122,2")).aEntidad()
    }
}
