package com.revscope.core.obd.cameras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverpassCameraParserTest {

    @Test
    fun `parses node with lat lon and maxspeed tag`() {
        val json = """
            {"elements":[
                {"type":"node","id":123,"lat":6.25,"lon":-75.58,"tags":{"maxspeed":"50"}}
            ]}
        """.trimIndent()

        val cameras = OverpassCameraParser.parse(json)

        assertEquals(1, cameras.size)
        assertEquals(6.25, cameras[0].latitude, 0.0001)
        assertEquals(-75.58, cameras[0].longitude, 0.0001)
        assertEquals(50, cameras[0].maxSpeedKmh)
    }

    @Test
    fun `parses way center when lat lon are absent`() {
        val json = """
            {"elements":[
                {"type":"way","id":456,"center":{"lat":6.30,"lon":-75.60},"tags":{}}
            ]}
        """.trimIndent()

        val cameras = OverpassCameraParser.parse(json)

        assertEquals(1, cameras.size)
        assertEquals(6.30, cameras[0].latitude, 0.0001)
        assertEquals(-75.60, cameras[0].longitude, 0.0001)
        assertNull(cameras[0].maxSpeedKmh)
    }

    @Test
    fun `parses enforcement relation center`() {
        val json = """
            {"elements":[
                {"type":"relation","id":789,"center":{"lat":6.10,"lon":-75.50},
                 "tags":{"type":"enforcement","enforcement":"maxspeed","maxspeed":"40"}}
            ]}
        """.trimIndent()

        val cameras = OverpassCameraParser.parse(json)

        assertEquals(1, cameras.size)
        assertEquals(40, cameras[0].maxSpeedKmh)
    }

    @Test
    fun `skips element with neither lat lon nor center`() {
        val json = """{"elements":[{"type":"way","id":1,"tags":{}}]}"""

        val cameras = OverpassCameraParser.parse(json)

        assertTrue(cameras.isEmpty())
    }

    @Test
    fun `node way and relation with the same raw id do not collide`() {
        val json = """
            {"elements":[
                {"type":"node","id":100,"lat":1.0,"lon":1.0,"tags":{}},
                {"type":"way","id":100,"center":{"lat":2.0,"lon":2.0},"tags":{}},
                {"type":"relation","id":100,"center":{"lat":3.0,"lon":3.0},"tags":{}}
            ]}
        """.trimIndent()

        val cameras = OverpassCameraParser.parse(json)

        val distinctIds = cameras.map { it.osmId }.toSet()
        assertEquals(3, distinctIds.size)
    }

    @Test
    fun `skips an element missing coordinates without aborting the rest of the batch`() {
        val json = """
            {"elements":[
                {"type":"node","id":1,"lat":6.25,"lon":-75.58,"tags":{"maxspeed":"50"}},
                {"type":"way","id":2,"tags":{}},
                {"type":"node","id":3,"lat":6.30,"lon":-75.60,"tags":{}}
            ]}
        """.trimIndent()

        val cameras = OverpassCameraParser.parse(json)

        assertEquals(2, cameras.size)
        assertEquals(setOf(4L, 12L), cameras.map { it.osmId }.toSet())
    }

    @Test
    fun `non numeric maxspeed tag is ignored`() {
        val json = """
            {"elements":[
                {"type":"node","id":1,"lat":1.0,"lon":1.0,"tags":{"maxspeed":"walk"}}
            ]}
        """.trimIndent()

        val cameras = OverpassCameraParser.parse(json)

        assertNull(cameras[0].maxSpeedKmh)
    }
}
