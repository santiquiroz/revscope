package com.revscope.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmRouteParserTest {

    /** Forma real de la respuesta de router.project-osrm.org con `steps=true`. */
    private fun body(
        code: String = "Ok",
        geometry: String = "_p~iF~ps|U_ulLnnqC",
        legs: String = DEFAULT_LEGS,
    ) = """
        {
          "code": "$code",
          "routes": [{
            "geometry": "$geometry",
            "distance": 1234.5,
            "duration": 300.2,
            "legs": [$legs]
          }]
        }
    """.trimIndent()

    @Test
    fun `extrae distancia duracion y geometria`() {
        val route = OsrmRouteParser.parse(body(), precision = 5)

        assertNotNull(route)
        assertEquals(1234.5, route!!.distanceM, 0.01)
        assertEquals(300.2, route.durationS, 0.01)
        assertEquals(2, route.points.size)
    }

    @Test
    fun `extrae los pasos con su maniobra`() {
        val route = OsrmRouteParser.parse(body(), precision = 5)!!

        assertEquals(2, route.steps.size)
        assertEquals("depart", route.steps[0].maneuver.type)
        assertEquals("Carrera 52", route.steps[0].maneuver.streetName)
        assertEquals("turn", route.steps[1].maneuver.type)
        assertEquals("right", route.steps[1].maneuver.modifier)
        assertEquals(120.0, route.steps[1].distanceM, 0.01)
    }

    @Test
    fun `la ubicacion de la maniobra viene como lon lat y se guarda al derecho`() {
        val route = OsrmRouteParser.parse(body(), precision = 5)!!

        assertEquals(6.2442, route.steps[0].maneuverAt.lat, 1e-6)
        assertEquals(-75.5812, route.steps[0].maneuverAt.lon, 1e-6)
    }

    @Test
    fun `los pasos de varios tramos quedan en una sola secuencia`() {
        val route = OsrmRouteParser.parse(body(legs = "$DEFAULT_LEGS,$DEFAULT_LEGS"), precision = 5)!!

        assertEquals(4, route.steps.size)
    }

    @Test
    fun `un codigo distinto de Ok no produce ruta`() {
        assertNull(OsrmRouteParser.parse(body(code = "NoRoute"), precision = 5))
    }

    @Test
    fun `una geometria vacia no produce ruta`() {
        assertNull(OsrmRouteParser.parse(body(geometry = ""), precision = 5))
    }

    @Test
    fun `un cuerpo que no es json no revienta`() {
        assertNull(OsrmRouteParser.parse("<html>502 Bad Gateway</html>", precision = 5))
    }

    @Test
    fun `sin legs la ruta sigue sirviendo para dibujar`() {
        val route = OsrmRouteParser.parse(body(legs = ""), precision = 5)

        assertNotNull(route)
        assertTrue(route!!.steps.isEmpty())
        assertEquals(2, route.points.size)
    }

    @Test
    fun `un modifier ausente queda en null y no en cadena vacia`() {
        val route = OsrmRouteParser.parse(body(), precision = 5)!!

        assertNull(route.steps[0].maneuver.modifier)
    }

    @Test
    fun `un nombre de via vacio queda en null para que la voz no lo mencione`() {
        val legs = """
            {"steps":[{"distance":50.0,"duration":10.0,"name":"",
              "maneuver":{"location":[-75.5812,6.2442],"type":"turn","modifier":"left"}}]}
        """.trimIndent()
        val route = OsrmRouteParser.parse(body(legs = legs), precision = 5)!!

        assertNull(route.steps[0].maneuver.streetName)
    }

    @Test
    fun `la precision se aplica tambien a la geometria del paso`() {
        // Sin `location`, la posición de la maniobra sale del primer punto de su geometría.
        val legs = """
            {"steps":[{"distance":50.0,"duration":10.0,"name":"Calle 30","geometry":"_p~iF~ps|U",
              "maneuver":{"type":"turn","modifier":"left"}}]}
        """.trimIndent()
        val route = OsrmRouteParser.parse(body(legs = legs), precision = 5)!!

        assertEquals(38.5, route.steps[0].maneuverAt.lat, 1e-5)
    }

    @Test
    fun `un paso sin maniobra ni geometria se descarta sin tumbar la ruta`() {
        val legs = """
            {"steps":[{"distance":50.0,"duration":10.0,"name":"Calle 30"},
              {"distance":120.0,"duration":30.0,"name":"Calle 33",
               "maneuver":{"location":[-75.58,6.24],"type":"turn","modifier":"right"}}]}
        """.trimIndent()
        val route = OsrmRouteParser.parse(body(legs = legs), precision = 5)!!

        assertEquals(1, route.steps.size)
        assertEquals("turn", route.steps[0].maneuver.type)
    }

    private companion object {
        const val DEFAULT_LEGS = """
            {"steps":[
              {"distance":80.0,"duration":20.0,"name":"Carrera 52",
               "maneuver":{"location":[-75.5812,6.2442],"type":"depart"}},
              {"distance":120.0,"duration":30.0,"name":"Calle 33",
               "maneuver":{"location":[-75.5801,6.2450],"type":"turn","modifier":"right"}}
            ]}
        """
    }
}
