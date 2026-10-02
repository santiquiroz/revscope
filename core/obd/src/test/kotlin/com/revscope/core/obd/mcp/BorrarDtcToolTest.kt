package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.OrigenAutorizacion
import com.revscope.core.obd.mcp.escritura.PermisoEscritura
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorrarDtcToolTest {

    private val ahora = 100_000L
    private val registry = PidRegistry(TestPids.load())
    private val notifier = mockk<McpActionNotifier>(relaxed = true)
    private val ejecutor = mockk<EjecutorEscritura>(relaxed = true).also {
        coEvery { it.autorizar(any()) } returns PermisoEscritura.Concedido(OrigenAutorizacion.TOQUE)
    }

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

    private fun manager(velocidad: ObdReading?, rechazado: Boolean = false): ObdSessionManager =
        mockk<ObdSessionManager>().also {
            every { it.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))
            every { it.readings } returns MutableStateFlow(listOfNotNull(velocidad).associateBy { r -> r.pid })
            coEvery { it.borrarDtcConRelectura(any()) } returns Result.success(
                BorradoDtc(
                    respuestaCruda = if (rechazado) "7F0422" else "44",
                    rechazadoPorCondiciones = rechazado,
                    antes = scan("P0300"),
                    despues = if (rechazado) scan("P0300") else scan(),
                ),
            )
        }

    private fun tool(m: ObdSessionManager) = BorrarDtcTool(m, registry, notifier, ejecutor) { ahora }

    private fun detenido() = ObdReading("0D", 0.0, "km/h", timestamp = ahora - 500)

    @Test
    fun `sin confirmar BORRAR no envia 04`() = runTest {
        val m = manager(detenido())

        val json = JSONObject(tool(m).call(JSONObject().put("confirmar", "si")))

        assertTrue(json.getString("error").contains("BORRAR"))
        coVerify(exactly = 0) { m.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `en movimiento se rechaza`() = runTest {
        val m = manager(ObdReading("0D", 35.0, "km/h", timestamp = ahora - 200))

        val json = JSONObject(tool(m).call(JSONObject().put("confirmar", "BORRAR")))

        assertTrue(json.getString("error").contains("movimiento"))
        coVerify(exactly = 0) { m.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `sin lectura reciente de velocidad se rechaza`() = runTest {
        val vieja = manager(ObdReading("0D", 0.0, "km/h", timestamp = ahora - 5_000))
        val sinLectura = manager(null)

        assertTrue(JSONObject(tool(vieja).call(JSONObject().put("confirmar", "BORRAR"))).has("error"))
        assertTrue(JSONObject(tool(sinLectura).call(JSONObject().put("confirmar", "BORRAR"))).has("error"))
        coVerify(exactly = 0) { vieja.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `detenido y confirmado borra relee y avisa en el telefono`() = runTest {
        val m = manager(detenido())

        val json = JSONObject(tool(m).call(JSONObject().put("confirmar", "BORRAR")))

        assertTrue(json.getBoolean("borrado"))
        assertEquals("P0300", json.getJSONObject("antes").getJSONArray("activos").getString(0))
        assertEquals(0, json.getJSONObject("despues").getJSONArray("activos").length())
        verify(exactly = 1) { notifier.avisar("El MCP borró los códigos de falla", any()) }
    }

    @Test
    fun `informa el rechazo 7F 04 22 del ecu`() = runTest {
        val json = JSONObject(tool(manager(detenido(), rechazado = true)).call(JSONObject().put("confirmar", "BORRAR")))

        assertFalse(json.getBoolean("borrado"))
        assertTrue(json.getBoolean("rechazadoPorCondiciones"))
        assertTrue(json.getString("mensaje").contains("7F 04 22"))
    }

    @Test
    fun `pide permiso de borrado y advierte de la revision tecnico-mecanica`() {
        val tool = tool(manager(detenido()))

        assertEquals(McpPermiso.BORRADO, tool.permiso)
        assertTrue(tool.description.contains("técnico-mecánica"))
    }

    @Test
    fun `sin relectura valida no dice que borro ni que quedo limpio`() = runTest {
        val m = manager(detenido())
        val fallida = scan().copy(serviciosFallidos = setOf(DtcServicio.ACTIVOS))
        coEvery { m.borrarDtcConRelectura(any()) } returns
            Result.success(BorradoDtc("44", rechazadoPorCondiciones = false, antes = scan("P0300"), despues = fallida))

        val json = JSONObject(tool(m).call(JSONObject().put("confirmar", "BORRAR")))

        assertFalse(json.getBoolean("verificado"))
        assertFalse(json.getJSONObject("despues").getBoolean("completa"))
        assertEquals("03", json.getJSONObject("despues").getJSONArray("serviciosSinRespuesta").getString(0))
        assertTrue(json.getString("mensaje").contains("no se pudo confirmar"))
    }

    @Test
    fun `si los codigos vuelven el mensaje lo dice`() = runTest {
        val m = manager(detenido())
        coEvery { m.borrarDtcConRelectura(any()) } returns
            Result.success(BorradoDtc("44", rechazadoPorCondiciones = false, antes = scan("P0300"), despues = scan("P0300")))

        val json = JSONObject(tool(m).call(JSONObject().put("confirmar", "BORRAR")))

        assertTrue(json.getString("mensaje").contains("sigue reportando P0300"))
    }

    @Test
    fun `si el dueno rechaza en el telefono no borra nada`() = runTest {
        val m = manager(detenido())
        coEvery { ejecutor.autorizar(any()) } returns PermisoEscritura.Denegado("el dueño rechazó la escritura en el teléfono")

        val json = JSONObject(tool(m).call(JSONObject().put("confirmar", "BORRAR")))

        assertTrue(json.getString("error").contains("rechazó"))
        coVerify(exactly = 0) { m.borrarDtcConRelectura(any()) }
    }

    @Test
    fun `el borrado autorizado queda en la auditoria con la respuesta del ECU`() = runTest {
        val m = manager(detenido())

        tool(m).call(JSONObject().put("confirmar", "BORRAR"))

        verify { ejecutor.registrarResultado(match { it.tool == "borrar_dtc" }, OrigenAutorizacion.TOQUE, listOf("44"), null) }
    }
}
