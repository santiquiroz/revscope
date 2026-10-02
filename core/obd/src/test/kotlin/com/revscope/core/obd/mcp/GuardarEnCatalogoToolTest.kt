package com.revscope.core.obd.mcp

import com.revscope.core.obd.catalogo.CatalogoPropietario
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder

class GuardarEnCatalogoToolTest {
    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `guarda entrada descubierta y aplica defaults`() = kotlinx.coroutines.test.runTest {
        val archivo = File(temporal.root, "catalogo.json")
        val catalogo = CatalogoPropietario({ null }, archivo)
        val entrada = JSONObject()
            .put("id", "descubierto.01")
            .put("marca", "Mazda")
            .put("tipo", "lectura")
            .put("pasos", org.json.JSONArray().put("22 21 21"))
            .put("descripcion", "DID descubierto")

        val json = JSONObject(GuardarEnCatalogoTool(catalogo).call(JSONObject().put("entrada", entrada)))
        val guardada = catalogo.porId("descubierto.01")!!

        assertTrue(json.getBoolean("guardada"))
        assertEquals("bajo", guardada.riesgo)
        assertEquals("descubierto con RevScope", guardada.fuente)
        assertEquals("propia", guardada.licencia)
        assertFalse(guardada.verificado)
    }

    @Test
    fun `rechaza comandos de escritura bloqueados`() = kotlinx.coroutines.test.runTest {
        val catalogo = CatalogoPropietario({ null }, File(temporal.root, "catalogo.json"))
        val entrada = JSONObject()
            .put("id", "descubierto.01")
            .put("marca", "Mazda")
            .put("tipo", "lectura")
            .put("pasos", org.json.JSONArray().put("2E F1 90"))
            .put("descripcion", "DID")

        val json = JSONObject(GuardarEnCatalogoTool(catalogo).call(JSONObject().put("entrada", entrada)))

        assertTrue(json.has("error"))
    }
}
