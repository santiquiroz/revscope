package com.revscope.core.obd.mcp

import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.taller.dtc.GuiaDePrueba
import com.revscope.core.obd.taller.sesion.AdjuntosTaller
import com.revscope.core.obd.taller.sesion.AnalizadorSesion
import com.revscope.core.obd.taller.sesion.BENELLI
import com.revscope.core.obd.taller.sesion.ChequeoRegistrado
import com.revscope.core.obd.taller.sesion.HistorialChequeosEnMemoria
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SugeridorPruebas
import com.revscope.core.obd.taller.sesion.TallerRepositoryEnMemoria
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.workshop.MetricasChequeo
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SesionTallerToolsTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private var ahora = 1_758_848_000_000L
    private var vehiculo: VehiculoTaller? = BENELLI
    private val repositorio = TallerRepositoryEnMemoria()

    private val base11Julio = ChequeoRegistrado(
        id = 3, vehiculoId = 7, instante = ahora - 76L * 24 * 3_600_000, items = emptyList(),
        metricas = MetricasChequeo(stft = -6.2, ltft = 2.3, voltaje = 13.7, motorEncendido = true, ect = 79.0, dtcs = emptyList()),
    )
    private val historial = HistorialChequeosEnMemoria(listOf(base11Julio))

    private val registro by lazy {
        RegistroTaller(
            repositorio, { vehiculo }, historial, AdjuntosTaller(File(carpeta.root, "taller")),
            PidRegistry(TestPids.load()), { ahora },
        )
    }
    private val analizador by lazy { AnalizadorSesion(repositorio, historial, SugeridorPruebas.desde(GuiaDePrueba.baseReal())) }

    private val iniciar by lazy { IniciarSesionTallerTool(registro, repositorio, { vehiculo }, analizador) }
    private val getSesion by lazy { GetSesionTallerTool(repositorio, { vehiculo }, analizador) }
    private val agregarNota by lazy { AgregarNotaTallerTool(registro) }

    private val scanP0122 = DtcScan(
        activos = listOf(DtcCode("P0122", DtcMode.Active)), pendientes = emptyList(), permanentes = emptyList(),
        milEncendida = true, conteoSegunEcu = 1, freezeFrame = null, crudo = mapOf("03" to "430101220000"), errores = emptyList(),
    )

    private suspend fun abrirBenelli(): JSONObject = JSONObject(
        iniciar.call(
            JSONObject()
                .put("sintomas", JSONArray(listOf("NO_SOSTIENE_MINIMO_FRIO", "SE_APAGA_AL_SOLTAR", "SE_AHOGA_AL_ACELERAR")))
                .put("sintomas_texto", "El TPS se cambió hace unos meses")
                .put("notas", "Moto Benelli TNT 150i 2022"),
        ),
    )

    @Test
    fun `los permisos son lectura para get y control para abrir y anotar`() {
        assertEquals(McpPermiso.LECTURA, getSesion.permiso)
        assertEquals(McpPermiso.CONTROL, iniciar.permiso)
        assertEquals(McpPermiso.CONTROL, agregarNota.permiso)
    }

    @Test
    fun `iniciar abre la sesión con síntomas, base y pruebas sugeridas y anota los síntomas con origen MCP`() = runTest {
        val respuesta = abrirBenelli()

        val sesion = respuesta.getJSONObject("sesion")
        assertTrue(sesion.getBoolean("abierta"))
        assertEquals("No sostiene el mínimo en frío", sesion.getString("titulo"))
        assertEquals(3, sesion.getInt("chequeoBaseId"))
        assertEquals(3, sesion.getJSONArray("sintomas").length())
        val sugeridas = respuesta.getJSONObject("analisis").getJSONArray("pruebasSugeridas")
        assertEquals(
            setOf("PRUEBA:ARRANQUE_FRIO", "PRUEBA:MINIMO_RETORNO", "PRUEBA:TPS_BARRIDO", "MULTIMETRO:TPS"),
            (0 until sugeridas.length()).map { sugeridas.getJSONObject(it).getString("accion") }.toSet(),
        )
        val sintomas = repositorio.todosLosEventos.single { it.tipo == TipoEvento.SINTOMAS }
        assertEquals(OrigenEvento.MCP, sintomas.origen)
    }

    @Test
    fun `iniciar con otra abierta no la pisa salvo cerrar_anterior`() = runTest {
        abrirBenelli()

        val rechazo = JSONObject(iniciar.call(JSONObject().put("titulo", "Segunda")))
        assertTrue(rechazo.getString("error").contains("cerrar_anterior=true"))
        assertEquals(1, rechazo.getJSONObject("sesionAbierta").getInt("id"))

        ahora += 60_000
        val nueva = JSONObject(iniciar.call(JSONObject().put("titulo", "Segunda").put("cerrar_anterior", true)))
        assertEquals(1, nueva.getInt("sesionCerrada"))
        assertEquals("Segunda", nueva.getJSONObject("sesion").getString("titulo"))
    }

    @Test
    fun `iniciar sin vehículo activo o con texto enorme falla con un mensaje claro`() = runTest {
        assertTrue(JSONObject(iniciar.call(JSONObject().put("notas", "x".repeat(2_001)))).getString("error").contains("notas"))
        vehiculo = null

        assertTrue(JSONObject(iniciar.call(JSONObject())).getString("error").contains("vehículo activo"))
        assertTrue(repositorio.todosLosEventos.isEmpty())
    }

    @Test
    fun `iniciar reporta los síntomas que no reconoce`() = runTest {
        val respuesta = JSONObject(iniciar.call(JSONObject().put("sintomas", JSONArray(listOf("TIRONES", "HUMO_AZUL")))))

        assertEquals("HUMO_AZUL", respuesta.getJSONArray("sintomasIgnorados").getString(0))
    }

    @Test
    fun `get sin sesión abierta lo dice y sugiere abrirla`() = runTest {
        val respuesta = JSONObject(getSesion.call(JSONObject()))

        assertFalse(respuesta.getBoolean("abierta"))
        assertTrue(respuesta.getString("mensaje").contains("iniciar_sesion_taller"))
    }

    @Test
    fun `get devuelve la línea de tiempo, los códigos actuales y la comparación del caso Benelli`() = runTest {
        abrirBenelli()
        ahora += 60_000
        registro.anotarLecturaDtc(scanP0122, OrigenEvento.MCP)
        ahora += 60_000
        registro.anotarChequeo(
            12, emptyList(),
            MetricasChequeo(stft = -4.0, ltft = 2.3, voltaje = 14.2, motorEncendido = true, ect = 78.0, dtcs = listOf("P0122")),
            null,
        )

        val respuesta = JSONObject(getSesion.call(JSONObject()))

        assertEquals(3, respuesta.getJSONObject("sesion").getInt("totalEventos"))
        val analisis = respuesta.getJSONObject("analisis")
        assertEquals("P0122", analisis.getJSONArray("codigosActuales").getString(0))
        val filas = analisis.getJSONArray("comparacion")
        val codigos = (0 until filas.length()).map { filas.getJSONObject(it) }.first { it.getString("metrica") == "CODIGOS" }
        assertEquals("FALLA", codigos.getString("veredicto"))
        val voltaje = (0 until filas.length()).map { filas.getJSONObject(it) }.first { it.getString("metrica") == "VOLTAJE" }
        assertEquals("OK", voltaje.getString("veredicto"))
        val eventos = respuesta.getJSONArray("eventos")
        val tipos = (0 until eventos.length()).map { eventos.getJSONObject(it).getString("tipo") }
        assertEquals(listOf("SINTOMAS", "DTC_LECTURA", "CHEQUEO"), tipos)
        assertFalse(eventos.getJSONObject(1).has("payload"))
        assertTrue(respuesta.isNull("siguienteDesde"))
    }

    @Test
    fun `get sin chequeo en la sesión explica cómo comparar`() = runTest {
        abrirBenelli()

        val comparacion = JSONObject(getSesion.call(JSONObject())).getJSONObject("analisis").get("comparacion")

        assertTrue(comparacion.toString(), comparacion.toString().contains("Corre un chequeo"))
    }

    @Test
    fun `get pagina los eventos y limita los que llevan payload`() = runTest {
        abrirBenelli()
        repeat(8) { ahora += 1_000; registro.anotarNota("nota $it") }

        val pagina = JSONObject(getSesion.call(JSONObject().put("desde_evento", 2).put("max_eventos", 3)))
        assertEquals(3, pagina.getJSONArray("eventos").length())
        assertEquals("nota 1", pagina.getJSONArray("eventos").getJSONObject(0).getString("resumen"))
        assertEquals(5, pagina.getInt("siguienteDesde"))

        val conPayload = JSONObject(getSesion.call(JSONObject().put("max_eventos", 50).put("incluir_payload", true)))
        assertEquals(5, conPayload.getJSONArray("eventos").length())
        assertEquals("nota 0", conPayload.getJSONArray("eventos").getJSONObject(1).getJSONObject("payload").getString("texto"))
    }

    @Test
    fun `get con sesion_id devuelve una sesión cerrada y avisa si no existe`() = runTest {
        abrirBenelli()
        repositorio.cerrarSesion(1, ahora + 1)

        assertFalse(JSONObject(getSesion.call(JSONObject().put("sesion_id", 1))).getJSONObject("sesion").getBoolean("abierta"))
        assertTrue(JSONObject(getSesion.call(JSONObject().put("sesion_id", 99))).getString("error").contains("99"))
    }

    @Test
    fun `agregar nota la deja en la sesión abierta con origen MCP`() = runTest {
        abrirBenelli()

        val respuesta = JSONObject(agregarNota.call(JSONObject().put("texto", "Señal verde-amarilla: 0,1 V cerrado, 0,8 V a fondo")))

        val nota = repositorio.todosLosEventos.single { it.tipo == TipoEvento.NOTA }
        assertEquals(nota.id, respuesta.getLong("eventoId"))
        assertEquals(OrigenEvento.MCP, nota.origen)
        assertEquals("Señal verde-amarilla: 0,1 V cerrado, 0,8 V a fondo", nota.resumen)
    }

    @Test
    fun `agregar nota sin sesión, vacía o enorme no escribe nada`() = runTest {
        assertTrue(JSONObject(agregarNota.call(JSONObject().put("texto", "hola"))).getString("error").contains("iniciar_sesion_taller"))
        abrirBenelli()
        assertTrue(JSONObject(agregarNota.call(JSONObject().put("texto", "  "))).getString("error").contains("vacía"))
        assertTrue(JSONObject(agregarNota.call(JSONObject().put("texto", "x".repeat(2_001)))).has("error"))

        assertTrue(repositorio.todosLosEventos.none { it.tipo == TipoEvento.NOTA })
    }
}
