package com.revscope.feature.workshop.taller.multimetro

import androidx.lifecycle.SavedStateHandle
import com.revscope.core.obd.taller.multimetro.AsistenteMultimetro
import com.revscope.core.obd.taller.multimetro.CondicionesMultimetro
import com.revscope.core.obd.taller.multimetro.EstadoCelda
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.ResultadoEcu
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.feature.workshop.taller.BENELLI
import com.revscope.feature.workshop.taller.TallerDePrueba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
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
class MultimetroViewModelTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val despachador = UnconfinedTestDispatcher()
    private lateinit var taller: TallerDePrueba

    @Before
    fun setUp() {
        Dispatchers.setMain(despachador)
        taller = TallerDePrueba(carpeta.root)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(sensor: String? = null) = MultimetroViewModel(
        SavedStateHandle(mapOf(MultimetroViewModel.ARG_SENSOR to sensor)),
        AsistenteMultimetro(taller.repositorio, { taller.entorno.vehiculoFlujo.value }, taller.registro),
        taller.registro,
    )

    private fun MultimetroViewModel.escribirCaso() =
        MultimetroBenelli.textosCaso.forEach { (celda, texto) -> cambiarValor(celda.first, celda.second, texto) }

    private fun celda(ui: MultimetroUi, funcion: FuncionCable, condicion: String) =
        ui.plantilla!!.filas.single { it.funcion == funcion }.celdas.single { it.condicion == condicion }

    @Test
    fun `con el modelo usa los colores de su cableado y sin modelo la genérica sin colores`() = runTest(despachador) {
        taller.repositorio.modelos[BENELLI.claveModelo!!] = MultimetroBenelli.conocimiento

        val conModelo = vm().estado.value

        assertEquals(listOf("Rojo", "Negro", "Verde-amarillo"), conModelo.plantilla!!.filas.map { it.color })
        assertTrue(conModelo.plantilla!!.origenColores.startsWith("Colores: Medición del dueño"))

        taller.repositorio.modelos.clear()
        val generica = vm().estado.value
        assertTrue(generica.plantilla!!.filas.all { it.color == null })
        assertEquals(MapeoMultimetro.SIN_COLORES, generica.plantilla!!.origenColores)
    }

    @Test
    fun `al escribir el caso cada celda tiene su veredicto y el combinado dice señal baja en todo el recorrido`() = runTest(despachador) {
        val vm = vm()

        vm.escribirCaso()

        val ui = vm.estado.value
        assertEquals("Referencia y masa correctas; señal baja en todo el recorrido", ui.veredicto!!.titulo)
        assertEquals(Veredicto.FALLA, ui.veredicto!!.veredicto)
        assertEquals(EstadoCelda.DENTRO, celda(ui, FuncionCable.REF_5V, CondicionesMultimetro.FONDO).estado)
        assertEquals(EstadoCelda.BAJO, celda(ui, FuncionCable.SENAL, CondicionesMultimetro.MEDIO).estado)
    }

    @Test
    fun `un texto que no es número se marca y no entra en el veredicto`() = runTest(despachador) {
        val vm = vm()

        vm.cambiarValor(FuncionCable.SENAL, CondicionesMultimetro.CERRADO, "0,1x")

        val ui = vm.estado.value
        assertTrue(celda(ui, FuncionCable.SENAL, CondicionesMultimetro.CERRADO).invalido)
        assertNull(ui.veredicto)
        assertFalse(ui.guardar.habilitado)
    }

    @Test
    fun `con la sesión abierta y un barrido del TPS compara con la ECU y guarda la tabla en la sesión`() = runTest(despachador) {
        taller.registro.abrirSesion(SolicitudSesion(titulo = "P0122"))
        taller.registro.anotar(MultimetroBenelli.barridoTps())
        val vm = vm()
        vm.escribirCaso()
        assertEquals(ResultadoEcu.COINCIDE, vm.estado.value.veredicto!!.ecu!!.resultado)
        assertEquals("Guardar en la sesión", vm.estado.value.guardar.texto)

        vm.guardarEnSesion()

        val medicion = taller.repositorio.eventos.value.single { it.tipo == TipoEvento.MEDICION_MULTIMETRO }
        assertEquals(Veredicto.FALLA, medicion.veredicto)
        assertTrue(medicion.resumen.contains("la ECU recibe lo mismo"))
        assertEquals(9, JSONObject(medicion.payloadJson).getJSONArray("lecturas").length())
        assertEquals("Medición guardada en la sesión", vm.estado.value.mensaje)
        assertEquals(1, taller.repositorio.sesiones.value.size)
    }

    @Test
    fun `sin sesión abierta guardar abre una nueva para el vehículo`() = runTest(despachador) {
        val vm = vm()
        vm.cambiarValor(FuncionCable.REF_5V, CondicionesMultimetro.CERRADO, "4.98")
        assertEquals("Guardar en una sesión nueva", vm.estado.value.guardar.texto)

        vm.guardarEnSesion()

        val sesion = taller.repositorio.sesiones.value.single()
        assertEquals("Multímetro: TPS de 3 cables", sesion.titulo)
        assertEquals(1, taller.repositorio.eventos.value.count { it.tipo == TipoEvento.MEDICION_MULTIMETRO })
        assertEquals("Guardar en la sesión", vm.estado.value.guardar.texto)
    }

    @Test
    fun `editar un color y guardarlo lo deja en el cableado del modelo`() = runTest(despachador) {
        taller.repositorio.modelos[BENELLI.claveModelo!!] = MultimetroBenelli.conocimiento
        val vm = vm()

        vm.pedirColor(FuncionCable.SENAL)
        assertEquals("Verde-amarillo", vm.estado.value.dialogoColor!!.texto)
        vm.cambiarColor("Verde")
        vm.aceptarColor()
        assertTrue(vm.estado.value.colores.puedeGuardar)
        assertEquals(MapeoMultimetro.COLORES_EDITADOS, vm.estado.value.plantilla!!.origenColores)

        vm.guardarColores()

        val cableado = taller.repositorio.modelos.getValue(BENELLI.claveModelo!!).cableado.single()
        assertEquals(listOf("Rojo", "Negro", "Verde"), cableado.cables.map { it.color })
        assertFalse(vm.estado.value.colores.editados)
        assertEquals("Colores guardados en la plantilla de Benelli TNT 150i (2022)", vm.estado.value.mensaje)
    }

    @Test
    fun `sin modelo de referencia los colores editados no se pueden guardar en una plantilla`() = runTest(despachador) {
        val vm = vm()

        vm.pedirColor(FuncionCable.REF_5V)
        vm.cambiarColor("Rojo")
        vm.aceptarColor()

        assertTrue(vm.estado.value.colores.editados)
        assertFalse(vm.estado.value.colores.puedeGuardar)
        assertEquals("Rojo", vm.estado.value.plantilla!!.filas.first().color)
    }

    @Test
    fun `el sensor llega por argumento y se puede cambiar, empezando de cero`() = runTest(despachador) {
        val vm = vm(sensor = "BATERIA")
        assertEquals("Batería y carga", vm.estado.value.plantilla!!.titulo)
        assertTrue(vm.estado.value.colores.cables.isEmpty())
        vm.cambiarValor(FuncionCable.ALIMENTACION_12V, CondicionesMultimetro.CONTACTO, "12,2")

        vm.elegirSensor(SensorMultimetro.INYECTOR)

        val ui = vm.estado.value
        assertEquals(SensorMultimetro.INYECTOR, ui.sensor)
        assertEquals("Ω", ui.plantilla!!.unidad)
        assertNull(ui.veredicto)
    }

    @Test
    fun `la tabla cabe a 360 dp con letra normal y pasa a tarjetas desde 1,3`() {
        val anchoContenido = 360f - 32f

        assertTrue(DisposicionMultimetro.usaTabla(anchoContenido, 1.0f, 3))
        assertFalse(DisposicionMultimetro.usaTabla(anchoContenido, 1.3f, 3))
        assertFalse(DisposicionMultimetro.usaTabla(412f - 32f, 2.0f, 3))
        assertTrue(DisposicionMultimetro.usaTabla(412f - 32f, 1.0f, 3))
        assertTrue(DisposicionMultimetro.usaTabla(anchoContenido, 1.3f, 1))
    }

    @Test
    fun `el lector decimal acepta coma o punto y rechaza lo demás`() {
        assertEquals(0.35, LectorDecimal.leer(" 0,35 ")!!, 1e-9)
        assertEquals(12.6, LectorDecimal.leer("12.6")!!, 1e-9)
        assertEquals(-0.01, LectorDecimal.leer("-0,01")!!, 1e-9)
        assertNull(LectorDecimal.leer("1,2,3"))
        assertNull(LectorDecimal.leer(""))
    }
}
