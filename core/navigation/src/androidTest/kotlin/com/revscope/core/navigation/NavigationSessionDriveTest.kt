package com.revscope.core.navigation

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.LocationBias
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.advanceLocationSimulation
import uniffi.ferrostar.createRouteFromOsrmRoute
import uniffi.ferrostar.locationSimulationFromRoute

/**
 * Recorre la ruta completa con el simulador de ubicación de Ferrostar y comprueba que la
 * sesión progresa como debe. Es la prueba que de verdad importa: el cruce por índice entre
 * los pasos de Ferrostar y nuestras maniobras solo se ve fallar en movimiento.
 */
class NavigationSessionDriveTest {

    private fun osrmBody(): String =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("osrm-medellin.json").use { it.readBytes().decodeToString() }

    private fun routeJson(): String =
        JSONObject(osrmBody()).getJSONArray("routes").getJSONObject(0).toString()

    private fun session() = NavigationSession.create(
        osrmRouteJson = routeJson(),
        route = OsrmRouteParser.parse(osrmBody(), PRECISION)!!,
        origin = ORIGIN,
        destination = DESTINATION,
        polylinePrecision = PRECISION,
    )

    /** Estados a lo largo de todo el recorrido, un punto por muestra del simulador. */
    private fun drive(): List<NavigationState> {
        val session = requireNotNull(session()) { "la sesión no debería fallar al crearse" }
        return session.use {
            var simulation = locationSimulationFromRoute(
                createRouteFromOsrmRoute(
                    routeJson().toByteArray(),
                    listOf(waypoint(ORIGIN), waypoint(DESTINATION)),
                    PRECISION.toUInt(),
                ),
                SAMPLE_SPACING_M,
                LocationBias.None,
            )
            val states = mutableListOf(it.start(simulation.currentLocation.toFix(0)))
            var tick = 1
            while (simulation.remainingLocations.isNotEmpty() && tick < MAX_TICKS) {
                simulation = advanceLocationSimulation(simulation)
                states += it.update(simulation.currentLocation.toFix(tick * 1_000L))
                tick++
            }
            states
        }
    }

    @Test
    fun al_arrancar_ya_hay_una_maniobra_que_anunciar() {
        val primero = drive().first()

        assertNotNull("sin maniobra no hay nada que decirle al conductor", primero.maneuver)
        assertFalse(primero.arrived)
        assertTrue("debe faltar camino", primero.distanceRemainingM > 100)
    }

    @Test
    fun recorrer_la_ruta_entera_termina_en_llegada() {
        val estados = drive()

        assertTrue(
            "la ruta debería terminar en llegada; terminó en ${estados.last()}",
            estados.last().arrived,
        )
    }

    @Test
    fun lo_que_falta_baja_y_solo_retrocede_lo_que_cabe_en_un_reenganche() {
        val restantes = drive().filterNot { it.arrived }.map { it.distanceRemainingM }

        assertTrue("debe bajar de punta a punta", restantes.last() < restantes.first() / 2)
        // Al cambiar de paso, la posición enganchada puede correrse unos metros hacia atrás.
        // Eso es real; lo que no puede pasar es que la distancia salte hacia arriba.
        val saltoAtras = restantes.zipWithNext().maxOf { (antes, despues) -> despues - antes }
        assertTrue("retrocedió $saltoAtras m de golpe", saltoAtras <= MAX_BACKTRACK_M)
    }

    @Test
    fun yendo_exactamente_por_la_ruta_nunca_se_reporta_desvio() {
        assertEquals(0, drive().count { it.offRoute })
    }

    @Test
    fun las_maniobras_avanzan_y_no_retroceden() {
        val estados = drive()
        val orden = estados.mapNotNull { estado ->
            estado.maneuver?.let { maniobra -> OsrmRouteParser.parse(osrmBody(), PRECISION)!!.steps
                .indexOfFirst { it.maneuver == maniobra } }
        }

        assertTrue("debe pasar por varias maniobras", orden.distinct().size > 3)
        assertEquals(
            "una maniobra ya pasada no puede volver a anunciarse",
            0,
            orden.zipWithNext().count { (antes, despues) -> despues < antes },
        )
    }

    @Test
    fun la_frase_que_se_dice_al_arrancar_es_una_instruccion_de_verdad() {
        val primero = drive().first()

        val frase = ManeuverSpeech.spoken(primero.maneuver!!, primero.distanceToManeuverM)
        assertTrue("debe hablar de distancia: $frase", frase.startsWith("En ") || frase.startsWith("Ahora"))
        assertFalse("no puede quedar una preposición colgando: $frase", frase.trimEnd().endsWith(" en"))
    }

    private fun waypoint(at: LatLon) =
        Waypoint(GeographicCoordinate(at.lat, at.lon), WaypointKind.BREAK, null)

    private fun uniffi.ferrostar.UserLocation.toFix(timestampMs: Long) = LocationFix(
        lat = coordinates.lat,
        lon = coordinates.lng,
        accuracyM = 5.0,
        bearingDeg = courseOverGround?.degrees?.toInt(),
        speedMps = 11.0,
        timestampMs = timestampMs,
    )

    private companion object {
        // Sin `const`: el compilador revienta intentando evaluar `const.toUInt()` en tiempo
        // de compilación (Internal error in file lowering, Kotlin 2.2.21).
        val PRECISION = 5

        /** Un punto cada tantos metros: suficiente para no saltarse una maniobra. */
        const val SAMPLE_SPACING_M = 10.0

        /** Tope de seguridad: si el simulador no termina, el test falla por aserción, no colgado. */
        const val MAX_TICKS = 5_000

        /** Cuánto puede correrse hacia atrás la distancia restante al reenganchar. */
        const val MAX_BACKTRACK_M = 5

        val ORIGIN = LatLon(6.2442, -75.5812)
        val DESTINATION = LatLon(6.2100, -75.5650)
    }
}
