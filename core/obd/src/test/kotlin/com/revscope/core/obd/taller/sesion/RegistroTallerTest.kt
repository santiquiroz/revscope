package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.FreezeFrame
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.taller.ReductorSerie
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.ResumenPid
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.HealthReportFormato
import com.revscope.core.obd.workshop.MetricasChequeo
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RegistroTallerTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val ahora = 1_758_848_000_000L
    private val repositorio = TallerRepositoryEnMemoria()
    private val registry = PidRegistry(TestPids.load())
    private var vehiculo: VehiculoTaller? = BENELLI

    private val chequeo11Julio = ChequeoRegistrado(
        id = 3, vehiculoId = 7, instante = ahora - 76L * 24 * 3_600_000, items = emptyList(),
        metricas = MetricasChequeo(ltft = 2.3, stft = -6.2, voltaje = 13.7, motorEncendido = true, ect = 79.0),
    )

    private val registro by lazy {
        RegistroTaller(
            repositorio = repositorio,
            vehiculo = { vehiculo },
            historial = HistorialChequeosEnMemoria(listOf(chequeo11Julio)),
            adjuntos = AdjuntosTaller(File(carpeta.root, "taller")),
            registry = registry,
            reloj = { ahora },
        )
    }

    private val scanP0122 = DtcScan(
        activos = listOf(DtcCode("P0122", DtcMode.Active)),
        pendientes = emptyList(),
        permanentes = emptyList(),
        milEncendida = true,
        conteoSegunEcu = 1,
        freezeFrame = FreezeFrame("P0122", listOf(ObdReading("0C", 1_454.0, "rpm"))),
        crudo = mapOf("03" to "430101220000"),
        errores = emptyList(),
    )

    private val scanLimpio = scanP0122.copy(activos = emptyList(), milEncendida = false, conteoSegunEcu = 0, freezeFrame = null)

    private suspend fun abrir(): SesionTaller = registro.abrirSesion(
        SolicitudSesion(sintomas = setOf(Sintoma.SE_APAGA_AL_SOLTAR), sintomasTexto = "El TPS se cambió hace meses"),
    ).getOrThrow()

    private fun unicoEvento(tipo: TipoEvento): EventoTaller = repositorio.todosLosEventos.single { it.tipo == tipo }

    private fun payload(evento: EventoTaller) = JSONObject(evento.payloadJson)

    @Test
    fun `sin sesión abierta no escribe nada`() = runTest {
        assertNull(registro.anotarLecturaDtc(scanP0122))
        assertNull(registro.anotarNota("TPS nuevo instalado"))
        assertNull(registro.anotarChequeo(1, emptyList(), MetricasChequeo(), null))

        assertTrue(repositorio.todosLosEventos.isEmpty())
    }

    @Test
    fun `sin vehículo activo no escribe ni abre sesión`() = runTest {
        vehiculo = null

        assertNull(registro.anotarNota("hola"))
        assertTrue(registro.abrirSesion(SolicitudSesion()).isFailure)
        assertTrue(repositorio.todosLosEventos.isEmpty())
    }

    @Test
    fun `una sesión cerrada ya no recibe eventos`() = runTest {
        val sesion = abrir()
        repositorio.cerrarSesion(sesion.id, ahora + 1)

        assertNull(registro.anotarLecturaDtc(scanP0122))
    }

    @Test
    fun `abrir sesión toma el vehículo, el chequeo anterior como base y anota los síntomas`() = runTest {
        val sesion = abrir()

        assertEquals(7L, sesion.vehiculoId)
        assertEquals("benelli-tnt150i-2022", sesion.claveModelo)
        assertEquals(3L, sesion.chequeoBaseId)
        assertEquals("Se apaga al soltar el acelerador", sesion.titulo)
        val sintomas = unicoEvento(TipoEvento.SINTOMAS)
        assertEquals("Se apaga al soltar el acelerador · El TPS se cambió hace meses", sintomas.resumen)
        assertEquals("SE_APAGA_AL_SOLTAR", payload(sintomas).getJSONArray("sintomas").getString(0))
    }

    @Test
    fun `con sesión anota la lectura de códigos con freeze frame y la cadena cruda`() = runTest {
        val sesion = abrir()

        val id = registro.anotarLecturaDtc(scanP0122)

        val evento = unicoEvento(TipoEvento.DTC_LECTURA)
        assertEquals(id, evento.id)
        assertEquals(sesion.id, evento.sesionId)
        assertEquals(ahora, evento.instante)
        assertEquals(Veredicto.FALLA, evento.veredicto)
        assertEquals(OrigenEvento.APP, evento.origen)
        assertEquals("Activos: P0122 · testigo encendido", evento.resumen)
        val p = payload(evento)
        assertEquals("P0122", p.getJSONArray("activos").getString(0))
        assertEquals("430101220000", p.getJSONObject("crudo").getString("03"))
        assertEquals("P0122", p.getJSONObject("freezeFrame").getString("dtcCausante"))
        assertEquals("RPM Motor", p.getJSONObject("freezeFrame").getJSONArray("valores").getJSONObject(0).getString("nombre"))
    }

    @Test
    fun `la lectura del MCP queda marcada con su origen`() = runTest {
        abrir()

        registro.anotarLecturaDtc(scanLimpio, OrigenEvento.MCP)

        val evento = unicoEvento(TipoEvento.DTC_LECTURA)
        assertEquals(OrigenEvento.MCP, evento.origen)
        assertEquals(Veredicto.OK, evento.veredicto)
        assertEquals("Sin códigos · testigo apagado", evento.resumen)
    }

    @Test
    fun `el borrado guarda antes y después`() = runTest {
        abrir()

        registro.anotarBorradoDtc(BorradoDtc("44", rechazadoPorCondiciones = false, antes = scanP0122, despues = scanLimpio))

        val evento = unicoEvento(TipoEvento.DTC_BORRADO)
        assertEquals(Veredicto.OK, evento.veredicto)
        assertTrue(evento.resumen, evento.resumen.startsWith("Antes: P0122 · después: sin códigos"))
        val p = payload(evento)
        assertEquals("P0122", p.getJSONObject("antes").getJSONArray("activos").getString(0))
        assertEquals(0, p.getJSONObject("despues").getJSONArray("activos").length())
        assertEquals("44", p.getString("respuesta"))
    }

    @Test
    fun `un borrado rechazado o con el código de vuelta pide atención`() = runTest {
        abrir()

        registro.anotarBorradoDtc(BorradoDtc("7F0422", rechazadoPorCondiciones = true, antes = scanP0122, despues = scanP0122))

        assertEquals(Veredicto.ATENCION, unicoEvento(TipoEvento.DTC_BORRADO).veredicto)
    }

    @Test
    fun `el chequeo guarda hallazgos, métricas y el id del informe`() = runTest {
        abrir()
        val items = listOf(
            DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.FALLA, "DTC", "1 códigos: P0122", "Ábrelos"),
            DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.OK, "Eléctrico", "Carga correcta", "14,2 V"),
        )
        val metricas = MetricasChequeo(ltft = 2.3, stft = -4.0, voltaje = 14.2, motorEncendido = true, dtcs = listOf("P0122"))

        registro.anotarChequeo(chequeoId = 12, items = items, metricas = metricas, dtc = scanP0122)

        val evento = unicoEvento(TipoEvento.CHEQUEO)
        assertEquals(Veredicto.FALLA, evento.veredicto)
        assertEquals("1 falla · 1 OK", evento.resumen)
        val p = payload(evento)
        assertEquals(12, p.getInt("healthReportId"))
        assertEquals(metricas, HealthReportFormato.metricasDe(p.getJSONObject("metricas")))
        assertEquals(2, p.getJSONArray("items").length())
        assertEquals("430101220000", p.getJSONObject("dtc").getJSONObject("crudo").getString("03"))
    }

    @Test
    fun `la captura copia el CSV a la carpeta de la sesión y guarda una serie reducida`() = runTest {
        val sesion = abrir()
        val csv = carpeta.newFile("captura-cap-1.csv").apply { writeText("t_ms,pid,valor\n0,11,2.35\n") }
        val muestras = (0 until 3_000).map { i ->
            MuestraCaptura(i.toLong(), i * 50_000L, "11", if (i == 1_500) 0.4 else 2.35, i.toLong(), 20)
        }
        val resumen = ResumenCaptura(
            id = "cap-1", duracionMs = 150_000, porPid = listOf(ResumenPid("11", 3_000, 20.0, 0.4, 2.35, 2.35)),
            latenciaP50Ms = 20.0, latenciaP95Ms = 30.0, motivoFin = "detenida por el usuario", rutaCsv = csv.absolutePath,
        )

        registro.anotarCaptura(resumen, muestras)

        val evento = unicoEvento(TipoEvento.CAPTURA)
        val copia = File(evento.adjunto!!)
        assertEquals(File(carpeta.root, "taller/${sesion.id}/captura-cap-1.csv").canonicalPath, copia.canonicalPath)
        assertEquals(csv.readText(), copia.readText())
        val p = payload(evento)
        assertEquals(copia.absolutePath, p.getString("csvEnSesion"))
        val serie = p.getJSONObject("serie").getJSONObject("11")
        assertTrue(serie.getJSONArray("v").length() <= ReductorSerie.MAX_PUNTOS)
        assertEquals(3_000, serie.getInt("n"))
        assertTrue((0 until serie.getJSONArray("v").length()).any { serie.getJSONArray("v").getDouble(it) == 0.4 })
        assertEquals(2.35, p.getJSONObject("porPid").getJSONObject("11").getDouble("max"), 1e-9)
        assertTrue(evento.titulo, evento.titulo.startsWith("Captura rápida · 1 PID · 150,0 s"))
    }

    @Test
    fun `una captura sin CSV se anota igual, sin adjunto`() = runTest {
        abrir()
        val resumen = ResumenCaptura("cap-2", 1_000, emptyList(), null, null, "enlace perdido", rutaCsv = null)

        registro.anotarCaptura(resumen, emptyList())

        assertNull(unicoEvento(TipoEvento.CAPTURA).adjunto)
        assertFalse(payload(unicoEvento(TipoEvento.CAPTURA)).has("csvEnSesion"))
    }

    @Test
    fun `una nota vacía no se anota y una con texto sí`() = runTest {
        abrir()

        assertNull(registro.anotarNota("   "))
        assertNotNull(registro.anotarNota("  Conector del TPS con un pin flojo ", OrigenEvento.MCP))

        val nota = unicoEvento(TipoEvento.NOTA)
        assertEquals("Conector del TPS con un pin flojo", nota.resumen)
        assertEquals(OrigenEvento.MCP, nota.origen)
    }

    @Test
    fun `la instantánea guarda cada lectura con su nombre, unidad y edad`() = runTest {
        abrir()
        val lecturas = mapOf(
            "05" to ObdReading("05", 32.0, "°C", timestamp = ahora - 2_000),
            "0B" to ObdReading("0B", 70.0, "kPa", timestamp = ahora),
        )

        registro.anotarInstantanea(lecturas)

        val evento = unicoEvento(TipoEvento.INSTANTANEA_SENSORES)
        val primera = payload(evento).getJSONArray("lecturas").getJSONObject(0)
        assertEquals("05", primera.getString("pid"))
        assertEquals(2, primera.getInt("edadS"))
        assertEquals("°C", primera.getString("unidad"))
        assertTrue(evento.resumen, evento.resumen.contains("32 °C"))
    }

    @Test
    fun `un fallo del almacenamiento no se propaga a quien anota`() = runTest {
        abrir()
        repositorio.fallarAlAgregar = true

        assertNull(registro.anotarLecturaDtc(scanP0122))
    }

    @Test
    fun `abrir sin chequeo base o con uno elegido respeta la elección`() = runTest {
        val sinBase = registro.abrirSesion(SolicitudSesion(chequeoBase = ChequeoBase.Ninguno)).getOrThrow()
        val elegido = registro.abrirSesion(SolicitudSesion(chequeoBase = ChequeoBase.Elegido(42))).getOrThrow()

        assertNull(sinBase.chequeoBaseId)
        assertEquals(42L, elegido.chequeoBaseId)
    }

    @Test
    fun `cerrar la sesión le pone la hora de cierre y un segundo cierre no hace nada`() = runTest {
        val sesion = abrir()

        assertTrue(registro.cerrarSesion(sesion.id))
        assertFalse(registro.cerrarSesion(sesion.id))

        assertEquals(ahora, repositorio.sesion(sesion.id)?.cierre)
        assertNull(registro.sesionAbierta())
    }

    @Test
    fun `eliminar la sesión borra sus eventos y la carpeta de adjuntos`() = runTest {
        val sesion = abrir()
        val csv = carpeta.newFile("captura-cap-9.csv").apply { writeText("t_ms,pid,valor\n") }
        registro.anotarCaptura(
            ResumenCaptura("cap-9", 1_000, emptyList(), null, null, "detenida por el usuario", rutaCsv = csv.absolutePath),
            emptyList(),
        )
        val carpetaSesion = File(carpeta.root, "taller/${sesion.id}")
        assertTrue(carpetaSesion.isDirectory)

        assertTrue(registro.eliminarSesion(sesion.id))

        assertNull(repositorio.sesion(sesion.id))
        assertTrue(repositorio.todosLosEventos.isEmpty())
        assertFalse(carpetaSesion.exists())
        assertTrue("el CSV original no es de la sesión y se conserva", csv.exists())
    }

    @Test
    fun `eliminar una sesión que no existe no borra nada`() = runTest {
        assertFalse(registro.eliminarSesion(99))
    }

    @Test
    fun `un payload enorme pierde primero la serie y conserva las estadísticas`() {
        val grande = JSONObject().put("porPid", JSONObject().put("11", 1)).put("serie", "x".repeat(70_000))

        val ajustado = JSONObject(LimitePayload.ajustar(grande))

        assertFalse(ajustado.has("serie"))
        assertTrue(ajustado.getBoolean("serieOmitida"))
        assertEquals(1, ajustado.getJSONObject("porPid").getInt("11"))
    }
}
