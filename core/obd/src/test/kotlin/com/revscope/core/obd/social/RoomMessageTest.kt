package com.revscope.core.obd.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMessageTest {

    @Test
    fun `parses pos with explicit type`() {
        val json = """{"type":"pos","rider":"ana","lat":6.25,"lon":-75.58,"speed_kmh":30.0,"heading_deg":90.0}"""

        val msg = RoomMessageParser.parse(json)

        assertTrue(msg is RoomMessage.Pos)
        val pos = msg as RoomMessage.Pos
        assertEquals("ana", pos.rider)
        assertEquals(6.25, pos.lat, 0.0001)
        assertEquals(-75.58, pos.lon, 0.0001)
        assertEquals(30.0, pos.speedKmh!!, 0.0001)
        assertEquals(90.0, pos.headingDeg!!, 0.0001)
    }

    @Test
    fun `parses pos without type as legacy position`() {
        val json = """{"rider":"beto","lat":6.30,"lon":-75.60,"speed_kmh":15.0}"""

        val msg = RoomMessageParser.parse(json)

        assertTrue(msg is RoomMessage.Pos)
        assertEquals("beto", (msg as RoomMessage.Pos).rider)
    }

    @Test
    fun `parses pos without speed and heading as null`() {
        val json = """{"type":"pos","rider":"ana","lat":6.25,"lon":-75.58}"""

        val pos = RoomMessageParser.parse(json) as RoomMessage.Pos

        assertNull(pos.speedKmh)
        assertNull(pos.headingDeg)
    }

    @Test
    fun `parses dest`() {
        val json = """{"type":"dest","rider":"ana","lat":6.3,"lon":-75.6,"name":"Chilis"}"""

        val msg = RoomMessageParser.parse(json)

        assertTrue(msg is RoomMessage.Dest)
        val dest = msg as RoomMessage.Dest
        assertEquals("ana", dest.rider)
        assertEquals(6.3, dest.lat, 0.0001)
        assertEquals(-75.6, dest.lon, 0.0001)
        assertEquals("Chilis", dest.name)
    }

    @Test
    fun `parses race start with start_at_ms`() {
        val json = """{"type":"race","rider":"ana","action":"start","start_at_ms":123456}"""

        val msg = RoomMessageParser.parse(json)

        assertTrue(msg is RoomMessage.Race)
        val race = msg as RoomMessage.Race
        assertEquals("ana", race.rider)
        assertEquals("start", race.action)
        assertEquals(123456L, race.startAtMs)
    }

    @Test
    fun `parses race stop without start_at_ms`() {
        val json = """{"type":"race","rider":"ana","action":"stop"}"""

        val race = RoomMessageParser.parse(json) as RoomMessage.Race

        assertEquals("stop", race.action)
        assertNull(race.startAtMs)
    }

    @Test
    fun `parses room_state with dest null and race null`() {
        val json = """{"type":"room_state","dest":null,"race":null}"""

        val msg = RoomMessageParser.parse(json)

        assertTrue(msg is RoomMessage.RoomStateMsg)
        val state = msg as RoomMessage.RoomStateMsg
        assertNull(state.dest)
        assertNull(state.race)
    }

    @Test
    fun `parses room_state with populated dest and race`() {
        val json = """
            {"type":"room_state",
             "dest":{"type":"dest","rider":"ana","lat":6.3,"lon":-75.6,"name":"Chilis"},
             "race":{"type":"race","rider":"ana","action":"start","start_at_ms":123}}
        """.trimIndent()

        val state = RoomMessageParser.parse(json) as RoomMessage.RoomStateMsg

        assertEquals("Chilis", state.dest?.name)
        assertEquals("start", state.race?.action)
        assertEquals(123L, state.race?.startAtMs)
    }

    @Test
    fun `garbage returns null`() {
        assertNull(RoomMessageParser.parse("not json"))
    }

    @Test
    fun `pos missing required lat returns null`() {
        assertNull(RoomMessageParser.parse("""{"type":"pos","rider":"ana","lon":-75.58}"""))
    }

    @Test
    fun `unknown type returns null`() {
        assertNull(RoomMessageParser.parse("""{"type":"bogus","foo":"bar"}"""))
    }
}
