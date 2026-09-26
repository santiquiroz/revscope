package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Batería (sobre la ráfaga de AT RV) y MAP contra la barométrica de punta a punta sobre el controlador real.
class PruebasElectricasControladorTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private class FuentesFalsas(
        val ambiente: LecturasAmbiente? = null,
        val desfase: DesfaseVoltaje = DesfaseVoltaje.SIN_CALIBRAR,
        val fallarAmbiente: Boolean = false,
    ) : FuentesAnalisis {
        var lecturasAmbiente = 0
        override suspend fun ambiente(): LecturasAmbiente? {
            lecturasAmbiente++
            if (fallarAmbiente) throw IllegalStateException("sin sensores")
            return ambiente
        }
        override suspend fun desfaseVoltaje(): DesfaseVoltaje = desfase
    }

    // Reposo 12,2 V con la farola; cae a 10,4 V al girar el motor de arranque (a 1 s del paso) y carga a 14,2 V.
    private fun bateriaSana(tMs: Long): Double = when {
        tMs < 11_000 -> 12.2
        tMs < 11_300 -> 10.4
        tMs < 12_500 -> 11.0
        tMs < 33_000 -> 13.6
        else -> 14.2
    }

    private fun TestScope.montaje(fuentes: FuentesAnalisis = FuentesFalsas(), valorEn: (Long) -> Double = { 0.0 }) =
        MontajePrueba(this, carpeta.root, valorEn = valorEn, voltajeEn = ::bateriaSana, fuentes = fuentes).apply {
            enlace.lecturas = mapOf("0C" to ObdReading("0C", 0.0, "rpm", reloj()))
            enlace.soportados = setOf("0C", "0D", "0B")
        }

    @Test
    fun `la batería corre sobre la ráfaga de voltaje con el desfase del vehículo y queda en la sesión`() = runTest {
        val m = montaje(FuentesFalsas(desfase = DesfaseVoltaje.editado(0.1)))
        val sesion = m.abrirSesion()

        val primero = m.controlador.iniciar(TipoPrueba.BATERIA_CARGA).getOrThrow() as EstadoPrueba.EnPaso
        advanceTimeBy(33_100)
        val altas = m.controlador.estado.value as EstadoPrueba.EnPaso
        m.controlador.avanzar().getOrThrow()
        advanceTimeBy(10_100)
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        assertEquals(Pasos.CONTACTO, primero.paso.clave)
        assertEquals(Pasos.RPM_ALTAS, altas.paso.clave)
        assertEquals(FasePaso.POSICIONANDO, altas.fase)
        assertEquals(listOf("VBAT"), m.rafaga!!.configs.single().pids)
        assertTrue("la captura rápida no se usa", m.captura.configs.isEmpty())
        val a = terminada.resultado.detalle as AnalisisBateria
        assertEquals(PatronBateria.NORMAL, a.patron)
        assertEquals(DesfaseVoltaje.editado(0.1), a.desfase)
        assertEquals(12.3, a.contacto!!.mediaV, 0.01)
        assertEquals(10.5, a.arranque!!.valleV!!, 1e-9)
        assertEquals(14.3, a.rpmAltas!!.mediaV, 0.01)
        assertEquals(listOf("prueba guiada"), m.rafaga!!.motivosDetener)
        val evento = m.repositorio.eventos(sesion.id).single { it.tipo == TipoEvento.PRUEBA_GUIADA }
        assertEquals("Batería y carga", evento.titulo)
        assertEquals(Veredicto.OK, evento.veredicto)
        assertEquals("NORMAL", JSONObject(evento.payloadJson).getJSONObject("detalle").getString("patron"))
    }

    @Test
    fun `si el enlace se cae durante el arranque se analiza lo capturado como adaptador reiniciado`() = runTest {
        val m = montaje()
        val sesion = m.abrirSesion()

        m.controlador.iniciar(TipoPrueba.BATERIA_CARGA).getOrThrow()
        advanceTimeBy(11_200)
        m.enlace.conectado = false
        advanceTimeBy(300)
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        val datos = terminada.datos!!
        assertEquals(listOf(Pasos.CONTACTO, Pasos.ARRANQUE), datos.segmentos.map { it.clave })
        assertEquals(Pasos.ARRANQUE, datos.contexto.enlacePerdidoEn)
        val a = terminada.resultado.detalle as AnalisisBateria
        assertEquals(PatronBateria.ADAPTADOR_REINICIADO, a.patron)
        assertNull(a.rpmAltas)
        assertTrue(terminada.resultado.hallazgos.any { it.contains("adaptador se reinició") })
        val evento = m.repositorio.eventos(sesion.id).single { it.tipo == TipoEvento.PRUEBA_GUIADA }
        assertEquals("Batería y carga", evento.titulo)
        assertEquals(Veredicto.ATENCION, evento.veredicto)
    }

    @Test
    fun `si el enlace se cae con el contacto, antes de arrancar, la prueba queda fallida y se puede repetir`() = runTest {
        val m = montaje()

        m.controlador.iniciar(TipoPrueba.BATERIA_CARGA).getOrThrow()
        advanceTimeBy(3_000)
        m.enlace.conectado = false
        advanceTimeBy(300)

        val fallida = m.controlador.estado.value as EstadoPrueba.Fallida
        assertEquals(ControladorPruebaGuiada.MOTIVO_ENLACE_PERDIDO, fallida.motivo)
        assertTrue(fallida.reintentable)
    }

    @Test
    fun `con una ráfaga de voltaje activa no arranca otra prueba`() = runTest {
        val m = montaje()
        m.rafaga!!.manualActiva = true

        val intento = m.controlador.iniciar(TipoPrueba.TPS_BARRIDO)

        assertEquals("Detén la captura rápida primero", intento.exceptionOrNull()?.message)
    }

    @Test
    fun `el MAP de 70 kPa contra la altitud de 1 500 m sale fuera de banda con la referencia estimada`() = runTest {
        val fuentes = FuentesFalsas(ambiente = LecturasAmbiente(altitudGpsM = 1_500.0))
        val m = montaje(fuentes, valorEn = { 70.0 })
        m.abrirSesion()

        m.controlador.iniciar(TipoPrueba.MAP_BARO).getOrThrow()
        advanceTimeBy(10_100)
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        assertEquals(listOf("0B"), m.captura.configs.single().pids)
        val a = terminada.resultado.detalle as AnalisisMapBaro
        assertEquals(PatronMapBaro.MAP_BAJO, a.patron)
        assertEquals(OrigenBarometrica.ALTITUD_GPS, a.referencia!!.origen)
        assertEquals(1, fuentes.lecturasAmbiente)
        assertEquals(Veredicto.FALLA, terminada.resultado.veredicto)
    }

    @Test
    fun `el MAP pide el PID 33 cuando la ECU lo tiene`() = runTest {
        val m = montaje(FuentesFalsas(), valorEn = { 84.0 })
        m.enlace.soportados = setOf("0C", "0D", "0B", "33")

        m.controlador.iniciar(TipoPrueba.MAP_BARO).getOrThrow()
        m.controlador.cancelar()

        assertEquals(listOf("0B", "33"), m.captura.configs.single().pids)
    }

    @Test
    fun `si el teléfono no puede leer el ambiente el MAP se analiza igual, sin referencia`() = runTest {
        val m = montaje(FuentesFalsas(fallarAmbiente = true), valorEn = { 70.0 })

        m.controlador.iniciar(TipoPrueba.MAP_BARO).getOrThrow()
        advanceTimeBy(10_100)
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        assertEquals(PatronMapBaro.SIN_REFERENCIA, (terminada.resultado.detalle as AnalisisMapBaro).patron)
    }

    @Test
    fun `las pruebas que no son de voltaje no leen el desfase ni el ambiente`() = runTest {
        val fuentes = FuentesFalsas(desfase = DesfaseVoltaje.editado(0.3))
        val m = montaje(fuentes, valorEn = { 1_400.0 })
        m.enlace.lecturas = mapOf("0C" to ObdReading("0C", 1_400.0, "rpm", m.reloj()))

        m.controlador.iniciar(TipoPrueba.MINIMO_RETORNO).getOrThrow()
        advanceTimeBy(45_100 + 3 * 8_000L)
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        assertEquals(DesfaseVoltaje.SIN_CALIBRAR, terminada.datos!!.contexto.desfaseVoltaje)
        assertEquals(0, fuentes.lecturasAmbiente)
        assertTrue(m.rafaga!!.configs.isEmpty())
    }
}
