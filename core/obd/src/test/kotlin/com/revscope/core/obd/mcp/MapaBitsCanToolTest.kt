package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.CapturaCan
import com.revscope.core.obd.escucha.CapturasCanRepositorio
import com.revscope.core.obd.escucha.TramaCan
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MapaBitsCanToolTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `captura inexistente devuelve error`() = runTest {
        val respuesta = JSONObject(
            MapaBitsCanTool(CapturasCanRepositorio(temporal.root))
                .call(JSONObject().put("id", "1000-falta")),
        )

        assertTrue(respuesta.getString("error").contains("1000-falta"))
    }

    @Test
    fun `filtra por id can`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        repo.guardar(captura("1000-base", listOf(
            TramaCan(0, 0x123, false, listOf(0x11)),
            TramaCan(1, 0x456, false, listOf(0x22)),
        )))

        val respuesta = JSONObject(
            MapaBitsCanTool(repo).call(JSONObject().put("id", "1000-base").put("can_id", "123")),
        )

        val bytes = respuesta.getJSONArray("bytes")
        assertEquals(1, bytes.length())
        assertEquals("123", bytes.getJSONObject(0).getString("id"))
    }

    @Test
    fun `limita salida a cuatrocientas entradas`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        val tramas = (0 until 51).map { id ->
            TramaCan(0, id, false, List(8) { it })
        }
        repo.guardar(captura("1000-grande", tramas))

        val respuesta = JSONObject(MapaBitsCanTool(repo).call(JSONObject().put("id", "1000-grande")))

        assertEquals(400, respuesta.getJSONArray("bytes").length())
        assertTrue(respuesta.getBoolean("truncado"))
    }

    private fun captura(id: String, tramas: List<TramaCan>) = CapturaCan(
        id = id,
        etiqueta = "base",
        inicioEpochMs = 1_000L,
        duracionMs = 1_000L,
        protocolo = "6",
        bits29 = false,
        filtro = null,
        silencioso = true,
        desbordes = 0,
        errores = 0,
        tramas = tramas,
    )
}
