package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.CapturaCan
import com.revscope.core.obd.escucha.CapturasCanRepositorio
import com.revscope.core.obd.escucha.MuestraGps
import com.revscope.core.obd.escucha.TramaCan
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LeerGpsCapturaCanToolTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `devuelve una pagina de muestras con nulos`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        repo.guardar(
            captura("1000-ruta"),
            listOf(
                MuestraGps(0L, 10.5f, 3f, 90f),
                MuestraGps(1_000L, 12.3f, null, null),
                MuestraGps(2_000L, null, 5f, 180f),
            ),
        )

        val respuesta = JSONObject(
            LeerGpsCapturaCanTool(repo).call(JSONObject().put("id", "1000-ruta").put("desde", 1).put("limite", 1)),
        )

        assertEquals("1000-ruta", respuesta.getString("id"))
        assertEquals(3, respuesta.getInt("total"))
        assertEquals(1, respuesta.getInt("desde"))
        val muestras = respuesta.getJSONArray("muestras")
        assertEquals(1, muestras.length())
        val muestra = muestras.getJSONObject(0)
        assertEquals(1_000L, muestra.getLong("tMs"))
        assertEquals(12.3, muestra.getDouble("velocidadKmh"), 0.0)
        assertTrue(muestra.isNull("precisionM"))
        assertTrue(muestra.isNull("rumboGrados"))
    }

    @Test
    fun `desde mas alla del final devuelve lista vacia`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        repo.guardar(captura("1000-ruta"), listOf(MuestraGps(0L, 1f, 1f, 1f)))

        val respuesta = JSONObject(LeerGpsCapturaCanTool(repo).call(JSONObject().put("id", "1000-ruta").put("desde", 5)))

        assertEquals(1, respuesta.getInt("total"))
        assertEquals(0, respuesta.getJSONArray("muestras").length())
    }

    @Test
    fun `captura inexistente devuelve error`() = runTest {
        val respuesta = JSONObject(
            LeerGpsCapturaCanTool(CapturasCanRepositorio(temporal.root)).call(JSONObject().put("id", "9000-falta")),
        )

        assertTrue(respuesta.getString("error").contains("no existe la captura 9000-falta"))
    }

    @Test
    fun `captura sin gps devuelve error`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        repo.guardar(captura("1000-base"))

        val respuesta = JSONObject(LeerGpsCapturaCanTool(repo).call(JSONObject().put("id", "1000-base")))

        assertTrue(respuesta.getString("error").contains("no tiene muestras GPS"))
    }

    @Test
    fun `limite fuera de rango devuelve error`() = runTest {
        val respuesta = JSONObject(
            LeerGpsCapturaCanTool(CapturasCanRepositorio(temporal.root))
                .call(JSONObject().put("id", "1000-ruta").put("limite", 0)),
        )

        assertTrue(respuesta.has("error"))
    }

    private fun captura(id: String) = CapturaCan(
        id = id,
        etiqueta = "ruta",
        inicioEpochMs = 1_000L,
        duracionMs = 3_000L,
        protocolo = "6",
        bits29 = false,
        filtro = null,
        silencioso = true,
        desbordes = 0,
        errores = 0,
        tramas = listOf(TramaCan(0, 0x7E8, false, listOf(0x01))),
        origen = CapturaCan.ORIGEN_APP,
    )
}
