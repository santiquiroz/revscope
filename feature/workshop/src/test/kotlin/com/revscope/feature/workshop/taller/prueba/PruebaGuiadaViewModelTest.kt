package com.revscope.feature.workshop.taller.prueba

import androidx.lifecycle.SavedStateHandle
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.grafica.FuenteVref
import com.revscope.core.obd.taller.grafica.PreferenciasVref
import com.revscope.core.obd.taller.pruebas.AnunciadorTaller
import com.revscope.core.obd.taller.pruebas.CapturaPrueba
import com.revscope.core.obd.taller.pruebas.ControladorPruebaGuiada
import com.revscope.core.obd.taller.pruebas.DesfaseVoltaje
import com.revscope.core.obd.taller.pruebas.EnlacePrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.FuenteDesfase
import com.revscope.core.obd.taller.pruebas.PreferenciasDesfase
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.telemetry.captura.ConfigCaptura
import com.revscope.core.obd.telemetry.captura.InicioCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.feature.workshop.taller.BENELLI
import com.revscope.feature.workshop.taller.TallerDePrueba
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PruebaGuiadaViewModelTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val despachador = UnconfinedTestDispatcher()
    private var relojMs = 0L
    private val captura = CapturaQuieta { relojMs }
    private val enlace = EnlaceQuieto()
    private val preferencias = PreferenciasEnMemoria()
    private val desfases = DesfasesEnMemoria()
    private lateinit var taller: TallerDePrueba
    private lateinit var controlador: ControladorPruebaGuiada

    @Before
    fun setUp() {
        Dispatchers.setMain(despachador)
        taller = TallerDePrueba(carpeta.root)
        controlador = ControladorPruebaGuiada(
            captura, enlace, taller.registro, taller.repositorio, { BENELLI }, AnunciadorTaller { }, CoroutineScope(despachador), { relojMs },
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(tipo: TipoPrueba? = TipoPrueba.TPS_BARRIDO) = PruebaGuiadaViewModel(
        SavedStateHandle(tipo?.let { mapOf(PruebaGuiadaViewModel.ARG_TIPO to it.name) } ?: emptyMap()),
        controlador, enlace, taller.entorno, FuenteVref(preferencias, taller.repositorio) { BENELLI }, { emptyList() },
        taller.repositorio, { BENELLI }, taller.registro, FuenteDesfase(desfases) { BENELLI },
    ).also { v -> CoroutineScope(despachador).launch { v.estado.collect { } } }

    @Test
    fun `con la prueba del argumento muestra la preparacion con las precondiciones evaluadas en vivo`() {
        val vm = vm()

        val fase = vm.estado.value.fase as FasePantalla.Preparacion
        assertTrue(fase.listas)
        assertEquals("Adaptador conectado", fase.precondiciones.first().texto)
    }

    @Test
    fun `empezar arranca la prueba con la referencia guardada del vehiculo`() {
        preferencias.guardadas[BENELLI.id] = 4.9
        val vm = vm()

        vm.empezar()

        val paso = (vm.estado.value.fase as FasePantalla.Paso).paso
        assertEquals(1 to FasePaso.POSICIONANDO, paso.indice to paso.fase)
        assertEquals(listOf("11"), captura.configs.single().pids)
        assertEquals(4.9, vm.estado.value.vref.voltios, 1e-9)
    }

    @Test
    fun `cancelar sin nada grabado no pregunta y con el paso en curso pide confirmar`() {
        val vm = vm()
        vm.empezar()
        vm.pedirCancelar()
        val sinPreguntar = vm.estado.value.fase

        vm.reintentar()
        vm.avanzar()
        vm.pedirCancelar()
        val dialogo = vm.estado.value.dialogo
        vm.confirmarCancelar()

        assertTrue(sinPreguntar is FasePantalla.Cancelada)
        assertEquals(DialogoPrueba.CANCELAR, dialogo)
        assertNull(vm.estado.value.dialogo)
        assertTrue(vm.estado.value.fase is FasePantalla.Cancelada)
    }

    @Test
    fun `volver desde una prueba elegida en la pantalla regresa a la lista sin salir`() {
        val vm = vm(tipo = null)
        vm.elegir(TipoPrueba.TPS_BARRIDO)
        val preparando = vm.estado.value.fase

        vm.volver()

        assertTrue(preparando is FasePantalla.Preparacion)
        assertTrue(vm.estado.value.fase is FasePantalla.Elegir)
    }

    @Test
    fun `la referencia editada se guarda para el vehiculo y quitarla vuelve a la tipica`() {
        val vm = vm()

        vm.guardarVref(4.95)
        val editada = vm.estado.value.vref
        vm.guardarVref(null)

        assertEquals(4.95, editada.voltios, 1e-9)
        assertEquals("Editado por ti", editada.origen)
        assertEquals("Típico (editable)", vm.estado.value.vref.origen)
        assertTrue(preferencias.guardadas.isEmpty())
    }

    @Test
    fun `calibrar con el multímetro guarda la diferencia contra el adaptador y quitarla vuelve a sin calibrar`() {
        enlace.lecturas = mapOf("VBAT" to ObdReading("VBAT", 12.4, "V"))
        val vm = vm(tipo = TipoPrueba.BATERIA_CARGA)

        vm.pedirDesfase()
        val dialogo = vm.estado.value.dialogo
        val adaptador = vm.estado.value.voltajeAdaptador
        vm.guardarDesfase(12.62)
        val calibrado = vm.estado.value.desfase
        vm.pedirDesfase()
        vm.guardarDesfase(null)

        assertEquals(DialogoPrueba.DESFASE, dialogo)
        assertEquals(12.4, adaptador!!, 1e-9)
        assertEquals(0.22, calibrado.voltios, 1e-9)
        assertEquals(DesfaseVoltaje.ORIGEN_MULTIMETRO, calibrado.origen)
        assertEquals(DesfaseVoltaje.SIN_CALIBRAR, vm.estado.value.desfase)
        assertTrue(desfases.guardados.isEmpty())
        assertNull(vm.estado.value.dialogo)
    }

    @Test
    fun `sin lectura del adaptador no calibra y lo dice`() {
        val vm = vm(tipo = TipoPrueba.BATERIA_CARGA)

        vm.pedirDesfase()
        vm.guardarDesfase(12.6)

        assertEquals(DesfaseVoltaje.SIN_CALIBRAR, vm.estado.value.desfase)
        assertTrue(vm.estado.value.mensaje!!, vm.estado.value.mensaje!!.contains("no se calibró"))
        assertEquals(DialogoPrueba.DESFASE, vm.estado.value.dialogo)
    }

    @Test
    fun `la preparación de la batería muestra el desfase y no la referencia de 5 V`() {
        val vm = vm(tipo = TipoPrueba.BATERIA_CARGA)

        val fase = vm.estado.value.fase as FasePantalla.Preparacion

        assertTrue(fase.usaDesfase)
        assertTrue(!fase.usaVref)
    }

    @Test
    fun `apagar la voz se refleja en la pantalla`() {
        val vm = vm()

        vm.cambiarVoz(false)

        assertEquals(false, vm.estado.value.voz)
    }
}

private class CapturaQuieta(private val reloj: () -> Long) : CapturaPrueba {
    override val ultimoResumen: StateFlow<ResumenCaptura?> = MutableStateFlow(null)
    val configs = mutableListOf<ConfigCaptura>()
    private var inicio: Long? = null

    override suspend fun iniciar(config: ConfigCaptura): Result<InicioCaptura> {
        configs += config
        inicio = reloj()
        return Result.success(InicioCaptura("c", config.pids, emptyList(), listOf(config.pids), emptySet(), config.duracionMaxMs))
    }

    override suspend fun detener(motivo: String): ResumenCaptura? {
        inicio = null
        return null
    }

    override fun activa(): Boolean = inicio != null

    override fun transcurridoMs(): Long? = inicio?.let { reloj() - it }

    override fun muestrasActuales(): List<MuestraCaptura> = emptyList()
}

private class EnlaceQuieto : EnlacePrueba {
    var lecturas: Map<String, ObdReading> = emptyMap()
    override fun conectado() = true
    override fun lecturas(): Map<String, ObdReading> = lecturas
    override fun soportado(pid: String) = pid == "11"
}

private class PreferenciasEnMemoria : PreferenciasVref {
    val guardadas = mutableMapOf<Long?, Double>()

    override suspend fun leer(vehiculoId: Long?): Double? = guardadas[vehiculoId]

    override suspend fun guardar(vehiculoId: Long?, voltios: Double?) {
        if (voltios == null) guardadas.remove(vehiculoId) else guardadas[vehiculoId] = voltios
    }
}

private class DesfasesEnMemoria : PreferenciasDesfase {
    val guardados = mutableMapOf<Long?, DesfaseVoltaje>()

    override suspend fun leer(vehiculoId: Long?): DesfaseVoltaje? = guardados[vehiculoId]

    override suspend fun guardar(vehiculoId: Long?, desfase: DesfaseVoltaje?) {
        if (desfase == null) guardados.remove(vehiculoId) else guardados[vehiculoId] = desfase
    }
}
