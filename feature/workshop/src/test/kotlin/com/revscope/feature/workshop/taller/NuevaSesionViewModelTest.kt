package com.revscope.feature.workshop.taller

import com.revscope.core.obd.taller.sesion.ChequeoRegistrado
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.workshop.MetricasChequeo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class NuevaSesionViewModelTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val despachador = UnconfinedTestDispatcher()
    private val julio11 = 1_783_810_800_000L
    private val chequeos = listOf(
        ChequeoRegistrado(3, BENELLI.id, julio11, emptyList(), MetricasChequeo(dtcs = emptyList(), dtcsLeidos = true)),
        ChequeoRegistrado(4, BENELLI.id, julio11 + 86_400_000L * 30, emptyList(), MetricasChequeo(dtcs = listOf("P0122"), dtcsLeidos = true)),
        ChequeoRegistrado(5, MAZDA.id, julio11, emptyList(), null),
    )
    private lateinit var taller: TallerDePrueba

    @Before
    fun setUp() {
        Dispatchers.setMain(despachador)
        taller = TallerDePrueba(carpeta.root, chequeos)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): Pair<NuevaSesionViewModel, List<Long>> {
        val vm = NuevaSesionViewModel(taller.registro, taller.repositorio, taller.historial, taller.entorno)
        val abiertas = mutableListOf<Long>()
        backgroundScope.launch { vm.sesionAbierta.toList(abiertas) }
        return vm to abiertas
    }

    @Test
    fun `carga los chequeos recientes del vehículo, elige el último y autocompleta el odómetro de la ECU`() = runTest(despachador) {
        taller.entorno.odometro = 12_345.4

        val estado = vm().first.estado.value

        assertEquals("Benelli TNT 150i", estado.vehiculo)
        assertEquals(listOf(4L, 3L), estado.bases.map { it.id })
        assertEquals(4L, estado.baseElegida)
        assertEquals("Códigos: P0122", estado.bases.first().resumen)
        assertEquals("Sin códigos", estado.bases.last().resumen)
        assertEquals("12345", estado.odometro)
        assertTrue(estado.odometroDeEcu)
    }

    @Test
    fun `abrir sin otra sesión la crea con síntomas, texto, odómetro y la base elegida`() = runTest(despachador) {
        val (vm, abiertas) = vm()
        vm.alternarSintoma(Sintoma.NO_SOSTIENE_MINIMO_FRIO)
        vm.alternarSintoma(Sintoma.SE_AHOGA_AL_ACELERAR)
        vm.cambiarSintomasTexto("El TPS se cambió hace unos meses")
        vm.cambiarOdometro("12.345")
        vm.elegirBase(3)

        vm.abrir()

        val creada = taller.repositorio.sesion(abiertas.single())!!
        assertEquals(setOf(Sintoma.NO_SOSTIENE_MINIMO_FRIO, Sintoma.SE_AHOGA_AL_ACELERAR), creada.sintomas)
        assertEquals("El TPS se cambió hace unos meses", creada.sintomasTexto)
        assertEquals(12_345.0, creada.odometroKm!!, 1e-9)
        assertEquals(3L, creada.chequeoBaseId)
    }

    @Test
    fun `abrir con otra sesión abierta ofrece cerrarla y no abre nada todavía`() = runTest(despachador) {
        val anterior = taller.repositorio.abrirSesion(SesionTaller(vehiculoId = BENELLI.id, inicio = julio11, titulo = "Mínimo inestable"))
        val (vm, abiertas) = vm()

        vm.abrir()

        assertEquals(SesionPorCerrar("Mínimo inestable", "11 jul 2026, 18:00"), vm.estado.value.porCerrar)
        assertTrue(abiertas.isEmpty())
        assertTrue(taller.repositorio.sesion(anterior)!!.abierta)
    }

    @Test
    fun `confirmar cierra la anterior y abre la nueva`() = runTest(despachador) {
        val anterior = taller.repositorio.abrirSesion(SesionTaller(vehiculoId = BENELLI.id, inicio = julio11, titulo = "Mínimo inestable"))
        val (vm, abiertas) = vm()
        vm.abrir()

        vm.confirmarCierreYAbrir()

        assertNull(vm.estado.value.porCerrar)
        assertEquals(SEP25_20H, taller.repositorio.sesion(anterior)!!.cierre)
        assertEquals(abiertas.single(), taller.repositorio.sesionAbierta(BENELLI.id)?.id)
    }

    @Test
    fun `cancelar deja la anterior abierta`() = runTest(despachador) {
        val anterior = taller.repositorio.abrirSesion(SesionTaller(vehiculoId = BENELLI.id, inicio = julio11, titulo = "Mínimo inestable"))
        val (vm, abiertas) = vm()
        vm.abrir()

        vm.cancelarCierre()

        assertNull(vm.estado.value.porCerrar)
        assertTrue(abiertas.isEmpty())
        assertEquals(anterior, taller.repositorio.sesionAbierta(BENELLI.id)?.id)
    }

    @Test
    fun `un odómetro con letras no abre la sesión y explica el formato`() = runTest(despachador) {
        val (vm, abiertas) = vm()
        vm.cambiarOdometro("doce mil")

        vm.abrir()

        assertEquals(OdometroTexto.ERROR, vm.estado.value.errorOdometro)
        assertTrue(abiertas.isEmpty())
        vm.cambiarOdometro("12000")
        assertNull(vm.estado.value.errorOdometro)
    }

    @Test
    fun `sin chequeo base elegido la sesión queda sin base`() = runTest(despachador) {
        val (vm, abiertas) = vm()
        vm.elegirBase(null)

        vm.abrir()

        assertNull(taller.repositorio.sesion(abiertas.single())!!.chequeoBaseId)
    }

    @Test
    fun `sin vehículo activo no se puede abrir`() = runTest(despachador) {
        taller.entorno.vehiculoFlujo.value = null
        val (vm, abiertas) = vm()

        vm.abrir()

        assertNull(vm.estado.value.vehiculo)
        assertTrue(!vm.estado.value.puedeAbrir)
        assertTrue(abiertas.isEmpty())
    }

    @Test
    fun `el odómetro acepta puntos de miles y coma decimal`() {
        assertEquals(12_345.6, OdometroTexto.leer("12.345,6").getOrNull()!!, 1e-9)
        assertNull(OdometroTexto.leer("  ").getOrNull())
        assertTrue(OdometroTexto.leer("-5").isFailure)
        assertNotNull(OdometroTexto.leer("0").getOrNull())
    }
}
