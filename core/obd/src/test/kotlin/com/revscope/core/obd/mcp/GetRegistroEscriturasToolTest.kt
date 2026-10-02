package com.revscope.core.obd.mcp

import com.revscope.core.obd.mcp.escritura.EntradaRegistro
import com.revscope.core.obd.mcp.escritura.RegistroEscrituras
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GetRegistroEscriturasToolTest {

    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    @Test
    fun `expone entradas recientes con fecha ISO local sin consultar el ECU`() = runTest {
        val registro = RegistroEscrituras(File(carpetaTemporal.root, "escrituras.jsonl"))
        registro.registrar(
            EntradaRegistro(
                ts = 1_700_000_000_000,
                tool = "comando_escritura",
                resumen = "prueba",
                header = "7E0",
                pasos = listOf("2E F1 90"),
                autorizacion = "rechazada",
                respuestas = listOf("7F 2E 33"),
                error = "sin autorización",
            ),
        )

        val json = JSONObject(GetRegistroEscriturasTool(registro).call(JSONObject()))
        val entrada = json.getJSONArray("escrituras").getJSONObject(0)

        assertEquals("comando_escritura", entrada.getString("tool"))
        assertEquals("rechazada", entrada.getString("autorizacion"))
        assertEquals("7E0", entrada.getString("header"))
        assertEquals("sin autorización", entrada.getString("error"))
        assertEquals(1_700_000_000_000, entrada.getLong("ts"))
        assertEquals(true, entrada.getString("fecha").startsWith("2023-"))
    }

    @Test
    fun `limita la cantidad solicitada al rango admitido`() = runTest {
        val registro = RegistroEscrituras(File(carpetaTemporal.root, "escrituras.jsonl"))
        registro.registrar(
            EntradaRegistro(
                ts = 1L,
                tool = "tool",
                resumen = "una entrada",
                header = null,
                pasos = emptyList(),
                autorizacion = "ninguna",
            ),
        )

        val json = JSONObject(GetRegistroEscriturasTool(registro).call(JSONObject().put("n", 0)))

        assertEquals(1, json.getJSONArray("escrituras").length())
    }
}
