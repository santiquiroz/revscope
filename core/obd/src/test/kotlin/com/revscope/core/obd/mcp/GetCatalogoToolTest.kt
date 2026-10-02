package com.revscope.core.obd.mcp

import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.catalogo.CatalogoPropietario
import com.revscope.core.obd.catalogo.CodecCatalogo
import com.revscope.core.obd.catalogo.EntradaCatalogo
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder

class GetCatalogoToolTest {
    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `lista coincidencias limita entradas y conserva total`() = runTest {
        val catalogo = CatalogoPropietario(
            { CodecCatalogo.escribir("aviso", listOf(entrada("a"), entrada("b"))) },
            File(temporal.root, "catalogo.json"),
        )
        val manager = mockk<ObdSessionManager>()
        every { manager.activeProfile } returns MutableStateFlow<VehicleProfileEntity?>(null)

        val json = JSONObject(GetCatalogoTool(catalogo, manager).call(JSONObject().put("limite", 1)))

        assertEquals(2, json.getInt("total"))
        assertEquals(1, json.getJSONArray("entradas").length())
        assertTrue(json.getString("aviso") == "aviso")
    }

    private fun entrada(id: String) = EntradaCatalogo(
        id, "Mazda", listOf("CX-30"), null, "PCM", "7E0", "lectura",
        listOf("22 21 21"), "A", "km/h", "Velocidad", false, "bajo", null, null, false, null,
    )
}
