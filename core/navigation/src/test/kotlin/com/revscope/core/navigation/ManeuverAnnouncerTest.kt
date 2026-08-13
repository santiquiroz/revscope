package com.revscope.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverAnnouncerTest {

    private val announcer = ManeuverAnnouncer()

    private fun state(
        distanceM: Int,
        index: Int = 1,
        type: String = "turn",
        modifier: String? = "right",
        street: String? = "Carrera 52",
        offRoute: Boolean = false,
        arrived: Boolean = false,
    ) = NavigationState(
        maneuver = Maneuver(type, modifier, street),
        maneuverIndex = index,
        distanceToManeuverM = distanceM,
        distanceRemainingM = distanceM + 500,
        durationRemainingS = 120,
        snapped = LatLon(6.24, -75.58),
        offRoute = offRoute,
        arrived = arrived,
    )

    // ── No repetirse ─────────────────────────────────────────────────────────

    @Test
    fun `el mismo aviso no se repite en actualizaciones seguidas`() {
        announcer.next(state(1_200))

        assertNull(announcer.next(state(1_190)))
        assertNull(announcer.next(state(1_180)))
    }

    @Test
    fun `dentro de un mismo umbral solo se habla una vez`() {
        announcer.next(state(1_200))
        assertNotNull(announcer.next(state(390)))

        assertNull(announcer.next(state(300)))
        assertNull(announcer.next(state(200)))
    }

    @Test
    fun `cada umbral mas cercano vuelve a hablar`() {
        announcer.next(state(1_200))

        assertNotNull("400 m", announcer.next(state(390)))
        assertNotNull("150 m", announcer.next(state(140)))
        assertNotNull("inmediato", announcer.next(state(20)))
    }

    @Test
    fun `un salto grande entre lecturas no dispara los umbrales atrasados`() {
        announcer.next(state(1_200))

        assertNotNull(announcer.next(state(100)))
        // Ya pasó el de 150; el de 400 quedó saltado y no debe sonar después.
        assertNull(announcer.next(state(90)))
    }

    // ── Arranque ─────────────────────────────────────────────────────────────

    @Test
    fun `al arrancar se habla aunque la maniobra este lejisimos`() {
        val frase = announcer.next(state(3_000))

        assertNotNull("quedarse callado deja al conductor sin saber para dónde arrancar", frase)
        assertTrue(frase!!.contains("kilómetros"))
    }

    @Test
    fun `despues del aviso inicial los umbrales siguen funcionando`() {
        announcer.next(state(3_000))

        assertNull(announcer.next(state(2_000)))
        assertNotNull(announcer.next(state(380)))
    }

    // ── Cambio de maniobra ───────────────────────────────────────────────────

    @Test
    fun `una maniobra nueva vuelve a permitir todos los umbrales`() {
        announcer.next(state(1_200, index = 1))
        announcer.next(state(20, index = 1))

        assertNotNull(announcer.next(state(390, index = 2)))
        assertNotNull(announcer.next(state(140, index = 2)))
    }

    @Test
    fun `dos giros identicos seguidos se distinguen por el indice del paso`() {
        announcer.next(state(100, index = 1, street = "Calle 30"))

        val segunda = announcer.next(state(100, index = 2, street = "Calle 30"))
        assertNotNull("son maniobras distintas aunque digan lo mismo", segunda)
    }

    // ── Desvío y llegada ─────────────────────────────────────────────────────

    @Test
    fun `salirse de la ruta se avisa una sola vez`() {
        announcer.next(state(300))

        assertEquals("Se salió de la ruta, recalculando", announcer.next(state(300, offRoute = true)))
        assertNull(announcer.next(state(280, offRoute = true)))
    }

    @Test
    fun `volver a la ruta rearma el aviso de desvio`() {
        announcer.next(state(300, offRoute = true))
        announcer.next(state(300))

        assertNotNull(announcer.next(state(300, offRoute = true)))
    }

    @Test
    fun `estando fuera de ruta no se cantan maniobras`() {
        announcer.next(state(1_200))
        announcer.next(state(300, offRoute = true))

        assertNull(announcer.next(state(100, offRoute = true)))
    }

    @Test
    fun `la llegada se anuncia una sola vez`() {
        announcer.next(state(1_200))

        val llegada = announcer.next(state(0, type = "arrive", modifier = null, arrived = true))
        assertEquals("Llegó a su destino", llegada)
        assertNull(announcer.next(state(0, type = "arrive", modifier = null, arrived = true)))
    }

    @Test
    fun `sin maniobra no se dice nada`() {
        assertNull(announcer.next(NavigationState.IDLE))
    }

    @Test
    fun `reiniciar permite volver a anunciar todo`() {
        announcer.next(state(1_200))
        announcer.next(state(390))
        announcer.reset()

        assertNotNull(announcer.next(state(390)))
    }
}
