package com.revscope.core.obd.cameras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MEDELLIN_LAT = 6.2442
private const val MEDELLIN_LON = -75.5812

class AnsvCameraParserTest {

    @Test
    fun `parses operational location within radius with maxspeed`() {
        val json = """
            {"results":[{"ubicaciones":[
                {"id":191,"latitud":$MEDELLIN_LAT,"longitud":$MEDELLIN_LON,
                 "estado_operacion":"Operando","velocidad_maxima_permitida":"50"}
            ]}]}
        """.trimIndent()

        val cameras = AnsvCameraParser.parse(json, MEDELLIN_LAT, MEDELLIN_LON, 50_000.0)

        assertEquals(1, cameras.size)
        assertEquals(-191L, cameras[0].osmId)
        assertEquals(50, cameras[0].maxSpeedKmh)
    }

    @Test
    fun `excludes locations not marked as operando`() {
        val json = """
            {"results":[{"ubicaciones":[
                {"id":1,"latitud":$MEDELLIN_LAT,"longitud":$MEDELLIN_LON,
                 "estado_operacion":"Vencida","velocidad_maxima_permitida":"50"}
            ]}]}
        """.trimIndent()

        val cameras = AnsvCameraParser.parse(json, MEDELLIN_LAT, MEDELLIN_LON, 50_000.0)

        assertTrue(cameras.isEmpty())
    }

    @Test
    fun `excludes locations outside the requested radius`() {
        val farLat = MEDELLIN_LAT + 5.0 // several hundred km away
        val json = """
            {"results":[{"ubicaciones":[
                {"id":1,"latitud":$farLat,"longitud":$MEDELLIN_LON,
                 "estado_operacion":"Operando","velocidad_maxima_permitida":"50"}
            ]}]}
        """.trimIndent()

        val cameras = AnsvCameraParser.parse(json, MEDELLIN_LAT, MEDELLIN_LON, 50_000.0)

        assertTrue(cameras.isEmpty())
    }

    @Test
    fun `treats the literal string None as no maxspeed`() {
        val json = """
            {"results":[{"ubicaciones":[
                {"id":1,"latitud":$MEDELLIN_LAT,"longitud":$MEDELLIN_LON,
                 "estado_operacion":"Operando","velocidad_maxima_permitida":"None"}
            ]}]}
        """.trimIndent()

        val cameras = AnsvCameraParser.parse(json, MEDELLIN_LAT, MEDELLIN_LON, 50_000.0)

        assertNull(cameras[0].maxSpeedKmh)
    }

    @Test
    fun `skips a record with a malformed latitud without aborting the rest of the batch`() {
        val json = """
            {"results":[{"ubicaciones":[
                {"id":1,"latitud":"None","longitud":$MEDELLIN_LON,
                 "estado_operacion":"Operando","velocidad_maxima_permitida":"50"},
                {"id":2,"latitud":$MEDELLIN_LAT,"longitud":$MEDELLIN_LON,
                 "estado_operacion":"Operando","velocidad_maxima_permitida":"40"}
            ]}]}
        """.trimIndent()

        val cameras = AnsvCameraParser.parse(json, MEDELLIN_LAT, MEDELLIN_LON, 50_000.0)

        assertEquals(1, cameras.size)
        assertEquals(-2L, cameras[0].osmId)
    }

    @Test
    fun `flattens multiple solicitudes and multiple ubicaciones each`() {
        val json = """
            {"results":[
                {"ubicaciones":[
                    {"id":1,"latitud":$MEDELLIN_LAT,"longitud":$MEDELLIN_LON,
                     "estado_operacion":"Operando","velocidad_maxima_permitida":"30"}
                ]},
                {"ubicaciones":[
                    {"id":2,"latitud":$MEDELLIN_LAT,"longitud":$MEDELLIN_LON,
                     "estado_operacion":"Operando","velocidad_maxima_permitida":"40"},
                    {"id":3,"latitud":$MEDELLIN_LAT,"longitud":$MEDELLIN_LON,
                     "estado_operacion":"Operando","velocidad_maxima_permitida":"60"}
                ]}
            ]}
        """.trimIndent()

        val cameras = AnsvCameraParser.parse(json, MEDELLIN_LAT, MEDELLIN_LON, 50_000.0)

        assertEquals(3, cameras.size)
    }
}
