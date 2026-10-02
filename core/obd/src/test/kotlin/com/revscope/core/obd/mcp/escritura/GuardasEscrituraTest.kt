package com.revscope.core.obd.mcp.escritura

import com.revscope.core.obd.model.ObdReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardasEscrituraTest {

    private val ahoraMs = 100_000L

    @Test
    fun `rechaza cuando no hay enlace`() {
        val rechazo = evaluar(conectado = false, lecturas = mapOf(velocidad()))

        assertEquals(RechazoGuarda.SinEnlace, rechazo)
        assertTextoNoVacio(rechazo)
    }

    @Test
    fun `rechaza cuando falta velocidad reciente`() {
        val rechazo = evaluar()

        assertEquals(RechazoGuarda.SinVelocidadReciente, rechazo)
        assertTextoNoVacio(rechazo)
    }

    @Test
    fun `rechaza velocidad con mas de dos segundos`() {
        val rechazo = evaluar(lecturas = mapOf(velocidad(timestamp = ahoraMs - 2_001)))

        assertEquals(RechazoGuarda.SinVelocidadReciente, rechazo)
        assertTextoNoVacio(rechazo)
    }

    @Test
    fun `rechaza movimiento e informa velocidad`() {
        val rechazo = evaluar(lecturas = mapOf(velocidad(12.0)))

        assertEquals(RechazoGuarda.EnMovimiento(12), rechazo)
        assertTextoNoVacio(rechazo)
    }

    @Test
    fun `detenido no requiere rpm cuando no se exige motor apagado`() {
        val rechazo = evaluar(lecturas = mapOf(velocidad()), requiereMotorApagado = false)

        assertNull(rechazo)
    }

    @Test
    fun `requiere rpm reciente cuando se exige motor apagado`() {
        val rechazo = evaluar(
            lecturas = mapOf(velocidad()),
            requiereMotorApagado = true,
        )

        assertEquals(RechazoGuarda.SinRpmReciente, rechazo)
        assertTextoNoVacio(rechazo)
    }

    @Test
    fun `rechaza motor encendido`() {
        val rechazo = evaluar(
            lecturas = mapOf(velocidad(), rpm(850.0)),
            requiereMotorApagado = true,
        )

        assertEquals(RechazoGuarda.MotorEncendido(850), rechazo)
        assertTextoNoVacio(rechazo)
    }

    @Test
    fun `acepta rpm cero reciente`() {
        val rechazo = evaluar(
            lecturas = mapOf(velocidad(), rpm(0.0)),
            requiereMotorApagado = true,
        )

        assertNull(rechazo)
    }

    private fun evaluar(
        conectado: Boolean = true,
        lecturas: Map<String, ObdReading> = emptyMap(),
        requiereMotorApagado: Boolean = false,
    ) = GuardasEscritura.evaluar(conectado, lecturas, requiereMotorApagado, ahoraMs)

    private fun velocidad(valor: Double = 0.0, timestamp: Long = ahoraMs - 100) =
        "0D" to ObdReading("0D", valor, "km/h", timestamp)

    private fun rpm(valor: Double, timestamp: Long = ahoraMs - 100) =
        "0C" to ObdReading("0C", valor, "rpm", timestamp)

    private fun assertTextoNoVacio(rechazo: RechazoGuarda?) {
        assertNotNull(rechazo)
        assertTrue(GuardasEscritura.texto(rechazo!!).isNotBlank())
    }
}
