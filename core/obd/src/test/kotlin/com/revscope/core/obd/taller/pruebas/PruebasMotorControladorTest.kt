package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// El mínimo y el arranque en frío de punta a punta sobre el controlador real, con la captura falsa del montaje.
class PruebasMotorControladorTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun EstadoPrueba.comoPaso() = this as EstadoPrueba.EnPaso

    // 45 s en 1 400 rpm y, en cada retorno de 8 s, un pico a 3 000 rpm a los 1,5 s que vuelve al mínimo.
    private fun minimoSano(tMs: Long): Double {
        if (tMs < 45_000) return 1_400.0
        val s = ((tMs - 45_000) % 8_000) / 1_000.0
        return MinimoSintetico.retornoSano()(s)
    }

    // Motor apagado 10 s en contacto; prende a los 2 s del paso de arranque.
    private fun arranqueEnFrio(tMs: Long): Double = if (tMs < 12_000) 0.0 else 1_700.0

    private fun TestScope.montaje(valorEn: (Long) -> Double) = MontajePrueba(this, carpeta.root, valorEn)

    @Test
    fun `el mínimo corre sus 4 pasos grabando solos y anota el resultado en la sesión`() = runTest {
        val m = montaje(::minimoSano)
        val sesion = m.abrirSesion()
        m.enlace.lecturas = mapOf("0C" to ObdReading("0C", 1_400.0, "rpm", m.reloj()))

        val primero = m.controlador.iniciar(TipoPrueba.MINIMO_RETORNO).getOrThrow().comoPaso()
        advanceTimeBy(45_100)
        val retorno = m.controlador.estado.value.comoPaso()
        advanceTimeBy(3 * 8_000L)
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        assertEquals(AnalizadorMinimo.Pasos.MINIMO, primero.paso.clave)
        assertEquals(FasePaso.GRABANDO, primero.fase)
        assertEquals(AnalizadorMinimo.Pasos.RETORNO_1, retorno.paso.clave)
        assertTrue(m.voz.dichos.any { it.startsWith("Paso 2 de 4. Retorno 1. Acelera hasta unas 3 000 rpm") })
        val a = terminada.resultado.detalle as AnalisisMinimo
        assertEquals(PatronMinimo.NORMAL, a.patron)
        assertEquals(3, a.retornosValidos.size)
        assertEquals(listOf("0C", "11", "05"), m.captura.configs.single().pids)
        val evento = m.repositorio.eventos(sesion.id).single { it.tipo == TipoEvento.PRUEBA_GUIADA }
        assertEquals("Mínimo y retorno", evento.titulo)
        assertEquals(Veredicto.OK, evento.veredicto)
        assertEquals("NORMAL", JSONObject(evento.payloadJson).getJSONObject("detalle").getString("patron"))
    }

    @Test
    fun `el mínimo no arranca con el motor apagado`() = runTest {
        val m = montaje(::minimoSano)

        val estado = m.controlador.iniciar(TipoPrueba.MINIMO_RETORNO).getOrThrow() as EstadoPrueba.Verificando

        assertEquals(listOf("Sin lectura de RPM"), estado.precondiciones.filterNot { it.cumple }.map { it.texto })
        assertTrue(m.captura.configs.isEmpty())
    }

    @Test
    fun `el arranque en frío pasa solo al calentamiento al prender y termina con Terminar`() = runTest {
        val m = montaje(::arranqueEnFrio)
        m.enlace.soportados = setOf("0C", "0D", "05", "0F", "11")

        m.controlador.iniciar(TipoPrueba.ARRANQUE_FRIO).getOrThrow().comoPaso()
        advanceTimeBy(10_100)
        val arrancando = m.controlador.estado.value.comoPaso()
        advanceTimeBy(4_500)
        val calentando = m.controlador.estado.value.comoPaso()
        m.controlador.avanzar().getOrThrow()
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        assertEquals(AnalizadorArranqueFrio.Pasos.ARRANQUE, arrancando.paso.clave)
        assertEquals(AnalizadorArranqueFrio.Pasos.CALENTAMIENTO, calentando.paso.clave)
        assertEquals(listOf("0C", "05", "0F", "11"), m.captura.configs.single().pids)
        val a = terminada.resultado.detalle as AnalisisArranqueFrio
        assertTrue(a.arranque.arranco)
        assertEquals(1, a.arranque.intentos)
        assertEquals(2.0, a.arranque.tiempoS!!, 0.2)
        assertEquals(CondicionMotor.SIN_DATO, a.plausibilidad.condicion)
    }
}
