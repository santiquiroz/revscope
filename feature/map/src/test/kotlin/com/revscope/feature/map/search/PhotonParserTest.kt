package com.revscope.feature.map.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotonParserTest {

    @Test
    fun `respuesta vacia devuelve lista vacia`() {
        val json = """{"type":"FeatureCollection","features":[]}"""
        assertTrue(PhotonParser.parse(json).isEmpty())
    }

    @Test
    fun `json invalido no explota y devuelve vacio`() {
        assertTrue(PhotonParser.parse("no soy json").isEmpty())
    }

    @Test
    fun `las coordenadas vienen en orden lon lat`() {
        // Capturado del servicio real: Medellín, Colombia.
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "properties":{"name":"Medellín","city":"Medellín","state":"Antioquia","country":"Colombia"},
               "geometry":{"type":"Point","coordinates":[-75.573553,6.2443382]}}
            ]}
        """.trimIndent()
        val place = PhotonParser.parse(json).single()
        assertEquals(6.2443382, place.lat, 1e-7)
        assertEquals(-75.573553, place.lon, 1e-7)
    }

    @Test
    fun `el subtitulo encadena calle ciudad departamento y pais`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "properties":{"name":"Barrio Santa Fe","street":"Carrera 52","city":"Medellín","state":"Antioquia","country":"Colombia"},
               "geometry":{"type":"Point","coordinates":[-75.58,6.24]}}
            ]}
        """.trimIndent()
        val place = PhotonParser.parse(json).single()
        assertEquals("Barrio Santa Fe", place.name)
        assertEquals("Carrera 52, Medellín, Antioquia, Colombia", place.subtitle)
    }

    @Test
    fun `el subtitulo no repite el nombre`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "properties":{"name":"Carrera 52","street":"Carrera 52","city":"Medellín","country":"Colombia"},
               "geometry":{"type":"Point","coordinates":[-75.58,6.24]}}
            ]}
        """.trimIndent()
        val place = PhotonParser.parse(json).single()
        assertEquals("Medellín, Colombia", place.subtitle)
    }

    @Test
    fun `el numero de casa se antepone a la calle`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "properties":{"name":"Casa","housenumber":"31AA","street":"Calle 52","city":"Medellín"},
               "geometry":{"type":"Point","coordinates":[-75.58,6.24]}}
            ]}
        """.trimIndent()
        val place = PhotonParser.parse(json).single()
        assertEquals("Calle 52 31AA, Medellín", place.subtitle)
    }

    @Test
    fun `un lugar sin contexto queda con subtitulo vacio pero valido`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"name":"Algo"},
               "geometry":{"type":"Point","coordinates":[-75.58,6.24]}}
            ]}
        """.trimIndent()
        val place = PhotonParser.parse(json).single()
        assertEquals("Algo", place.name)
        assertEquals("", place.subtitle)
    }

    @Test
    fun `una feature sin nombre usable se descarta`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"city":"Medellín"},
               "geometry":{"type":"Point","coordinates":[-75.58,6.24]}},
              {"type":"Feature","properties":{"name":"Válido"},
               "geometry":{"type":"Point","coordinates":[-75.58,6.24]}}
            ]}
        """.trimIndent()
        val places = PhotonParser.parse(json)
        assertEquals(1, places.size)
        assertEquals("Válido", places.single().name)
    }

    @Test
    fun `una feature sin geometria se descarta`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"name":"Sin punto"}},
              {"type":"Feature","properties":{"name":"Con punto"},
               "geometry":{"type":"Point","coordinates":[-75.58,6.24]}}
            ]}
        """.trimIndent()
        assertEquals("Con punto", PhotonParser.parse(json).single().name)
    }
}
