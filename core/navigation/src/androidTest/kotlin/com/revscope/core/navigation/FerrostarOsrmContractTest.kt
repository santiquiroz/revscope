package com.revscope.core.navigation

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.createRouteFromOsrm
import uniffi.ferrostar.createRouteFromOsrmRoute

/**
 * Qué entrega Ferrostar cuando se le da una respuesta del OSRM **público** — no la de Mapbox
 * ni la de Stadia. Corre en el dispositivo porque el núcleo de Ferrostar es Rust y solo hay
 * binarios de Android.
 *
 * La respuesta de `src/androidTest/assets/osrm-medellin.json` es real: una ruta de Medellín
 * pedida con `overview=full&geometries=polyline&steps=true`.
 *
 * De esto depende una decisión de arquitectura: si Ferrostar no puede darnos el tipo de
 * maniobra, las instrucciones tienen que salir de nuestro propio parser.
 */
class FerrostarOsrmContractTest {

    private fun osrmBody(): String =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("osrm-medellin.json").use { it.readBytes().decodeToString() }

    private fun waypoints() = listOf(
        Waypoint(GeographicCoordinate(6.2442, -75.5812), WaypointKind.BREAK, null),
        Waypoint(GeographicCoordinate(6.2100, -75.5650), WaypointKind.BREAK, null),
    )

    /** Ruta suelta: `routes[0]` más los waypoints como objetos, sin JSON inventado. */
    private fun ferrostarRoute() = createRouteFromOsrmRoute(
        JSONObject(osrmBody()).getJSONArray("routes").getJSONObject(0).toString().toByteArray(),
        waypoints(),
        POLYLINE_PRECISION,
    )

    @Test
    fun la_via_de_respuesta_completa_no_sirve_con_el_osrm_publico() {
        // Medido: `createRouteFromOsrm` rechaza la respuesta con "missing field `duration`".
        // Por eso producción usa `createRouteFromOsrmRoute` con `routes[0]` y los waypoints
        // como objetos. Si algún día esto deja de fallar, se puede simplificar.
        val resultado = runCatching {
            createRouteFromOsrm(
                osrmBody().toByteArray(),
                JSONObject(osrmBody()).getJSONArray("waypoints").toString().toByteArray(),
                POLYLINE_PRECISION,
            )
        }

        assertTrue("ya no falla: revisar si conviene simplificar", resultado.isFailure)
    }

    @Test
    fun ferrostar_parsea_una_ruta_del_osrm_publico() {
        val route = ferrostarRoute()

        assertTrue("la geometría no puede venir vacía", route.geometry.size > 10)
        assertTrue("debe haber pasos", route.steps.isNotEmpty())
    }

    @Test
    fun ferrostar_y_nuestro_parser_ven_los_mismos_pasos_en_el_mismo_orden() {
        val ferrostar = ferrostarRoute()
        val nuestra = OsrmRouteParser.parse(osrmBody(), POLYLINE_PRECISION.toInt())!!

        assertEquals(
            "si los conteos no coinciden, no se pueden cruzar por índice",
            ferrostar.steps.size,
            nuestra.steps.size,
        )
        ferrostar.steps.forEachIndexed { index, step ->
            assertEquals(
                "el paso $index debe ser la misma vía en ambos parsers",
                step.roadName.orEmpty(),
                nuestra.steps[index].maneuver.streetName.orEmpty(),
            )
            assertEquals(
                "el paso $index debe medir lo mismo en ambos parsers",
                step.distance,
                nuestra.steps[index].distanceM,
                0.5,
            )
        }
    }

    @Test
    fun sin_bannerInstructions_ferrostar_no_puede_decir_que_maniobra_es() {
        val route = ferrostarRoute()

        // El OSRM público no manda `bannerInstructions` ni `voiceInstructions` — son
        // extensiones de Mapbox. Sin esa fuente Ferrostar se queda sin nada que traducir, y
        // su texto de instrucción es un marcador de posición: la síntesis desde OSRM no está
        // implementada río arriba. Por eso las instrucciones las arma ManeuverSpeech.
        assertEquals("visualInstructions", 0, route.steps.count { it.visualInstructions.isNotEmpty() })
        assertEquals("spokenInstructions", 0, route.steps.count { it.spokenInstructions.isNotEmpty() })
        assertEquals(
            "si Ferrostar implementó la síntesis, vale la pena reevaluar ManeuverSpeech",
            setOf("TODO: OSRM instruction synthesis"),
            route.steps.map { it.instruction }.toSet(),
        )
    }

    @Test
    fun nuestro_parser_si_tiene_el_tipo_de_maniobra_de_cada_paso() {
        val nuestra = OsrmRouteParser.parse(osrmBody(), POLYLINE_PRECISION.toInt())!!

        assertEquals("depart", nuestra.steps.first().maneuver.type)
        assertEquals("arrive", nuestra.steps.last().maneuver.type)
        assertTrue(
            "debe haber giros con lado",
            nuestra.steps.any { it.maneuver.type == "turn" && it.maneuver.modifier != null },
        )
    }

    private companion object {
        /** `geometries=polyline` ⇒ precisión 5. Ver PolylineDecoder. */
        val POLYLINE_PRECISION = 5u
    }
}
