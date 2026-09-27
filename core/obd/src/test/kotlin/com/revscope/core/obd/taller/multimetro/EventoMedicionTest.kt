package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.grafica.VrefSesion
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.NuevoEvento
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventoMedicionTest {

    private val tps = PlantillasGenericas.para(SensorMultimetro.TPS, VehicleType.MOTORCYCLE)
    private val bandas = BandasTipicas.para(VehicleType.MOTORCYCLE)

    private fun guardado(e: NuevoEvento, instante: Long, id: Long = instante) = EventoTaller(
        id = id, sesionId = 1, instante = instante, tipo = e.tipo, titulo = e.titulo, resumen = e.resumen,
        veredicto = e.veredicto, payloadJson = e.payload.toString(),
    )

    private fun lectura(f: FuncionCable, c: String, v: Double) = LecturaMultimetro(f, c, v, "V")

    @Test
    fun `la tabla guardada lleva el veredicto combinado y la referencia medida sirve para la prueba guiada`() {
        val lecturas = listOf(lectura(FuncionCable.REF_5V, "CERRADO", 4.96), lectura(FuncionCable.SENAL, "CERRADO", 0.1))
        val veredicto = VeredictoMultimetro.combinar(tps, lecturas, bandas)

        val evento = EventoMedicion.tabla(tps, veredicto)

        assertEquals(TipoEvento.MEDICION_MULTIMETRO, evento.tipo)
        assertEquals("Multímetro · TPS de 3 cables", evento.titulo)
        assertEquals(Veredicto.FALLA, evento.veredicto)
        assertEquals(veredicto.titulo, evento.payload.getJSONObject("veredicto").getString("titulo"))
        assertEquals(4.96, VrefSesion.desde(listOf(guardado(evento, 1_000)))!!.voltios, 1e-9)
    }

    @Test
    fun `la celda registrada sola resume cable, condición, valor y banda`() {
        val celda = EvaluadorCelda.evaluar(tps, lectura(FuncionCable.SENAL, "CERRADO", 0.1), emptyMap(), bandas)
        val acumulado = VeredictoMultimetro.combinar(tps, listOf(lectura(FuncionCable.SENAL, "CERRADO", 0.1)), bandas)

        val evento = EventoMedicion.celda(tps, celda, acumulado, OrigenEvento.MCP)

        assertEquals("Señal · Cerrado: 0,10 V, bajo (0,3–1,0 V · Típico (editable))", evento.resumen)
        assertEquals(Veredicto.FALLA, evento.veredicto)
        assertTrue(evento.payload.has("veredictoAcumulado"))
    }

    @Test
    fun `lo medido en la sesión junta los eventos y la medida más reciente de cada celda gana`() {
        val primero = EventoMedicion.tabla(
            tps,
            VeredictoMultimetro.combinar(tps, listOf(lectura(FuncionCable.SENAL, "CERRADO", 0.1), lectura(FuncionCable.MASA, "CERRADO", 0.0)), bandas),
        )
        val segundo = EventoMedicion.tabla(tps, VeredictoMultimetro.combinar(tps, listOf(lectura(FuncionCable.SENAL, "CERRADO", 0.6)), bandas))
        val otroSensor = EventoMedicion.tabla(
            PlantillasGenericas.para(SensorMultimetro.MAP, VehicleType.MOTORCYCLE),
            VeredictoMultimetro.combinar(
                PlantillasGenericas.para(SensorMultimetro.MAP, VehicleType.MOTORCYCLE),
                listOf(lectura(FuncionCable.SENAL, "CONTACTO", 3.9)),
                bandas,
            ),
        )

        val lecturas = LecturasSesion.de(listOf(guardado(segundo, 2_000), guardado(otroSensor, 3_000), guardado(primero, 1_000)), SensorMultimetro.TPS)

        assertEquals(
            setOf(lectura(FuncionCable.SENAL, "CERRADO", 0.6), lectura(FuncionCable.MASA, "CERRADO", 0.0)),
            lecturas.toSet(),
        )
    }

    @Test
    fun `las lecturas de la ECU salen del último barrido del TPS terminado de la sesión`() {
        fun prueba(estado: String, cerrado: Double, instante: Long) = EventoTaller(
            id = instante, sesionId = 1, instante = instante, tipo = TipoEvento.PRUEBA_GUIADA, titulo = "Barrido del TPS",
            payloadJson = JSONObject().put("prueba", "TPS_BARRIDO").put("estado", estado)
                .put("detalle", JSONObject().put("cerradoV", cerrado).put("medioV", 0.46).put("fondoV", 0.89)).toString(),
        )
        val eventos = listOf(prueba("TERMINADA", 0.12, 1_000), prueba("FALLIDA", 0.5, 3_000), prueba("TERMINADA", 0.13, 2_000))

        val ecu = LecturasEcuSesion.desde(eventos, SensorMultimetro.TPS)!!

        assertEquals(0.13, ecu.voltiosPorCondicion.getValue(CondicionesMultimetro.CERRADO), 1e-9)
        assertEquals(0.89, ecu.voltiosPorCondicion.getValue(CondicionesMultimetro.FONDO), 1e-9)
        assertTrue(ecu.origen.contains("Barrido del TPS"))
        assertNull(LecturasEcuSesion.desde(eventos, SensorMultimetro.MAP))
        assertNull(LecturasEcuSesion.desde(emptyList(), SensorMultimetro.TPS))
    }
}
