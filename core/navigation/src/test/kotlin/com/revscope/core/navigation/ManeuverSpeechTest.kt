package com.revscope.core.navigation

import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import java.util.Locale

class ManeuverSpeechTest {

    private val es = XmlStringResolver.es(R::class.java)
    private val en = XmlStringResolver.en(R::class.java)
    private val metric = UnitFormatter(UnitSystem.METRIC, Locale("es"))
    private val us = UnitFormatter(UnitSystem.US, Locale.US)
    private val uk = UnitFormatter(UnitSystem.UK, Locale.UK)

    // ── Distancia ────────────────────────────────────────────────────────────

    @Test
    fun `muy cerca se dice ahora y no una distancia`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", "Carrera 52"), distanceM = 20, es, metric)
        assertEquals("Ahora, gire a la derecha en Carrera 52", frase)
    }

    @Test
    fun `bajo el kilometro se redondea a cincuenta metros`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "left", null), distanceM = 237, es, metric)
        assertEquals("En 250 metros, gire a la izquierda", frase)
    }

    @Test
    fun `sobre el kilometro se dice en kilometros con un decimal`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), distanceM = 1240, es, metric)
        assertEquals("En 1,2 kilómetros, gire a la derecha", frase)
    }

    @Test
    fun `un kilometro exacto va en singular`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), distanceM = 1000, es, metric)
        assertEquals("En 1 kilómetro, gire a la derecha", frase)
    }

    @Test
    fun `el decimal usa coma y no punto porque lo lee un TTS en espanol`() {
        val frase = ManeuverSpeech.spoken(Maneuver("continue", null, null), distanceM = 2500, es, metric)
        assertTrue(frase.contains("2,5"))
        assertTrue(!frase.contains("2.5"))
    }

    // ── Maniobras ────────────────────────────────────────────────────────────

    @Test
    fun `llegada no lleva distancia ni calle`() {
        assertEquals("Llegó a su destino", ManeuverSpeech.spoken(Maneuver("arrive", null, "Carrera 52"), 15, es, metric))
    }

    @Test
    fun `salida lleva la calle con hacia y no con en`() {
        val frase = ManeuverSpeech.spoken(Maneuver("off ramp", "right", "Autopista Sur"), 400, es, metric)
        assertEquals("En 400 metros, tome la salida a la derecha hacia Autopista Sur", frase)
    }

    @Test
    fun `giro en u`() {
        assertEquals("Ahora, haga un giro en U", ManeuverSpeech.spoken(Maneuver("turn", "uturn", null), 10, es, metric))
    }

    @Test
    fun `giro leve y giro cerrado se distinguen`() {
        val leve = ManeuverSpeech.spoken(Maneuver("turn", "slight right", null), 100, es, metric)
        val cerrado = ManeuverSpeech.spoken(Maneuver("turn", "sharp right", null), 100, es, metric)
        assertTrue(leve.contains("levemente a la derecha"))
        assertTrue(cerrado.contains("cerrado a la derecha"))
    }

    @Test
    fun `glorieta`() {
        assertTrue(ManeuverSpeech.spoken(Maneuver("roundabout", "right", null), 150, es, metric).contains("glorieta"))
    }

    @Test
    fun `fin de via`() {
        val frase = ManeuverSpeech.spoken(Maneuver("end of road", "left", "Calle 30"), 80, es, metric)
        assertEquals("En 100 metros, al final de la vía gire a la izquierda en Calle 30", frase)
    }

    @Test
    fun `bifurcacion se mantiene a un lado`() {
        assertTrue(ManeuverSpeech.spoken(Maneuver("fork", "left", null), 200, es, metric).contains("manténgase a la izquierda"))
    }

    @Test
    fun `bifurcacion leve se dice sin el articulo sobrante`() {
        val frase = ManeuverSpeech.spoken(Maneuver("fork", "slight right", null), 200, es, metric)
        assertTrue(frase, frase.contains("manténgase levemente a la derecha"))
    }

    @Test
    fun `una maniobra desconocida no rompe y dice continue`() {
        val frase = ManeuverSpeech.spoken(Maneuver("teletransporte", "diagonal", null), 100, es, metric)
        assertEquals("En 100 metros, continúe", frase)
    }

    @Test
    fun `sin nombre de calle no queda un en colgando`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), 100, es, metric)
        assertTrue(!frase.trimEnd().endsWith("en"))
        assertEquals("En 100 metros, gire a la derecha", frase)
    }

    @Test
    fun `un nombre de calle en blanco se trata como ausente`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", "   "), 100, es, metric)
        assertEquals("En 100 metros, gire a la derecha", frase)
    }

    @Test
    fun `la salida no se anuncia dos veces seguidas a la misma distancia`() {
        val m = Maneuver("turn", "right", "Carrera 52")
        assertEquals(ManeuverSpeech.spoken(m, 500, es, metric), ManeuverSpeech.spoken(m, 500, es, metric))
    }

    // ── Inglés ───────────────────────────────────────────────────────────────

    @Test
    fun `en ingles la distancia en metros va antes de la accion`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), 300, en, metric)
        assertEquals("In 300 meters, turn right", frase)
    }

    @Test
    fun `en ingles el fin de via con kilometros y calle usa punto decimal`() {
        val frase = ManeuverSpeech.spoken(Maneuver("end of road", "left", "Calle 30"), 1240, en, metric)
        assertEquals("In 1.2 kilometers, at the end of the road, turn left onto Calle 30", frase)
    }

    @Test
    fun `en ingles la salida lleva el lado y la calle`() {
        val frase = ManeuverSpeech.spoken(Maneuver("off ramp", "right", "Autopista Sur"), 400, en, metric)
        assertEquals("In 400 meters, take the exit on the right toward Autopista Sur", frase)
    }

    // ── Unidades imperiales ──────────────────────────────────────────────────

    @Test
    fun `en US bajo la decima de milla se dice en pies redondeados a cincuenta`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), 150, en, us)
        assertEquals("In 500 feet, turn right", frase)
    }

    @Test
    fun `en US sobre la decima de milla se dice en millas con un decimal`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "left", null), 400, en, us)
        assertEquals("In 0.2 miles, turn left", frase)
    }

    @Test
    fun `en US una milla va en singular aunque no caiga en metros enteros`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), 1609, en, us)
        assertEquals("In 1 mile, turn right", frase)
    }

    @Test
    fun `en US las millas enteras se dicen sin decimal`() {
        val frase = ManeuverSpeech.spoken(Maneuver("continue", null, null), 3219, en, us)
        assertEquals("In 2 miles, continue", frase)
    }

    @Test
    fun `en UK tambien se dice en pies y millas`() {
        assertEquals("In 500 feet, turn right", ManeuverSpeech.spoken(Maneuver("turn", "right", null), 150, en, uk))
        assertEquals("In 1.5 miles, turn right", ManeuverSpeech.spoken(Maneuver("turn", "right", null), 2414, en, uk))
    }

    @Test
    fun `en espanol con unidades imperiales se dice pies y millas con coma`() {
        assertEquals("En 500 pies, gire a la derecha", ManeuverSpeech.spoken(Maneuver("turn", "right", null), 150, es, us))
        assertEquals("En 1,5 millas, gire a la derecha", ManeuverSpeech.spoken(Maneuver("turn", "right", null), 2414, es, us))
    }

    @Test
    fun `ahora no depende del sistema de unidades`() {
        assertEquals("Now, turn right", ManeuverSpeech.spoken(Maneuver("turn", "right", null), 20, en, us))
    }
}
