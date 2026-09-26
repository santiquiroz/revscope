package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.core.obd.telemetry.captura.CapturaRapida
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ControladorPruebaGuiadaTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun TestScope.montaje() = MontajePrueba(this, carpeta.root)

    private suspend fun MontajePrueba.iniciarTps(): EstadoPrueba = controlador.iniciar(TipoPrueba.TPS_BARRIDO).getOrThrow()

    private fun EstadoPrueba.comoPaso() = this as EstadoPrueba.EnPaso

    @Test
    fun `sin adaptador ni tps disponible no arranca y dice que hacer`() = runTest {
        val m = montaje()
        m.enlace.conectado = false
        m.enlace.soportados = setOf("0C", "0D")

        val estado = m.iniciarTps() as EstadoPrueba.Verificando

        assertFalse(estado.listas)
        assertEquals(listOf("Sin adaptador conectado", "TPS no disponible en esta ECU (PID 11)"), estado.precondiciones.filterNot { it.cumple }.map { it.texto })
        assertTrue(estado.precondiciones.filterNot { it.cumple }.all { it.queHacer != null })
        assertTrue(m.captura.configs.isEmpty())
    }

    @Test
    fun `con el motor encendido o la moto andando no arranca y lo explica con el valor leido`() = runTest {
        val m = montaje()
        m.enlace.lecturas = mapOf(
            "0C" to ObdReading("0C", 1_454.0, "rpm", m.reloj()),
            "0D" to ObdReading("0D", 12.0, "km/h", m.reloj()),
        )

        val estado = m.iniciarTps() as EstadoPrueba.Verificando

        assertEquals(
            listOf("Motor encendido (1454 rpm)", "Moto en movimiento (12 km/h)"),
            estado.precondiciones.filterNot { it.cumple }.map { it.texto },
        )
        assertTrue(m.captura.configs.isEmpty())
    }

    @Test
    fun `una lectura de rpm vieja no cuenta como motor encendido`() = runTest {
        val m = montaje()
        m.enlace.lecturas = mapOf("0C" to ObdReading("0C", 1_454.0, "rpm", m.reloj() - 60_000))

        val estado = m.iniciarTps()

        assertTrue(estado is EstadoPrueba.EnPaso)
        m.controlador.cancelar()
    }

    @Test
    fun `con una captura manual activa pide detenerla primero`() = runTest {
        val m = montaje()
        m.captura.manualActiva = true

        val intento = m.controlador.iniciar(TipoPrueba.TPS_BARRIDO)

        assertEquals("Detén la captura rápida primero", intento.exceptionOrNull()?.message)
    }

    @Test
    fun `arranca la captura guiada del tps vigilando la velocidad y espera el listo del primer paso`() = runTest {
        val m = montaje()
        m.enlace.soportados = setOf("0C", "0D", "11", "45")

        val estado = m.iniciarTps().comoPaso()

        val config = m.captura.configs.single()
        assertEquals(listOf("11", "45"), config.pids)
        assertEquals(listOf("0D"), config.vigilar)
        assertTrue(config.guiada)
        assertEquals(Pasos.CERRADO_1, estado.paso.clave)
        assertEquals(1 to 5, (estado.indice + 1) to estado.total)
        assertEquals(FasePaso.POSICIONANDO, estado.fase)
        assertNull(estado.restanteMs)
        assertTrue(m.voz.dichos.single().startsWith("Paso 1 de 5. Cerrado."))
        assertTrue(m.controlador.enCurso())
        m.controlador.cancelar()
    }

    @Test
    fun `listo arranca la cuenta regresiva con el reloj de la captura y al terminar pasa al siguiente paso`() = runTest {
        val m = montaje()
        m.iniciarTps()
        advanceTimeBy(3_000)
        val esperando = m.controlador.estado.value.comoPaso()

        val sosteniendo = m.controlador.avanzar().getOrThrow().comoPaso()
        advanceTimeBy(2_050)
        val aMitad = m.controlador.estado.value.comoPaso()
        advanceTimeBy(3_000)
        val siguiente = m.controlador.estado.value.comoPaso()

        assertEquals(FasePaso.POSICIONANDO, esperando.fase)
        assertEquals(FasePaso.SOSTENIENDO, sosteniendo.fase)
        assertEquals(5_000L, sosteniendo.restanteMs)
        assertEquals(3_000L, aMitad.restanteMs)
        assertEquals(Pasos.MEDIO, siguiente.paso.clave)
        assertEquals(FasePaso.POSICIONANDO, siguiente.fase)
        assertTrue(m.voz.dichos.contains("Sostén 5 segundos"))
        assertTrue(m.voz.dichos.last().startsWith("Paso 2 de 5. Medio."))
        m.controlador.cancelar()
    }

    @Test
    fun `el paso que graba solo no acepta listo y arranca grabando`() = runTest {
        val m = montaje()
        m.iniciarTps()
        m.sostenerLosCuatroPasos(this)

        val barrido = m.controlador.estado.value.comoPaso()
        val intento = m.controlador.avanzar()

        assertEquals(Pasos.BARRIDO_LENTO, barrido.paso.clave)
        assertEquals(FasePaso.GRABANDO, barrido.fase)
        assertTrue(intento.isFailure)
        m.controlador.cancelar()
    }

    @Test
    fun `al terminar analiza con las bandas del vehiculo y anota el resultado en la sesion con el csv`() = runTest {
        val m = montaje()
        val sesion = m.abrirSesion()
        m.captura.rutaCsv = carpeta.newFile("revscope-captura-prueba.csv").apply { writeText("t_ms,pid,valor\n") }.absolutePath
        m.iniciarTps()

        m.sostenerLosCuatroPasos(this)
        advanceTimeBy(8_100)
        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        val a = terminada.resultado.detalle as AnalisisBarridoTps
        assertEquals(PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO, a.patron)
        assertEquals(Pasos.TODOS, a.pasos.map { it.clave })
        val evento = m.repositorio.eventos(sesion.id).single { it.tipo == TipoEvento.PRUEBA_GUIADA }
        assertEquals(evento.id, terminada.eventoId)
        assertEquals(Veredicto.FALLA, evento.veredicto)
        assertEquals("Barrido del TPS", evento.titulo)
        assertNotNull("el CSV de la captura queda adjunto a la sesión", evento.adjunto)
        val payload = JSONObject(evento.payloadJson)
        assertEquals("SENAL_BAJA_TODO_EL_RECORRIDO", payload.getJSONObject("detalle").getString("patron"))
        assertEquals(5, payload.getJSONArray("segmentos").length())
        assertTrue(payload.getJSONObject("serie").has("11"))
        assertEquals(listOf(ControladorPruebaGuiada.MOTIVO_FIN_CAPTURA), m.captura.motivosDetener)
        assertFalse(m.controlador.enCurso())
        assertTrue(m.voz.dichos.last().startsWith("Prueba terminada."))
    }

    @Test
    fun `sin sesion abierta el resultado queda y se guarda al abrir una`() = runTest {
        val m = montaje()
        m.iniciarTps()
        m.sostenerLosCuatroPasos(this)
        advanceTimeBy(8_100)
        val sinSesion = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        val sesion = m.abrirSesion()
        val eventoId = m.controlador.guardarResultadoEnSesion()

        assertNull(sinSesion.eventoId)
        assertEquals(m.repositorio.eventos(sesion.id).single { it.tipo == TipoEvento.PRUEBA_GUIADA }.id, eventoId)
        assertEquals(eventoId, (m.controlador.estado.value as EstadoPrueba.Terminada).eventoId)
    }

    @Test
    fun `velocidad mayor que 0 durante la prueba la cancela y detiene la captura`() = runTest {
        val m = montaje()
        m.iniciarTps()
        m.controlador.avanzar()
        advanceTimeBy(1_000)

        m.enlace.lecturas = mapOf("0D" to ObdReading("0D", 7.0, "km/h", m.reloj()))
        advanceTimeBy(150)

        val cancelada = m.controlador.estado.value as EstadoPrueba.Cancelada
        assertTrue(cancelada.motivo, cancelada.motivo.startsWith("Moto en movimiento (7 km/h)"))
        assertFalse(m.captura.activa())
        assertFalse(m.controlador.enCurso())
    }

    @Test
    fun `enlace perdido deja la prueba fallida y reintentable, con lo capturado en la sesion`() = runTest {
        val m = montaje()
        val sesion = m.abrirSesion()
        m.iniciarTps()
        m.controlador.avanzar()
        advanceTimeBy(6_000)

        m.enlace.conectado = false
        m.captura.terminarSola(CapturaRapida.MOTIVO_ENLACE)
        advanceTimeBy(150)
        val fallida = m.controlador.estado.first { it is EstadoPrueba.Fallida } as EstadoPrueba.Fallida

        assertEquals(ControladorPruebaGuiada.MOTIVO_ENLACE_PERDIDO, fallida.motivo)
        assertTrue(fallida.reintentable)
        val evento = m.repositorio.eventos(sesion.id).single { it.tipo == TipoEvento.PRUEBA_GUIADA }
        assertEquals("Barrido del TPS sin terminar", evento.titulo)
        assertEquals(Veredicto.ATENCION, evento.veredicto)
        assertEquals(1, JSONObject(evento.payloadJson).getInt("pasosCompletos"))

        m.enlace.conectado = true
        val otraVez = m.controlador.reintentar().getOrThrow().comoPaso()
        assertEquals(Pasos.CERRADO_1, otraVez.paso.clave)
        assertEquals(2, m.captura.configs.size)
        m.controlador.cancelar()
    }

    @Test
    fun `el enlace desconectado basta para fallar la prueba aunque la captura no haya terminado aun`() = runTest {
        val m = montaje()
        m.iniciarTps()

        m.enlace.conectado = false
        advanceTimeBy(150)
        val fallida = m.controlador.estado.first { it is EstadoPrueba.Fallida } as EstadoPrueba.Fallida

        assertEquals(ControladorPruebaGuiada.MOTIVO_ENLACE_PERDIDO, fallida.motivo)
        assertFalse("la prueba suelta la captura", m.captura.activa())
    }

    @Test
    fun `una salvaguarda de la captura deja la prueba fallida con su motivo`() = runTest {
        val m = montaje()
        m.iniciarTps()

        m.captura.terminarSola("batería del teléfono por debajo de 15 %")
        advanceTimeBy(150)
        val fallida = m.controlador.estado.first { it is EstadoPrueba.Fallida } as EstadoPrueba.Fallida

        assertEquals("La captura se detuvo: batería del teléfono por debajo de 15 %", fallida.motivo)
    }

    @Test
    fun `detener la captura desde otra pantalla cancela la prueba`() = runTest {
        val m = montaje()
        m.iniciarTps()

        m.captura.terminarSola(CapturaRapida.MOTIVO_USUARIO)
        advanceTimeBy(150)

        assertTrue(m.controlador.estado.value is EstadoPrueba.Cancelada)
    }

    @Test
    fun `una sola prueba a la vez y cancelar detiene la captura sin anotar en la sesion`() = runTest {
        val m = montaje()
        val sesion = m.abrirSesion()
        m.iniciarTps()

        val segunda = m.controlador.iniciar(TipoPrueba.TPS_BARRIDO)
        val cancelada = m.controlador.cancelar()
        runCurrent()

        assertEquals("Ya hay una prueba guiada en curso: termínala o cancélala", segunda.exceptionOrNull()?.message)
        assertEquals(ControladorPruebaGuiada.MOTIVO_USUARIO, (cancelada as EstadoPrueba.Cancelada).motivo)
        assertFalse(m.captura.activa())
        assertTrue(m.repositorio.eventos(sesion.id).none { it.tipo == TipoEvento.PRUEBA_GUIADA })
        assertEquals(EstadoPrueba.Inactiva, m.controlador.cerrar())
    }

    @Test
    fun `repetir paso vuelve a posicionar el paso actual sin perder los anteriores`() = runTest {
        val m = montaje()
        m.iniciarTps()
        m.controlador.avanzar()
        advanceTimeBy(6_000)
        m.controlador.avanzar()
        advanceTimeBy(2_000)

        val repetido = m.controlador.repetirPaso().getOrThrow().comoPaso()

        assertEquals(Pasos.MEDIO, repetido.paso.clave)
        assertEquals(FasePaso.POSICIONANDO, repetido.fase)
        m.controlador.cancelar()
    }

    @Test
    fun `sin voz no anuncia nada`() = runTest {
        val m = montaje()

        m.controlador.iniciar(TipoPrueba.TPS_BARRIDO, OpcionesPrueba(voz = false)).getOrThrow()
        m.controlador.avanzar()
        m.controlador.cancelar()

        assertTrue(m.voz.dichos.isEmpty())
    }

    @Test
    fun `apagar la voz a mitad de prueba calla los anuncios siguientes`() = runTest {
        val m = montaje()
        m.iniciarTps()
        val antes = m.voz.dichos.size

        m.controlador.cambiarVoz(false)
        m.controlador.avanzar()
        advanceTimeBy(6_000)
        m.controlador.cambiarVoz(true)
        m.controlador.avanzar()

        assertEquals(antes + 1, m.voz.dichos.size)
        assertEquals("Sostén 5 segundos", m.voz.dichos.last())
        m.controlador.cancelar()
    }

    @Test
    fun `la prueba terminada trae los datos con sus segmentos para dibujar la serie`() = runTest {
        val m = montaje()
        m.iniciarTps()
        m.sostenerLosCuatroPasos(this)
        advanceTimeBy(8_100)

        val terminada = m.controlador.estado.first { it is EstadoPrueba.Terminada } as EstadoPrueba.Terminada

        val datos = terminada.datos!!
        assertEquals(Pasos.TODOS, datos.segmentos.map { it.clave })
        assertTrue(datos.muestras.any { it.pid == AnalizadorBarridoTps.PID_TPS })
        assertEquals(ReferenciaVoltaje.TIPICA, datos.vref)
    }

    @Test
    fun `una prueba sin analizador todavia no se ofrece`() = runTest {
        val m = montaje()

        val intento = m.controlador.iniciar(TipoPrueba.MAP_BARO)

        assertEquals("La prueba «MAP contra presión barométrica» todavía no está disponible", intento.exceptionOrNull()?.message)
    }
}
