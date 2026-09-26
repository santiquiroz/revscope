package com.revscope.core.obd.taller.grafica

import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.sesion.BENELLI
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TallerRepositoryEnMemoria
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuenteVrefTest {

    private fun medicion(instante: Long, payload: String) = EventoTaller(
        sesionId = 0,
        instante = instante,
        tipo = TipoEvento.MEDICION_MULTIMETRO,
        titulo = "Multímetro · TPS",
        payloadJson = payload,
    )

    private fun lecturas(vararg ref: Double) =
        """{"lecturas":[${ref.joinToString(",") { """{"funcion":"REF_5V","condicion":"KOEO","valor":$it,"unidad":"V"}""" }},""" +
            """{"funcion":"SENAL","condicion":"CERRADO","valor":0.1,"unidad":"V"}]}"""

    @Test
    fun `sin mediciones de la referencia en la sesion no hay vref medida`() {
        assertNull(VrefSesion.desde(emptyList()))
        assertNull(VrefSesion.desde(listOf(medicion(1, """{"lecturas":[{"funcion":"SENAL","valor":0.1,"unidad":"V"}]}"""))))
        assertNull(VrefSesion.desde(listOf(medicion(1, "no es json"))))
    }

    @Test
    fun `la referencia medida es el promedio de la ultima medicion y lo dice`() {
        val vref = VrefSesion.desde(listOf(medicion(2, lecturas(5.02, 4.98, 5.0)), medicion(1, lecturas(4.5))))!!

        assertEquals(5.0, vref.voltios, 1e-9)
        assertEquals(VrefSesion.ORIGEN, vref.origen)
    }

    @Test
    fun `una lectura fuera de rango o en otra unidad no cuenta como referencia`() {
        val fuera = """{"lecturas":[{"funcion":"REF_5V","valor":12.4,"unidad":"V"},{"funcion":"REF_5V","valor":4900,"unidad":"mV"}]}"""

        assertNull(VrefSesion.desde(listOf(medicion(1, fuera))))
    }

    @Test
    fun `lo editado manda sobre lo medido y lo medido sobre lo tipico`() {
        val medida = ReferenciaVoltaje(4.96, VrefSesion.ORIGEN)

        assertEquals(ReferenciaVoltaje.editada(4.9), ResolutorVref.resolver(4.9, medida))
        assertEquals(medida, ResolutorVref.resolver(null, medida))
        assertEquals(ReferenciaVoltaje.TIPICA, ResolutorVref.resolver(null, null))
    }

    @Test
    fun `el texto de la vref acepta coma o punto y solo dentro del rango`() {
        assertEquals(4.98, ResolutorVref.leer("4,98")!!, 1e-9)
        assertEquals(4.98, ResolutorVref.leer(" 4.98 ")!!, 1e-9)
        assertNull(ResolutorVref.leer("7"))
        assertNull(ResolutorVref.leer("2,9"))
        assertNull(ResolutorVref.leer("cinco"))
    }

    @Test
    fun `la fuente combina lo guardado para el vehiculo con la medicion de la sesion abierta`() = runTest {
        val repositorio = TallerRepositoryEnMemoria()
        val preferencias = PreferenciasVrefEnMemoria()
        var vehiculo: VehiculoTaller? = BENELLI
        val fuente = FuenteVref(preferencias, repositorio) { vehiculo }
        val sesionId = repositorio.abrirSesion(SesionTaller(vehiculoId = BENELLI.id, inicio = 1, titulo = "TPS"))
        repositorio.agregarEvento(medicion(2, lecturas(4.96)).copy(sesionId = sesionId))

        val inicial = fuente.actual()
        fuente.guardar(4.9)
        val editada = fuente.actual()
        val guardada = preferencias.guardadas[BENELLI.id]
        fuente.guardar(null)
        val sinEditar = fuente.actual()
        vehiculo = null
        val sinVehiculo = fuente.actual()

        assertEquals(4.96, inicial.usada.voltios, 1e-9)
        assertEquals(4.96, inicial.medida!!.voltios, 1e-9)
        assertNull(inicial.editadaV)
        assertEquals(ReferenciaVoltaje.editada(4.9), editada.usada)
        assertEquals(4.9, guardada!!, 1e-9)
        assertEquals(VrefSesion.ORIGEN, sinEditar.usada.origen)
        assertEquals(ReferenciaVoltaje.TIPICA, sinVehiculo.usada)
    }
}

class PreferenciasVrefEnMemoria : PreferenciasVref {
    val guardadas = mutableMapOf<Long?, Double>()

    override suspend fun leer(vehiculoId: Long?): Double? = guardadas[vehiculoId]

    override suspend fun guardar(vehiculoId: Long?, voltios: Double?) {
        if (voltios == null) guardadas.remove(vehiculoId) else guardadas[vehiculoId] = voltios
    }
}
