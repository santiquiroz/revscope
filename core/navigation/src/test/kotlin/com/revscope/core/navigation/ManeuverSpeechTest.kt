package com.revscope.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverSpeechTest {

    // ── Distancia ────────────────────────────────────────────────────────────

    @Test
    fun `muy cerca se dice ahora y no una distancia`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", "Carrera 52"), distanceM = 20)
        assertEquals("Ahora, gire a la derecha en Carrera 52", frase)
    }

    @Test
    fun `bajo el kilometro se redondea a cincuenta metros`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "left", null), distanceM = 237)
        assertEquals("En 250 metros, gire a la izquierda", frase)
    }

    @Test
    fun `sobre el kilometro se dice en kilometros con un decimal`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), distanceM = 1240)
        assertEquals("En 1,2 kilómetros, gire a la derecha", frase)
    }

    @Test
    fun `un kilometro exacto va en singular`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), distanceM = 1000)
        assertEquals("En 1 kilómetro, gire a la derecha", frase)
    }

    @Test
    fun `el decimal usa coma y no punto porque lo lee un TTS en espanol`() {
        val frase = ManeuverSpeech.spoken(Maneuver("continue", null, null), distanceM = 2500)
        assertTrue(frase.contains("2,5"))
        assertTrue(!frase.contains("2.5"))
    }

    // ── Maniobras ────────────────────────────────────────────────────────────

    @Test
    fun `llegada no lleva distancia ni calle`() {
        assertEquals("Llegó a su destino", ManeuverSpeech.spoken(Maneuver("arrive", null, "Carrera 52"), 15))
    }

    @Test
    fun `salida lleva la calle con hacia y no con en`() {
        val frase = ManeuverSpeech.spoken(Maneuver("off ramp", "right", "Autopista Sur"), 400)
        assertEquals("En 400 metros, tome la salida a la derecha hacia Autopista Sur", frase)
    }

    @Test
    fun `giro en u`() {
        assertEquals("Ahora, haga un giro en U", ManeuverSpeech.spoken(Maneuver("turn", "uturn", null), 10))
    }

    @Test
    fun `giro leve y giro cerrado se distinguen`() {
        val leve = ManeuverSpeech.spoken(Maneuver("turn", "slight right", null), 100)
        val cerrado = ManeuverSpeech.spoken(Maneuver("turn", "sharp right", null), 100)
        assertTrue(leve.contains("levemente a la derecha"))
        assertTrue(cerrado.contains("cerrado a la derecha"))
    }

    @Test
    fun `glorieta`() {
        assertTrue(ManeuverSpeech.spoken(Maneuver("roundabout", "right", null), 150).contains("glorieta"))
    }

    @Test
    fun `fin de via`() {
        val frase = ManeuverSpeech.spoken(Maneuver("end of road", "left", "Calle 30"), 80)
        assertEquals("En 100 metros, al final de la vía gire a la izquierda en Calle 30", frase)
    }

    @Test
    fun `bifurcacion se mantiene a un lado`() {
        assertTrue(ManeuverSpeech.spoken(Maneuver("fork", "left", null), 200).contains("manténgase a la izquierda"))
    }

    @Test
    fun `una maniobra desconocida no rompe y dice continue`() {
        val frase = ManeuverSpeech.spoken(Maneuver("teletransporte", "diagonal", null), 100)
        assertEquals("En 100 metros, continúe", frase)
    }

    @Test
    fun `sin nombre de calle no queda un en colgando`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", null), 100)
        assertTrue(!frase.trimEnd().endsWith("en"))
        assertEquals("En 100 metros, gire a la derecha", frase)
    }

    @Test
    fun `un nombre de calle en blanco se trata como ausente`() {
        val frase = ManeuverSpeech.spoken(Maneuver("turn", "right", "   "), 100)
        assertEquals("En 100 metros, gire a la derecha", frase)
    }

    @Test
    fun `la salida no se anuncia dos veces seguidas a la misma distancia`() {
        val m = Maneuver("turn", "right", "Carrera 52")
        assertEquals(ManeuverSpeech.spoken(m, 500), ManeuverSpeech.spoken(m, 500))
    }
}
