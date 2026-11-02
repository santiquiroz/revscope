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

class DiferenciaCapturasCanToolTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `serializa byte cambiado y bit indicador`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        repo.guardar(captura("1000-base", listOf(0x00, 0x10), listOf(0x01, 0x10)))
        repo.guardar(captura("2000-accion", listOf(0x80, 0x20), listOf(0x81, 0x20)))

        val respuesta = JSONObject(
            DiferenciaCapturasCanTool(repo).call(
                JSONObject().put("base", "1000-base").put("accion", "2000-accion"),
            ),
        )

        val byte = respuesta.getJSONArray("bytes").getJSONObject(0)
        assertEquals("123", byte.getString("id"))
        assertEquals(1, byte.getInt("byte"))
        assertEquals("10", byte.getString("valorBase"))
        assertEquals("20", byte.getJSONArray("valoresNuevos").getString(0))
        val bit = respuesta.getJSONArray("bits").getJSONObject(0)
        assertEquals(0, bit.getInt("byte"))
        assertEquals(7, bit.getInt("bit"))
        assertEquals(0, bit.getInt("valorBase"))
    }

    @Test
    fun `id inexistente devuelve error`() = runTest {
        val respuesta = JSONObject(
            DiferenciaCapturasCanTool(CapturasCanRepositorio(temporal.root)).call(
                JSONObject().put("base", "1000-falta").put("accion", "2000-falta"),
            ),
        )

        assertTrue(respuesta.getString("error").contains("1000-falta"))
    }

    private fun captura(id: String, vararg valores: List<Int>) = CapturaCan(
        id = id,
        etiqueta = id,
        inicioEpochMs = id.substringBefore('-').toLong(),
        duracionMs = 1_000L,
        protocolo = "6",
        bits29 = false,
        filtro = null,
        silencioso = true,
        desbordes = 0,
        errores = 0,
        tramas = valores.map { TramaCan(0, 0x123, false, it) },
    )
}
