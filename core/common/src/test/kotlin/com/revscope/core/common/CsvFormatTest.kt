package com.revscope.core.common

import com.revscope.core.common.export.CsvFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvFormatTest {

    @Test
    fun `nulos vacios y numeros con punto decimal`() {
        assertEquals("1790000102345,,14.9,49", CsvFormat.linea(listOf(1_790_000_102_345L, null, 14.9, "49")))
    }

    @Test
    fun `escapa comas comillas y saltos de linea`() {
        assertEquals("\"a,b\",\"di \"\"hola\"\"\",\"x\ny\"", CsvFormat.linea(listOf("a,b", "di \"hola\"", "x\ny")))
    }
}
