package com.revscope.feature.workshop.taller

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TipoEvento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TallerHubViewModelTest {

    private val despachador = UnconfinedTestDispatcher()
    private val repositorio = RepositorioEnMemoria()
    private val entorno = EntornoDePrueba()
    private val hora = 60_000L * 60

    @Before
    fun setUp() = Dispatchers.setMain(despachador)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vmActivo(): TallerHubViewModel =
        TallerHubViewModel(repositorio, entorno).also { vm -> backgroundScope.launch { vm.estado.collect {} } }

    private fun sesion(id: Long, inicio: Long, cierre: Long? = null, vehiculo: Long = BENELLI.id) =
        SesionTaller(id = id, vehiculoId = vehiculo, inicio = inicio, cierre = cierre, titulo = "Sesión $id")

    private fun evento(id: Long, instante: Long, tipo: TipoEvento, titulo: String, payload: String = "{}") =
        EventoTaller(id = id, sesionId = 1, instante = instante, tipo = tipo, titulo = titulo, payloadJson = payload)

    @Test
    fun `sin sesión abierta ofrece iniciar y muestra el adaptador conectado`() = runTest(despachador) {
        val vm = vmActivo()

        val estado = vm.estado.value
        assertFalse(estado.cargando)
        assertEquals("Benelli TNT 150i", estado.vehiculo)
        assertTrue(estado.esMoto)
        assertNull(estado.sesionAbierta)
        assertEquals(EstadoAdaptador.Conectado("vLinker FS"), estado.adaptador)
    }

    @Test
    fun `con sesión abierta resume códigos, eventos y el último evento por hora`() = runTest(despachador) {
        repositorio.sesiones.value = listOf(sesion(1, SEP25_20H))
        repositorio.eventos.value = listOf(
            evento(2, SEP25_20H + hora / 2, TipoEvento.CHEQUEO, "Chequeo de salud"),
            evento(1, SEP25_20H + hora / 6, TipoEvento.DTC_LECTURA, "Lectura de códigos", """{"activos":["P0122"]}"""),
        )

        val tarjeta = vmActivo().estado.value.sesionAbierta!!

        assertEquals(1L, tarjeta.id)
        assertEquals(listOf("P0122"), tarjeta.codigos)
        assertEquals(2, tarjeta.eventos)
        assertEquals("20:30 · Chequeo de salud", tarjeta.ultimoEvento)
        assertEquals("25 sep 2026, 20:00", tarjeta.desde)
    }

    @Test
    fun `las anteriores son las cerradas del vehículo, de la más nueva a la más vieja, con ver todas`() = runTest(despachador) {
        val cerradas = (1L..5L).map { sesion(it, inicio = SEP25_20H - it * 24 * hora, cierre = SEP25_20H - it * 24 * hora + hora) }
        repositorio.sesiones.value = cerradas + sesion(6, SEP25_20H) + sesion(7, SEP25_20H - hora, SEP25_20H, vehiculo = MAZDA.id)
        val vm = vmActivo()

        assertEquals(listOf(1L, 2L, 3L), vm.estado.value.anterioresVisibles.map { it.id })
        assertTrue(vm.estado.value.hayMasAnteriores)
        assertEquals("24 sep 2026, 20:00 – 21:00", vm.estado.value.anteriores.first().fecha)

        vm.alternarVerTodas()

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), vm.estado.value.anterioresVisibles.map { it.id })
    }

    @Test
    fun `sin vehículo activo no hay sesiones y el adaptador sigue su estado`() = runTest(despachador) {
        entorno.vehiculoFlujo.value = null
        entorno.conexionFlujo.value = ConnectionState.Disconnected
        repositorio.sesiones.value = listOf(sesion(1, SEP25_20H))

        val estado = vmActivo().estado.value

        assertNull(estado.vehiculo)
        assertNull(estado.sesionAbierta)
        assertEquals(EstadoAdaptador.Desconectado, estado.adaptador)
    }

    @Test
    fun `cambiar de vehículo cambia la sesión abierta mostrada`() = runTest(despachador) {
        repositorio.sesiones.value = listOf(sesion(1, SEP25_20H), sesion(2, SEP25_20H, vehiculo = MAZDA.id))
        val vm = vmActivo()

        entorno.vehiculoFlujo.value = MAZDA

        assertEquals(2L, vm.estado.value.sesionAbierta?.id)
        assertFalse(vm.estado.value.esMoto)
    }

    @Test
    fun `la rejilla pasa a una columna con letra grande`() {
        assertEquals(2, HerramientasTaller.columnas(anchoDisponibleDp = 328f, escalaLetra = 1.0f))
        assertEquals(2, HerramientasTaller.columnas(anchoDisponibleDp = 328f, escalaLetra = 1.3f))
        assertEquals(1, HerramientasTaller.columnas(anchoDisponibleDp = 328f, escalaLetra = 2.0f))
        assertEquals(1, HerramientasTaller.columnas(anchoDisponibleDp = 380f, escalaLetra = 2.0f))
    }

    @Test
    fun `las herramientas que aún no existen no aparecen en la rejilla`() {
        val sinPruebas = HerramientasTaller.rapidas(AccionesHub()).map { it.titulo }
        val conPruebas = HerramientasTaller.rapidas(AccionesHub(onPruebasGuiadas = {}, onMultimetro = {})).map { it.titulo }

        assertEquals(listOf("Códigos", "Sensores y captura", "Chequeo de salud"), sinPruebas)
        assertEquals(listOf("Códigos", "Pruebas guiadas", "Multímetro", "Sensores y captura", "Chequeo de salud"), conPruebas)
    }
}
