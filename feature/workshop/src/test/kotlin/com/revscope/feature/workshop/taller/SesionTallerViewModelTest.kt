package com.revscope.feature.workshop.taller

import androidx.lifecycle.SavedStateHandle
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SesionTallerViewModelTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val despachador = UnconfinedTestDispatcher()
    private val minuto = 60_000L
    private lateinit var taller: TallerDePrueba
    private var sesionId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(despachador)
        taller = TallerDePrueba(carpeta.root)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun abrirBenelli(): Long = taller.repositorio.abrirSesion(
        SesionTaller(
            vehiculoId = BENELLI.id,
            inicio = SEP25_20H,
            titulo = "Se apaga al soltar el acelerador",
            sintomas = setOf(Sintoma.SE_APAGA_AL_SOLTAR, Sintoma.NO_SOSTIENE_MINIMO_FRIO),
            odometroKm = 12_345.0,
        ),
    ).also { sesionId = it }

    private suspend fun evento(minutos: Long, tipo: TipoEvento, titulo: String, adjunto: String? = null) =
        taller.repositorio.agregarEvento(
            EventoTaller(
                sesionId = sesionId,
                instante = SEP25_20H + minutos * minuto,
                tipo = tipo,
                titulo = titulo,
                veredicto = if (tipo == TipoEvento.DTC_LECTURA) Veredicto.FALLA else Veredicto.INFO,
                adjunto = adjunto,
                origen = if (tipo == TipoEvento.NOTA) OrigenEvento.MCP else OrigenEvento.APP,
            ),
        )

    private fun TestScope.vm(id: Long = sesionId): SesionTallerViewModel =
        SesionTallerViewModel(
            SavedStateHandle(mapOf(SesionTallerViewModel.ARG_SESION to id)),
            taller.repositorio,
            taller.registro,
            taller.analizador,
            taller.entorno,
        ).also { vm -> backgroundScope.launch { vm.estado.collect {} } }

    @Test
    fun `la línea de tiempo sale en orden cronológico aunque la base la devuelva desordenada`() = runTest(despachador) {
        abrirBenelli()
        evento(52, TipoEvento.CHEQUEO, "Chequeo de salud")
        evento(8, TipoEvento.DTC_LECTURA, "Lectura de códigos")
        evento(26, TipoEvento.INSTANTANEA_SENSORES, "Instantánea de sensores")
        evento(26, TipoEvento.NOTA, "Nota")
        evento(24 * 60 + 10, TipoEvento.NOTA, "Nota del día siguiente")

        val eventos = vm().estado.value.eventos

        assertEquals(
            listOf("Lectura de códigos", "Instantánea de sensores", "Nota", "Chequeo de salud", "Nota del día siguiente"),
            eventos.map { it.titulo },
        )
        assertEquals(listOf("20:08", "20:26", "20:26", "20:52", "26 sep · 20:10"), eventos.map { it.hora })
        assertTrue(eventos[2].detalle.contains("Anotado desde el PC (MCP)"))
    }

    @Test
    fun `el resumen muestra estado, síntomas en orden y odómetro`() = runTest(despachador) {
        abrirBenelli()

        val estado = vm().estado.value

        assertTrue(estado.abierta)
        assertEquals("Abierta desde las 20:00", estado.estadoTexto)
        assertEquals("Benelli TNT 150i · 25 sep 2026", estado.subtitulo)
        assertEquals(listOf("No sostiene el mínimo en frío", "Se apaga al soltar el acelerador"), estado.sintomas)
        assertEquals("12 345 km", estado.odometro)
        assertEquals(DisponibilidadAgregar.Disponible, estado.agregar)
        assertEquals(TarjetaComparacion.SinBase, estado.comparacion)
    }

    @Test
    fun `cerrar pide confirmación y después deja la sesión de solo lectura`() = runTest(despachador) {
        abrirBenelli()
        taller.entorno.reloj = SEP25_20H + 70 * minuto
        val vm = vm()

        vm.pedirCierre()
        assertEquals(DialogoSesion.CERRAR, vm.estado.value.ui.dialogo)
        assertTrue(taller.repositorio.sesion(sesionId)!!.abierta)

        vm.confirmarCierre()

        val estado = vm.estado.value
        assertNull(estado.ui.dialogo)
        assertFalse(estado.abierta)
        assertEquals("Cerrada el 25 sep 2026, 21:10", estado.estadoTexto)
        assertEquals(DisponibilidadAgregar.SesionCerrada, estado.agregar)
        assertEquals("Sesión cerrada: las lecturas nuevas ya no se anotan en ella", estado.ui.mensaje)
    }

    @Test
    fun `eliminar pide confirmación con lo que se pierde, borra la sesión y sale`() = runTest(despachador) {
        abrirBenelli()
        evento(8, TipoEvento.DTC_LECTURA, "Lectura de códigos")
        evento(14, TipoEvento.CAPTURA, "Captura rápida", adjunto = "/datos/taller/1/captura-cap-1.csv")
        val vm = vm()

        vm.pedirEliminacion()
        assertEquals(DialogoSesion.ELIMINAR, vm.estado.value.ui.dialogo)
        assertEquals(LoQueSeBorra(eventos = 2, adjuntos = 1), vm.estado.value.loQueSeBorra)
        assertEquals(3, AvisosEliminacion.de(vm.estado.value.loQueSeBorra).size)

        vm.confirmarEliminacion()
        // El borrado de la carpeta de adjuntos corre en Dispatchers.IO: se espera en tiempo real.
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(5_000) { vm.salir.first() } }

        assertNull(taller.repositorio.sesion(sesionId))
        assertTrue(taller.repositorio.eventos.value.isEmpty())
        assertTrue(vm.estado.value.eliminada)
    }

    @Test
    fun `cancelar el diálogo no borra nada`() = runTest(despachador) {
        abrirBenelli()
        val vm = vm()
        vm.pedirEliminacion()

        vm.cancelarDialogo()

        assertNull(vm.estado.value.ui.dialogo)
        assertTrue(taller.repositorio.sesion(sesionId) != null)
    }

    @Test
    fun `la nota se agrega a la línea de tiempo y limpia el borrador`() = runTest(despachador) {
        abrirBenelli()
        val vm = vm()
        vm.pedirNota()
        vm.cambiarNota("Multímetro: señal 0,1 V cerrado y 0,8 V a fondo")

        vm.guardarNota()

        val nota = vm.estado.value.eventos.single()
        assertEquals(TipoEvento.NOTA, nota.tipo)
        assertEquals("Multímetro: señal 0,1 V cerrado y 0,8 V a fondo", nota.resumen)
        assertEquals("", vm.estado.value.ui.nota)
        assertEquals("Nota agregada a la sesión", vm.estado.value.ui.mensaje)
    }

    @Test
    fun `la instantánea guarda las lecturas actuales`() = runTest(despachador) {
        abrirBenelli()
        taller.entorno.lecturasActuales = mapOf("05" to ObdReading("05", 32.0, "°C", timestamp = SEP25_20H))
        val vm = vm()

        vm.tomarInstantanea()

        assertEquals(TipoEvento.INSTANTANEA_SENSORES, vm.estado.value.eventos.single().tipo)
    }

    @Test
    fun `con otro vehículo activo no se agrega nada a esta sesión`() = runTest(despachador) {
        abrirBenelli()
        taller.entorno.vehiculoFlujo.value = MAZDA
        val vm = vm()
        vm.cambiarNota("no debería guardarse")

        vm.guardarNota()

        assertTrue(vm.estado.value.agregar is DisponibilidadAgregar.OtroVehiculo)
        assertTrue(taller.repositorio.eventos.value.isEmpty())
        assertEquals("25 sep 2026", vm.estado.value.subtitulo)
    }

    @Test
    fun `el adaptador conectado habilita las acciones que lo requieren`() = runTest(despachador) {
        abrirBenelli()
        val vm = vm()
        assertTrue(vm.estado.value.adaptadorConectado)

        taller.entorno.conexionFlujo.value = ConnectionState.Disconnected

        assertFalse(vm.estado.value.adaptadorConectado)
    }

    @Test
    fun `una sesión que no existe se marca eliminada para volver`() = runTest(despachador) {
        assertTrue(vm(id = 99).estado.value.eliminada)
    }

    @Test
    fun `la hoja y los eventos abiertos son estado de la pantalla`() = runTest(despachador) {
        abrirBenelli()
        val id = evento(8, TipoEvento.DTC_LECTURA, "Lectura de códigos")
        val vm = vm()

        vm.mostrarHojaAgregar(true)
        vm.alternarEvento(id)
        vm.alternarComparacion()

        val ui = vm.estado.value.ui
        assertTrue(ui.hojaAgregar)
        assertEquals(setOf(id), ui.eventosAbiertos)
        assertFalse(ui.comparacionExpandida)
        vm.pedirNota()
        assertFalse("pedir la nota cierra la hoja", vm.estado.value.ui.hojaAgregar)
    }
}
