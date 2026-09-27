package com.revscope.feature.dtc

import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.intelligence.dtc.DtcExplanation
import com.revscope.core.obd.diagnostics.AvisoLecturaDtc
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.taller.dtc.BaseConocimientoDtc
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        coEvery { registro.sesionAbierta() } returns null
        coEvery { registro.anotarLecturaDtc(any(), any()) } returns null
        coEvery { registro.marcarPaso(any(), any()) } returns null
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val guias = BaseConocimientoDtc { GUIA_JSON }

    private fun nuevoVm(): DtcViewModel = DtcViewModel(orchestrator, mockk<PidRegistry>(relaxed = true), registro, guias)

    private fun vmConCodigos(): DtcViewModel = nuevoVm().also { it.readDtcCodes(connectionVm) }

    private fun sesion(pasos: Set<String> = emptySet()) =
        SesionTaller(id = 4, vehiculoId = 7, inicio = 1, titulo = "Se apaga al soltar el acelerador", pasosMarcados = pasos)

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
    fun `la lectura adjunta la guia local de cada codigo y decodifica los que no estan`() {
        coEvery { connectionVm.leerDtcCompleto(any(), any()) } returns Result.success(scan("P0122", "P1234"))

        val vm = vmConCodigos()

        val codigos = (vm.state.value as DtcUiState.HasCodes).codes
        assertEquals(listOf("P0122", "P1234"), codigos.map { it.codigo })
        val p0122 = codigos.first()
        assertEquals("Sensor de posición del acelerador «A»: circuito con señal baja", p0122.guia?.titulo)
        assertEquals("Barrido guiado del TPS", p0122.guia?.verificaciones?.get(1)?.paso)
        assertNull(p0122.sinGuia)
        assertNull(codigos[1].guia)
        assertTrue(codigos[1].sinGuia!!.contains("Código del fabricante"))
    }

    @Test
    fun `un codigo activo y pendiente a la vez es una sola tarjeta con sus dos modos`() {
        coEvery { connectionVm.leerDtcCompleto(any(), any()) } returns Result.success(
            scan("P0122").copy(pendientes = listOf(DtcCode("P0122", DtcMode.Pending))),
        )

        val codigos = (vmConCodigos().state.value as DtcUiState.HasCodes).codes

        assertEquals(1, codigos.size)
        assertEquals(listOf(DtcMode.Active, DtcMode.Pending), codigos.single().modos)
    }

    @Test
    fun `con un solo codigo su guia se abre sola`() {
        assertEquals(setOf("P0122"), vmConCodigos().detalle.value.guiasAbiertas)
    }

    @Test
    fun `marcar una casilla con sesion abierta la guarda en la sesion`() {
        coEvery { registro.sesionAbierta() } returns sesion()
        coEvery { registro.marcarPaso("P0122#2", true) } returns sesion(setOf("P0122#2"))
        val vm = vmConCodigos()

        vm.marcarPaso("P0122#2", marcado = true)

        coVerify(exactly = 1) { registro.marcarPaso("P0122#2", true) }
        assertEquals(setOf("P0122#2"), vm.detalle.value.pasosMarcados)
        assertEquals(
            SesionDtcUi.Abierta("Se apaga al soltar el acelerador", setOf("P0122#2")),
            vm.detalle.value.sesion,
        )
    }

    @Test
    fun `sin sesion la casilla se recuerda en pantalla y pasa a la sesion nueva al guardar la lectura`() {
        coEvery { registro.abrirSesion(any()) } returns Result.success(sesion())
        coEvery { registro.anotarLecturaDtc(any(), any()) } returnsMany listOf(null, 11L)
        coEvery { registro.marcarPaso("P0122#1", true) } returns sesion(setOf("P0122#1"))
        val vm = vmConCodigos()

        vm.marcarPaso("P0122#1", marcado = true)
        coVerify(exactly = 0) { registro.marcarPaso(any(), any()) }
        assertEquals(setOf("P0122#1"), vm.detalle.value.pasosMarcados)

        vm.guardarEnSesionNueva()

        coVerify(exactly = 1) { registro.abrirSesion(solicitudDesdeLectura(scan("P0122"))) }
        coVerify(exactly = 2) { registro.anotarLecturaDtc(scan("P0122"), any()) }
        coVerify(exactly = 1) { registro.marcarPaso("P0122#1", true) }
        assertTrue(vm.detalle.value.lecturaEnSesion)
        assertEquals(SesionDtcUi.Abierta("Se apaga al soltar el acelerador", setOf("P0122#1")), vm.detalle.value.sesion)
    }

    @Test
    fun `guardar en sesion sin vehiculo activo avisa el motivo`() {
        coEvery { registro.abrirSesion(any()) } returns
            Result.failure(IllegalStateException("Elige el vehículo activo antes de abrir una sesión"))
        val vm = vmConCodigos()

        vm.guardarEnSesionNueva()

        assertEquals("Elige el vehículo activo antes de abrir una sesión", vm.detalle.value.mensaje)
        assertEquals(SesionDtcUi.Ninguna, vm.detalle.value.sesion)
    }

    @Test
    fun `la lectura anotada en la sesion avisa que el freeze frame ya quedo guardado`() {
        detenido()
        coEvery { registro.sesionAbierta() } returns sesion()
        coEvery { registro.anotarLecturaDtc(any(), any()) } returns 9L
        val vm = vmConCodigos()

        vm.solicitarBorrado(connectionVm, ahora)

        assertTrue(vm.detalle.value.lecturaEnSesion)
        assertTrue(vm.confirmacionBorrado.value!!.freezeFrameEnSesion)
        assertTrue(avisosBorradoDtc(freezeFrameEnSesion = true)[1].contains("ya quedó guardado en la sesión"))
    }

    @Test
    fun `el rechazo 7F 04 22 muestra por que la ECU no borro`() {
        detenido()
        coEvery { connectionVm.borrarDtcConRelectura(any()) } returns Result.success(
            BorradoDtc(respuestaCruda = "7F 04 22", rechazadoPorCondiciones = true, antes = scan("P0122"), despues = scan("P0122")),
        )
        val vm = vmConCodigos()
        vm.solicitarBorrado(connectionVm, ahora)

        vm.confirmarBorrado(connectionVm, declaraDetenido = false, ahoraMs = ahora)

        val resultado = (vm.state.value as DtcUiState.Borrado).resultado
        assertTrue(resultado.rechazadoPorEcu)
        assertEquals(
            "La ECU rechazó el borrado (7F 04 22: condiciones no correctas). Muchas ECU solo borran con el motor " +
                "apagado: apaga el motor, deja el contacto puesto y vuelve a intentarlo. Los códigos siguen guardados.",
            textoResultadoBorrado(resultado),
        )
    }

    @Test
    fun `con velocidad mayor a cero el borrado queda deshabilitado con el motivo`() {
        val vm = vmConCodigos()
        vm.observarVelocidad(connectionVm) { ahora }

        readings.value = mapOf("0D" to ObdReading("0D", 35.0, "km/h", timestamp = ahora - 200))

        assertFalse(DtcPantallaUi(vm.state.value, vm.detalle.value).puedeBorrar)
        assertEquals("El vehículo va a 35 km/h. Detente antes de borrar los códigos.", vm.detalle.value.bloqueoBorrado)

        readings.value = mapOf("0D" to ObdReading("0D", 0.0, "km/h", timestamp = ahora - 200))

        assertTrue(DtcPantallaUi(vm.state.value, vm.detalle.value).puedeBorrar)
    }

    @Test
    fun `explicar con IA es opcional y solo consulta el codigo pedido`() {
        coEvery { orchestrator.explainDtc("P0122", any(), any()) } returns
            DtcExplanation("P0122", "Configura un proveedor", source = "no_key")
        val vm = vmConCodigos()
        coVerify(exactly = 0) { orchestrator.explainDtc(any(), any(), any()) }

        vm.explicarConIa("P0122", connectionVm)

        val codigo = (vm.state.value as DtcUiState.HasCodes).codes.single()
        assertEquals(ExplicacionIa.Lista("Configura un proveedor", faltaConfigurar = true), codigo.explicacion)
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

    @Test
    fun `una lectura sin respuesta al 03 avisa y no muestra sin codigos`() {
        coEvery { connectionVm.leerDtcCompleto(any(), any()) } returns
            Result.success(scan().copy(serviciosFallidos = setOf(DtcServicio.ACTIVOS)))

        val vm = vmConCodigos()

        assertEquals(AvisoLecturaDtc.ACTIVOS_SIN_RESPUESTA, vm.detalle.value.avisoLectura)
        assertNull(vm.detalle.value.sinCodigos)
    }

    @Test
    fun `una lectura completa sin codigos lo dice sin aviso`() {
        coEvery { connectionVm.leerDtcCompleto(any(), any()) } returns Result.success(scan())

        val vm = vmConCodigos()

        assertNull(vm.detalle.value.avisoLectura)
        assertEquals(SIN_CODIGOS_LECTURA_COMPLETA, vm.detalle.value.sinCodigos)
    }
}
