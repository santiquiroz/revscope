package com.revscope.core.obd.mcp

import com.revscope.core.obd.escucha.CapturaCan
import com.revscope.core.obd.escucha.CapturasCanRepositorio
import com.revscope.core.obd.escucha.MuestraGps
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ListarCapturasCanToolTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `lista capturas de la mas nueva a la mas antigua`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        repo.guardar(captura("1000-vieja"))
        repo.guardar(captura("2000-nueva"))

        val respuesta = JSONObject(ListarCapturasCanTool(repo).call(JSONObject()))
        val capturas = respuesta.getJSONArray("capturas")

        assertEquals("2000-nueva", capturas.getJSONObject(0).getString("id"))
        assertEquals("1000-vieja", capturas.getJSONObject(1).getString("id"))
        assertEquals(1, capturas.getJSONObject(0).getInt("tramas"))
    }

    @Test
    fun `expone origen y muestras gps`() = runTest {
        val repo = CapturasCanRepositorio(temporal.root)
        repo.guardar(captura("1000-mcp"))
        repo.guardar(
            captura("2000-ruta").copy(origen = CapturaCan.ORIGEN_APP),
            listOf(MuestraGps(0L, 10f, 3f, 0f), MuestraGps(1_000L, 11f, 3f, 0f)),
        )

        val capturas = JSONObject(ListarCapturasCanTool(repo).call(JSONObject())).getJSONArray("capturas")

        assertEquals("app", capturas.getJSONObject(0).getString("origen"))
        assertEquals(2, capturas.getJSONObject(0).getInt("muestrasGps"))
        assertEquals("mcp", capturas.getJSONObject(1).getString("origen"))
        assertEquals(0, capturas.getJSONObject(1).getInt("muestrasGps"))
    }

    private fun captura(id: String) = CapturaCan(
        id = id,
        etiqueta = id.substringAfter('-'),
        inicioEpochMs = id.substringBefore('-').toLong(),
        duracionMs = 1_000L,
        protocolo = "6",
        bits29 = false,
        filtro = null,
        silencioso = true,
        desbordes = 0,
        errores = 0,
        tramas = emptyList(),
        totalTramas = 1,
    )
}
