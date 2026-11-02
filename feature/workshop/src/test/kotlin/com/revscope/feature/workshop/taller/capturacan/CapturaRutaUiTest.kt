package com.revscope.feature.workshop.taller.capturacan

import com.revscope.core.obd.escucha.EstadoCapturaRuta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturaRutaUiTest {

    @Test
    fun `cronometro en minutos y segundos con tope de 15 minutos`() {
        assertEquals("00:00", FormatoCapturaRuta.cronometro(-5))
        assertEquals("01:05", FormatoCapturaRuta.cronometro(65_400))
        assertEquals("15:00", FormatoCapturaRuta.tope)
    }

    @Test
    fun `velocidad redondeada o sin senal`() {
        assertEquals("43 km/h", FormatoCapturaRuta.velocidad(42.6f))
        assertEquals("sin señal", FormatoCapturaRuta.velocidad(null))
    }

    @Test
    fun `resumen de captura terminada sin gps lo dice`() {
        assertEquals(
            "120 tramas · sin muestras GPS · 00:30",
            FormatoCapturaRuta.resumenTerminada(EstadoCapturaRuta.Terminada("1-ruta", 120, 0, 30_000)),
        )
    }

    @Test
    fun `boton solo habilitado con conexion o capturando`() {
        val inactiva = CapturaRutaUi(EstadoCapturaRuta.Inactiva, conectado = false, etiqueta = "ruta", permisoGps = true)
        val capturando = inactiva.copy(estado = EstadoCapturaRuta.Capturando("ruta", 0, 0, 0, null))

        assertFalse(inactiva.botonHabilitado)
        assertTrue(inactiva.copy(conectado = true).botonHabilitado)
        assertTrue(capturando.botonHabilitado)
        assertEquals("Detener y guardar", capturando.textoBoton)
    }
}
