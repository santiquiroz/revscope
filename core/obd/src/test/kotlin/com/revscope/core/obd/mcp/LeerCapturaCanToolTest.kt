package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.Candump
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

class LeerCapturaCanToolTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `devuelve metadatos y pagina candump`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        val captura = captura("1000-base")
        repo.guardar(captura)

        val respuesta = JSONObject(
            LeerCapturaCanTool(repo).call(JSONObject().put("id", captura.id).put("desde", 1).put("limite", 1)),
        )

        assertEquals(captura.id, respuesta.getString("id"))
        assertEquals(3, respuesta.getInt("total"))
        assertEquals(1, respuesta.getInt("desde"))
        assertEquals(
            Candump.linea(captura.tramas[1], captura.inicioEpochMs),
            respuesta.getJSONArray("lineas").getString(0),
        )
    }

    @Test
    fun `id inexistente devuelve error`() = runTest {
        val respuesta = JSONObject(
            LeerCapturaCanTool(CapturasCanRepositorio(temporal.root))
                .call(JSONObject().put("id", "9000-falta")),
        )

        assertTrue(respuesta.getString("error").contains("9000-falta"))
    }

    private fun captura(id: String) = CapturaCan(
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
        tramas = listOf(
            TramaCan(0, 0x7E8, false, listOf(0x01)),
            TramaCan(100, 0x7E8, false, listOf(0x02)),
            TramaCan(200, 0x7E8, false, listOf(0x03)),
        ),
    )
}
