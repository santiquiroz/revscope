package com.revscope.feature.dtc

import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DtcViewModelTest {

    private val ahora = 50_000L
    private val readings = MutableStateFlow<Map<String, ObdReading>>(emptyMap())
    private val connectionVm = mockk<ConnectionViewModel>()
    private val orchestrator = mockk<IntelligenceOrchestrator>(relaxed = true)
    private val registro = mockk<RegistroTaller>(relaxed = true)

    private fun scan(vararg codes: String) = DtcScan(
        activos = codes.map { DtcCode(it, DtcMode.Active) },
        pendientes = emptyList(),
        permanentes = emptyList(),
        milEncendida = codes.isNotEmpty(),
        conteoSegunEcu = codes.size,
        freezeFrame = null,
        crudo = emptyMap(),
        errores = emptyList(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { connectionVm.readings } returns readings
        coEvery { connectionVm.leerDtcCompleto(any(), any()) } returns Result.success(scan("P0122"))
        coEvery { connectionVm.borrarDtcConRelectura(any()) } returns Result.success(
            BorradoDtc(respuestaCruda = "44", rechazadoPorCondiciones = false, antes = scan("P0122"), despues = scan()),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vmConCodigos(): DtcViewModel =
        DtcViewModel(orchestrator, mockk<PidRegistry>(relaxed = true), registro).also { it.readDtcCodes(connectionVm) }

    private fun detenido() {
        readings.value = mapOf("0D" to ObdReading("0D", 0.0, "km/h", timestamp = ahora - 300))
    }

    @Test
    fun `leer codigos anota la lectura en la sesion del taller`() {
        vmConCodigos()

        coVerify(exactly = 1) { registro.anotarLecturaDtc(scan("P0122")) }
    }

    @Test
    fun `borrar anota antes y despues en la sesion del taller y pedirlo sin confirmar no anota nada`() {
        detenido()
        val vm = vmConCodigos()
        vm.solicitarBorrado(connectionVm, ahora)
        coVerify(exactly = 0) { registro.anotarBorradoDtc(any()) }

        vm.confirmarBorrado(connectionVm, declaraDetenido = false, ahoraMs = ahora)

        coVerify(exactly = 1) {
            registro.anotarBorradoDtc(
                BorradoDtc(respuestaCruda = "44", rechazadoPorCondiciones = false, antes = scan("P0122"), despues = scan()),
            )
        }
    }

    @Test
    fun `pedir el borrado solo abre la confirmacion y no toca la ECU`() {
        detenido()
        val vm = vmConCodigos()

        vm.solicitarBorrado(connectionVm, ahora)

        assertEquals(ConfirmacionBorradoDtc(listOf("P0122"), rechazo = null), vm.confirmacionBorrado.value)
        coVerify(exactly = 0) { connectionVm.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `cancelar cierra la confirmacion sin borrar`() {
        detenido()
        val vm = vmConCodigos()
        vm.solicitarBorrado(connectionVm, ahora)

        vm.cancelarBorrado()

        assertNull(vm.confirmacionBorrado.value)
        coVerify(exactly = 0) { connectionVm.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `confirmar sin haber pedido el borrado no hace nada`() {
        detenido()
        val vm = vmConCodigos()

        vm.confirmarBorrado(connectionVm, declaraDetenido = true, ahoraMs = ahora)

        coVerify(exactly = 0) { connectionVm.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `confirmado y detenido borra con relectura y expone antes y despues`() {
        detenido()
        val vm = vmConCodigos()
        vm.solicitarBorrado(connectionVm, ahora)

        vm.confirmarBorrado(connectionVm, declaraDetenido = false, ahoraMs = ahora)

        coVerify(exactly = 1) { connectionVm.borrarDtcConRelectura(any()) }
        assertEquals(
            DtcUiState.Borrado(ResultadoBorradoUi(antes = listOf("P0122"), despues = emptyList(), rechazadoPorEcu = false)),
            vm.state.value,
        )
        assertNull(vm.confirmacionBorrado.value)
    }

    @Test
    fun `en movimiento no borra aunque se confirme`() {
        readings.value = mapOf("0D" to ObdReading("0D", 40.0, "km/h", timestamp = ahora - 100))
        val vm = vmConCodigos()
        vm.solicitarBorrado(connectionVm, ahora)

        vm.confirmarBorrado(connectionVm, declaraDetenido = true, ahoraMs = ahora)

        coVerify(exactly = 0) { connectionVm.borrarDtcConRelectura(any()) }
        assertEquals(RechazoBorradoDtc.EnMovimiento(40), vm.confirmacionBorrado.value?.rechazo)
    }

    @Test
    fun `sin velocidad reciente exige declarar que esta detenido`() {
        val vm = vmConCodigos()
        vm.solicitarBorrado(connectionVm, ahora)

        vm.confirmarBorrado(connectionVm, declaraDetenido = false, ahoraMs = ahora)
        coVerify(exactly = 0) { connectionVm.borrarDtcConRelectura(any()) }

        vm.confirmarBorrado(connectionVm, declaraDetenido = true, ahoraMs = ahora)
        coVerify(exactly = 1) { connectionVm.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `el texto del resultado dice si la falla sigue o si la ECU rechazo`() {
        val sigue = ResultadoBorradoUi(antes = listOf("P0122"), despues = listOf("P0122"), rechazadoPorEcu = false)
        val rechazado = sigue.copy(rechazadoPorEcu = true)
        val limpio = sigue.copy(despues = emptyList())

        assert(textoResultadoBorrado(sigue).contains("sigue presente"))
        assert(textoResultadoBorrado(rechazado).contains("7F 04 22"))
        assertEquals(
            "Códigos borrados. Antes: P0122. Después: sin códigos activos.",
            textoResultadoBorrado(limpio),
        )
    }
}
