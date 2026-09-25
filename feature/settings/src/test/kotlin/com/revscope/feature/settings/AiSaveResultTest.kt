package com.revscope.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSaveResultTest {

    @Test
    fun `key guardada con valor informa exito y funciones IA activadas`() {
        val result = aiSaveResult(Result.success(true), keyEntered = true)

        assertTrue(result.success)
        assertEquals("Configuración de IA guardada — funciones IA activadas", result.message)
    }

    @Test
    fun `key vacia guardada informa exito sin activar IA`() {
        val result = aiSaveResult(Result.success(true), keyEntered = false)

        assertTrue(result.success)
        assertEquals("Configuración de IA guardada", result.message)
    }

    @Test
    fun `almacen seguro no disponible informa error visible y no exito`() {
        val result = aiSaveResult(Result.success(false), keyEntered = true)

        assertFalse(result.success)
        assertTrue(result.message.contains("almacén seguro"))
    }

    @Test
    fun `excepcion al guardar informa error generico`() {
        val result = aiSaveResult(Result.failure(IllegalStateException("disco")), keyEntered = true)

        assertFalse(result.success)
        assertEquals("Error guardando la configuración de IA", result.message)
    }
}
