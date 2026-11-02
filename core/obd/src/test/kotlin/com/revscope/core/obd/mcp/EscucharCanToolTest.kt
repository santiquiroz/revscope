package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.CapturaCan
import com.revscope.core.obd.escucha.OpcionesEscucha
import com.revscope.core.obd.escucha.ServicioEscuchaCan
import com.revscope.core.obd.escucha.TramaCan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscucharCanToolTest {

    @Test
    fun `sin conexion devuelve el mensaje y no llama al servicio`() = runTest {
        val fixture = leaseFixture(conectado = false)
        val servicio = mockk<ServicioEscuchaCan>()

        val respuesta = JSONObject(EscucharCanTool(fixture.manager, servicio).call(JSONObject().put("etiqueta", "base")))

        assertEquals(false, respuesta.getBoolean("conectado"))
        assertEquals("vehículo no conectado", respuesta.getString("mensaje"))
        coVerify(exactly = 0) { servicio.escuchar(any()) }
    }

    @Test
    fun `argumentos invalidos se rechazan antes de llamar al servicio`() = runTest {
        val fixture = leaseFixture()
        val servicio = mockk<ServicioEscuchaCan>()
        val tool = EscucharCanTool(fixture.manager, servicio)
        val casos = listOf(
            JSONObject().put("etiqueta", ""),
            JSONObject().put("etiqueta", "base").put("segundos", 0),
            JSONObject().put("etiqueta", "base").put("segundos", 61),
            JSONObject().put("etiqueta", "base").put("filtro", "7E8Z"),
        )

        casos.forEach { argumentos ->
            assertTrue(JSONObject(tool.call(argumentos)).has("error"))
        }

        coVerify(exactly = 0) { servicio.escuchar(any()) }
    }

    @Test
    fun `captura exitosa devuelve metadatos resumen y opciones normalizadas`() = runTest {
        val fixture = leaseFixture()
        val servicio = mockk<ServicioEscuchaCan>()
        val captura = captura(silencioso = true)
        coEvery { servicio.escuchar(any()) } returns Result.success(captura)
        val slot = io.mockk.slot<OpcionesEscucha>()

        val respuesta = JSONObject(
            EscucharCanTool(fixture.manager, servicio).call(
                JSONObject().put("etiqueta", "base").put("segundos", 5).put("filtro", "7e8"),
            ),
        )

        coVerify(exactly = 1) { servicio.escuchar(capture(slot)) }
        assertEquals(OpcionesEscucha("base", 5_000L, "7E8"), slot.captured)
        assertEquals(captura.id, respuesta.getString("id"))
        assertEquals(2, respuesta.getInt("tramas"))
        assertTrue(respuesta.has("huecos"))
        assertFalse(respuesta.has("aviso"))
        val resumen = respuesta.getJSONArray("resumen").getJSONObject(0)
        assertEquals("7E8", resumen.getString("id"))
        assertEquals(2, resumen.getInt("tramas"))
        assertEquals(0.4, resumen.getDouble("hz"), 0.001)
        assertEquals(1, resumen.getJSONArray("bytesQueCambian").getInt(0))
    }

    @Test
    fun `captura no silenciosa incluye aviso sobre ACK`() = runTest {
        val fixture = leaseFixture()
        val servicio = mockk<ServicioEscuchaCan>()
        coEvery { servicio.escuchar(any()) } returns Result.success(captura(silencioso = false))

        val respuesta = JSONObject(
            EscucharCanTool(fixture.manager, servicio).call(JSONObject().put("etiqueta", "base")),
        )

        assertEquals(
            "el adaptador no aceptó AT CSM1: pudo responder ACK en el bus",
            respuesta.getString("aviso"),
        )
    }

    @Test
    fun `falla del servicio devuelve error`() = runTest {
        val fixture = leaseFixture()
        val servicio = mockk<ServicioEscuchaCan>()
        coEvery { servicio.escuchar(any()) } returns Result.failure(IllegalStateException("falló la captura"))

        val respuesta = JSONObject(
            EscucharCanTool(fixture.manager, servicio).call(JSONObject().put("etiqueta", "base")),
        )

        assertEquals("falló la captura", respuesta.getString("error"))
    }

    private fun captura(silencioso: Boolean) = CapturaCan(
        id = "1000-base",
        etiqueta = "base",
        inicioEpochMs = 1_000L,
        duracionMs = 5_000L,
        protocolo = "6",
        bits29 = false,
        filtro = null,
        silencioso = silencioso,
        desbordes = 0,
        errores = 0,
        tramas = listOf(
            TramaCan(0, 0x7E8, false, listOf(0x01, 0x10)),
            TramaCan(2_500, 0x7E8, false, listOf(0x01, 0x20)),
        ),
    )
}
