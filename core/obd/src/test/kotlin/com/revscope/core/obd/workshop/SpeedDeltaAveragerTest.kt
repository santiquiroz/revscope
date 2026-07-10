package com.revscope.core.obd.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedDeltaAveragerTest {

    private val averager = SpeedDeltaAverager()

    @Test
    fun `sin muestras el promedio es null`() {
        assertNull(averager.average)
    }

    @Test
    fun `una muestra por encima del umbral fija el promedio a su delta`() {
        averager.addSample(obdKmh = 55.0, gpsKmh = 50.0)
        assertEquals(10.0, averager.average!!, 0.001)
    }

    @Test
    fun `muestras en o bajo el umbral se descartan`() {
        averager.addSample(obdKmh = 12.0, gpsKmh = 10.0)
        averager.addSample(obdKmh = 5.0, gpsKmh = 4.0)
        assertNull(averager.average)
    }

    @Test
    fun `promedia varias muestras validas`() {
        averager.addSample(obdKmh = 55.0, gpsKmh = 50.0) // +10%
        averager.addSample(obdKmh = 66.0, gpsKmh = 60.0) // +10%
        averager.addSample(obdKmh = 100.0, gpsKmh = 80.0) // +25%
        assertEquals(15.0, averager.average!!, 0.001)
    }

    @Test
    fun `delta negativo cuando el GPS marca mas que el OBD`() {
        averager.addSample(obdKmh = 45.0, gpsKmh = 50.0)
        assertEquals(-10.0, averager.average!!, 0.001)
    }

    @Test
    fun `reset olvida las muestras acumuladas`() {
        averager.addSample(obdKmh = 55.0, gpsKmh = 50.0)
        averager.reset()
        assertNull(averager.average)
    }

    @Test
    fun `deltaPercent calcula el porcentaje de sobre-marcaje`() {
        assertEquals(10.0, SpeedDeltaAverager.deltaPercent(obdKmh = 55.0, gpsKmh = 50.0), 0.001)
        assertEquals(0.0, SpeedDeltaAverager.deltaPercent(obdKmh = 50.0, gpsKmh = 50.0), 0.001)
    }
}
