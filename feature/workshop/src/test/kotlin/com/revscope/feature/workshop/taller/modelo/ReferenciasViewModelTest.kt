package com.revscope.feature.workshop.taller.modelo

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.BandasTipicas
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.feature.workshop.taller.EntornoDePrueba
import com.revscope.feature.workshop.taller.MAZDA
import com.revscope.feature.workshop.taller.RepositorioEnMemoria
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReferenciasViewModelTest {
    private val despachador = UnconfinedTestDispatcher()
    private lateinit var repositorio: RepositorioReferencias
    private lateinit var viewModel: ReferenciasViewModel

    @Before
    fun preparar() {
        Dispatchers.setMain(despachador)
        repositorio = RepositorioReferencias()
        viewModel = ReferenciasViewModel(repositorio, EntornoDePrueba())
    }

    @After
    fun cerrar() = Dispatchers.resetMain()

    @Test
    fun `editar guarda una banda con origen Editado por ti`() = runTest(despachador) {
        viewModel.editar(ClavesBanda.TPS_CERRADO_V)
        viewModel.cambiarMinimo("0,45")
        viewModel.cambiarMaximo("0,95")

        viewModel.guardar()

        val banda = viewModel.estado.value.bandas.single { it.clave == ClavesBanda.TPS_CERRADO_V }
        assertEquals(0.45, banda.min!!, 0.001)
        assertEquals(0.95, banda.max!!, 0.001)
        assertEquals(OrigenBanda.USUARIO, banda.origen)
        assertEquals("Referencia guardada", viewModel.estado.value.mensaje)
        assertNull(viewModel.estado.value.dialogo)
    }

    @Test
    fun `restablecer elimina la edición y recupera la banda típica`() = runTest(despachador) {
        viewModel.editar(ClavesBanda.TPS_CERRADO_V)
        viewModel.cambiarMinimo("0,5")
        viewModel.cambiarMaximo("0,9")
        viewModel.guardar()
        assertEquals(OrigenBanda.USUARIO, bandaTps().origen)

        viewModel.restablecer(ClavesBanda.TPS_CERRADO_V)

        val banda = bandaTps()
        assertEquals(BandasTipicas.para(VehicleType.MOTORCYCLE).getValue(ClavesBanda.TPS_CERRADO_V), banda)
        assertEquals("Referencia restablecida a Típico", viewModel.estado.value.mensaje)
    }

    @Test
    fun `rechaza límites invertidos sin guardar`() = runTest(despachador) {
        viewModel.editar(ClavesBanda.TPS_CERRADO_V)
        viewModel.cambiarMinimo("2")
        viewModel.cambiarMaximo("1")

        viewModel.guardar()

        assertEquals("El mínimo no puede ser mayor que el máximo", viewModel.estado.value.dialogo?.error)
        assertEquals(OrigenBanda.TIPICO, bandaTps().origen)
    }

    @Test
    fun `rechaza NaN e infinitos sin guardar`() = runTest(despachador) {
        listOf("NaN", "Infinity", "-Infinity").forEach { valor ->
            viewModel.editar(ClavesBanda.TPS_CERRADO_V)
            viewModel.cambiarMinimo(valor)

            viewModel.guardar()

            assertEquals("El mínimo no es un número válido", viewModel.estado.value.dialogo?.error)
            viewModel.cerrarDialogo()
        }
        assertEquals(OrigenBanda.TIPICO, bandaTps().origen)
    }

    @Test
    fun `un reintento lento no reemplaza las referencias del vehículo nuevo`() = runTest(despachador) {
        val espera = CompletableDeferred<Unit>()
        val repo = RepositorioReferencias { clave -> if (clave == ModeloBenelli.conocimiento.clave) espera.await() }
        val entorno = EntornoDePrueba()
        val vm = ReferenciasViewModel(repo, entorno)
        vm.reintentar()

        entorno.vehiculoFlujo.value = MAZDA
        runCurrent()
        espera.complete(Unit)
        runCurrent()

        assertEquals(MAZDA.nombre, vm.estado.value.vehiculo)
        assertNull(vm.estado.value.modelo)
        assertEquals(600.0, vm.estado.value.bandas.single { it.clave == ClavesBanda.MINIMO_RPM }.min!!, 0.001)
    }

    @Test
    fun `un reintento lento no reemplaza el conocimiento del vehículo nuevo`() = runTest(despachador) {
        val espera = CompletableDeferred<Unit>()
        val repo = RepositorioReferencias { clave -> if (clave == ModeloBenelli.conocimiento.clave) espera.await() }
        val entorno = EntornoDePrueba()
        val vm = ConocimientoModeloViewModel(repo, entorno)
        vm.reintentar()

        entorno.vehiculoFlujo.value = MAZDA
        runCurrent()
        espera.complete(Unit)
        runCurrent()

        assertEquals(MAZDA.nombre, vm.estado.value.vehiculo)
        assertNull(vm.estado.value.modelo)
    }

    private fun bandaTps() = viewModel.estado.value.bandas.single { it.clave == ClavesBanda.TPS_CERRADO_V }
}

private class RepositorioReferencias(
    private val base: RepositorioEnMemoria = RepositorioEnMemoria(),
    private val antesDeConocimiento: suspend (String) -> Unit = {},
) : TallerRepository by base {
    private val bandasUsuario = mutableMapOf<String, BandaReferencia>()
    private val conocimiento: ConocimientoModelo = ModeloBenelli.conocimiento

    init {
        base.modelos[conocimiento.clave] = conocimiento
    }

    override suspend fun conocimiento(clave: String): ConocimientoModelo? {
        antesDeConocimiento(clave)
        return conocimiento.takeIf { it.clave == clave }
    }

    override suspend fun guardarBandaUsuario(claveModelo: String, banda: BandaReferencia) {
        check(claveModelo == conocimiento.clave)
        bandasUsuario[banda.clave] = banda
    }

    override suspend fun restablecerBanda(claveModelo: String, claveBanda: String): Boolean =
        claveModelo == conocimiento.clave && bandasUsuario.remove(claveBanda) != null

    override suspend fun bandasResueltas(claveModelo: String?, tipo: VehicleType): Map<String, BandaReferencia> =
        ResolutorBandas.resolverTodas(tipo, conocimiento.bandas + bandasUsuario.values)
}
