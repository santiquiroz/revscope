package com.revscope.core.obd.catalogo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecCatalogoTest {
    @Test
    fun `escribe y lee una entrada con header nulo`() {
        val original = entrada(header = null)

        val (aviso, entradas) = CodecCatalogo.leer(CodecCatalogo.escribir("aviso", listOf(original)))

        assertEquals("aviso", aviso)
        assertEquals(original, entradas.single())
        assertTrue(CodecCatalogo.aJson(entradas.single()).isNull("header"))
    }

    @Test
    fun `omite entradas mal formadas sin descartar las validas`() {
        val buena = CodecCatalogo.aJson(entrada()).toString()
        val json = """{"aviso":"x","entradas":[{},$buena]}"""

        val entradas = CodecCatalogo.leer(json).second

        assertEquals(1, entradas.size)
        assertEquals("test.id", entradas.single().id)
    }

    private fun entrada(header: String? = "7E0") = EntradaCatalogo(
        id = "test.id", marca = "Mazda", modelos = listOf("CX-30"), anios = "2020",
        modulo = "PCM", header = header, tipo = "lectura", pasos = listOf("22 21 21"),
        formula = "B", unidad = "km/h", descripcion = "Velocidad", requiereSecurityAccess = false,
        riesgo = "bajo", fuente = "https://example.test", licencia = "CC-BY-SA-4.0",
        verificado = false, notas = null,
    )
}
