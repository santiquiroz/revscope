package com.revscope.feature.workshop.taller.capturacan

import com.revscope.core.obd.escucha.EstadoCapturaRuta
import com.revscope.core.uitesting.XmlStringResolver
import com.revscope.feature.workshop.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturaRutaUiTest {

    private val es = XmlStringResolver.es(R::class.java)
    private val en = XmlStringResolver.en(R::class.java)

    @Test
    fun `cronometro en minutos y segundos con tope de 15 minutos`() {
        assertEquals("00:00", FormatoCapturaRuta.cronometro(-5))
        assertEquals("01:05", FormatoCapturaRuta.cronometro(65_400))
        assertEquals("15:00", FormatoCapturaRuta.tope)
    }

    @Test
    fun `velocidad redondeada o sin senal`() {
        assertEquals("43 km/h", FormatoCapturaRuta.velocidad(42.6f, es))
        assertEquals("sin señal", FormatoCapturaRuta.velocidad(null, es))
    }

    @Test
    fun `resumen de captura terminada sin gps lo dice`() {
        assertEquals(
            "120 tramas · sin muestras GPS · 00:30",
            FormatoCapturaRuta.resumenTerminada(EstadoCapturaRuta.Terminada("1-ruta", 120, 0, 30_000), es),
        )
    }

    @Test
    fun `resumen de captura terminada en ingles con plural`() {
        assertEquals(
            "1 frame · 1 GPS sample · 00:30",
            FormatoCapturaRuta.resumenTerminada(EstadoCapturaRuta.Terminada("1-ruta", 1, 1, 30_000), en),
        )
        assertEquals(
            "120 frames · 30 GPS samples · 00:30",
            FormatoCapturaRuta.resumenTerminada(EstadoCapturaRuta.Terminada("1-ruta", 120, 30, 30_000), en),
        )
        assertEquals("no signal", FormatoCapturaRuta.velocidad(null, en))
    }

    @Test
    fun `boton solo habilitado con conexion o capturando`() {
        val inactiva = CapturaRutaUi(EstadoCapturaRuta.Inactiva, conectado = false, etiqueta = "ruta", permisoGps = true)
        val capturando = inactiva.copy(estado = EstadoCapturaRuta.Capturando("ruta", 0, 0, 0, null))

        assertFalse(inactiva.botonHabilitado)
        assertTrue(inactiva.copy(conectado = true).botonHabilitado)
        assertTrue(capturando.botonHabilitado)
        assertEquals("Detener y guardar", es.get(capturando.textoBoton))
    }
}
