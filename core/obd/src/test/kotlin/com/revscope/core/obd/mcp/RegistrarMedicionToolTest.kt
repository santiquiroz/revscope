package com.revscope.core.obd.mcp

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.taller.modelo.CableModelo
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.multimetro.AsistenteMultimetro
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.sesion.AdjuntosTaller
import com.revscope.core.obd.taller.sesion.BENELLI
import com.revscope.core.obd.taller.sesion.HistorialChequeosEnMemoria
import com.revscope.core.obd.taller.sesion.NuevoEvento
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import com.revscope.core.obd.taller.sesion.TallerRepositoryEnMemoria
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RegistrarMedicionToolTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private var ahora = 1_758_848_000_000L
    private var vehiculo: VehiculoTaller? = BENELLI
    private val repositorio = TallerRepositoryEnMemoria()

    private val registro by lazy {
        RegistroTaller(
            repositorio, { vehiculo }, HistorialChequeosEnMemoria(), AdjuntosTaller(File(carpeta.root, "taller")),
            PidRegistry(TestPids.load()), { ahora++ },
        )
    }
    private val tool by lazy { RegistrarMedicionTool(AsistenteMultimetro(repositorio, { vehiculo }, registro)) }

    private fun benelliConColores() {
        repositorio.modelos[BENELLI.claveModelo!!] = ConocimientoModelo(
            clave = BENELLI.claveModelo!!, nombre = "Benelli TNT 150i (2022)", tipo = VehicleType.MOTORCYCLE, ecu = null,
            fuenteEcu = null, notasProtocolo = "", notas = emptyList(), repuestos = emptyList(), bandas = emptyList(),
            cableado = listOf(
                CableadoSensor(
                    "TPS", "TPS", fuente = "Medición del dueño con multímetro, 25-sep-2026 (editable)",
                    cables = listOf(
                        CableModelo(FuncionCable.REF_5V, "Rojo"),
                        CableModelo(FuncionCable.MASA, "Negro"),
                        CableModelo(FuncionCable.SENAL, "Verde-amarillo"),
                    ),
                ),
            ),
        )
    }

    private suspend fun abrirSesion() = registro.abrirSesion(SolicitudSesion(titulo = "P0122")).getOrThrow()

    private suspend fun registrar(funcion: String, condicion: String, valor: Double, extra: JSONObject.() -> Unit = {}) =
        JSONObject(
            tool.call(
                JSONObject().put("plantilla", "TPS").put("funcion", funcion).put("condicion", condicion).put("valor", valor).apply(extra),
            ),
        )

    private suspend fun barridoEnSesion() {
        registro.anotar(
            NuevoEvento(
                tipo = TipoEvento.PRUEBA_GUIADA,
                titulo = "Barrido del TPS",
                payload = JSONObject().put("prueba", "TPS_BARRIDO").put("estado", "TERMINADA")
                    .put("detalle", JSONObject().put("cerradoV", 0.12).put("medioV", 0.46).put("fondoV", 0.89)),
            ),
        )
    }

    @Test
    fun `es una tool de control`() {
        assertEquals(McpPermiso.CONTROL, tool.permiso)
        assertEquals("registrar_medicion", tool.name)
    }

    @Test
    fun `sin sesión abierta no anota nada y dice cómo abrirla`() = runTest {
        val r = registrar("SENAL", "CERRADO", 0.1)

        assertTrue(r.getString("error").contains("iniciar_sesion_taller"))
        assertTrue(repositorio.todosLosEventos.isEmpty())
    }

    @Test
    fun `anota la celda con el color del modelo y devuelve su veredicto y lo pendiente`() = runTest {
        benelliConColores()
        val sesion = abrirSesion()

        val r = registrar("SENAL", "cerrado", 0.1)

        val celda = r.getJSONObject("celda")
        assertEquals("Verde-amarillo", celda.getString("color"))
        assertEquals("BAJO", celda.getString("estado"))
        assertEquals("0,3–1,0 V · Típico (editable)", celda.getString("referencia"))
        assertEquals(8, r.getJSONArray("pendientes").length())
        val evento = repositorio.todosLosEventos.single { it.tipo == TipoEvento.MEDICION_MULTIMETRO }
        assertEquals(sesion.id, evento.sesionId)
        assertEquals(OrigenEvento.MCP, evento.origen)
        assertEquals(Veredicto.FALLA, evento.veredicto)
        assertEquals(evento.id, r.getLong("eventoId"))
    }

    @Test
    fun `el acumulado junta las celdas de la sesión y compara con el barrido del TPS`() = runTest {
        benelliConColores()
        abrirSesion()
        barridoEnSesion()
        listOf("CERRADO", "MEDIO", "FONDO").forEach { c ->
            registrar("REF_5V", c, 5.0)
            registrar("MASA", c, 0.0)
        }
        registrar("SENAL", "CERRADO", 0.1)
        registrar("SENAL", "MEDIO", 0.35)

        val r = registrar("SENAL", "FONDO", 0.8)

        val acumulado = r.getJSONObject("acumulado")
        assertEquals("Referencia y masa correctas; señal baja en todo el recorrido", acumulado.getString("titulo"))
        assertTrue(acumulado.getJSONObject("ecu").getString("texto").contains("la ECU recibe lo mismo"))
        assertEquals(0, r.getJSONArray("pendientes").length())
    }

    @Test
    fun `sin plantilla del modelo usa la genérica sin colores`() = runTest {
        abrirSesion()

        val r = registrar("REF_5V", "CERRADO", 5.02)

        assertTrue(r.getJSONObject("celda").isNull("color"))
        assertEquals("Genérica típica, sin colores", r.getJSONObject("plantilla").getString("fuenteColores"))
        assertEquals("DENTRO", r.getJSONObject("celda").getString("estado"))
    }

    @Test
    fun `argumentos inválidos se rechazan sin anotar`() = runTest {
        abrirSesion()

        assertTrue(JSONObject(tool.call(JSONObject().put("plantilla", "CARBURADOR"))).getString("error").contains("TPS"))
        assertTrue(registrar("CALEFACTOR", "CERRADO", 1.0).getString("error").contains("REF_5V"))
        assertTrue(registrar("SENAL", "CONTACTO", 1.0).getString("error").contains("CERRADO"))
        assertTrue(registrar("SENAL", "CERRADO", 1.0) { put("unidad", "ohm") }.getString("error").contains("en V"))
        assertTrue(JSONObject(tool.call(JSONObject().put("plantilla", "TPS").put("funcion", "SENAL").put("condicion", "CERRADO"))).has("error"))
        assertTrue(repositorio.todosLosEventos.none { it.tipo == TipoEvento.MEDICION_MULTIMETRO })
    }

    @Test
    fun `el inyector se registra en ohmios por defecto`() = runTest {
        abrirSesion()

        val r = JSONObject(
            tool.call(JSONObject().put("plantilla", "INYECTOR").put("funcion", "OTRO").put("condicion", "DESCONECTADO").put("valor", 12.4)),
        )

        assertEquals("Ω", r.getJSONObject("celda").getString("unidad"))
        assertEquals("DENTRO", r.getJSONObject("celda").getString("estado"))
    }
}
