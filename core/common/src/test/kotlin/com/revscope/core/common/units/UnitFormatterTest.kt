package com.revscope.core.common.units

import com.revscope.core.common.i18n.Region
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnitFormatterTest {

    private val us = UnitFormatter(UnitSystem.US, Locale.US)
    private val uk = UnitFormatter(UnitSystem.UK, Locale.UK)
    private val metric = UnitFormatter(UnitSystem.METRIC, Locale.US)

    @Test
    fun `formatSpeed in US converts to mph without decimals`() {
        assertEquals("62 mph", us.formatSpeed(100.0))
    }

    @Test
    fun `formatSpeed in METRIC keeps km per hour`() {
        assertEquals("100 km/h", metric.formatSpeed(100.0))
    }

    @Test
    fun `temperature in US is Fahrenheit`() {
        assertEquals(212.0, us.temperature(100.0), 1e-9)
    }

    @Test
    fun `UK temperature label stays Celsius`() {
        assertEquals("°C", uk.temperatureLabel())
    }

    @Test
    fun `pressure in UK is psi`() {
        assertEquals(14.5, uk.pressure(100.0), 0.01)
        assertEquals("psi", uk.pressureLabel())
    }

    @Test
    fun `toMetricTemperature inverts Fahrenheit`() {
        assertEquals(105.0, us.toMetricTemperature(221.0), 1e-9)
    }

    @Test
    fun `toMetricSpeed round trips speed`() {
        assertEquals(88.0, us.toMetricSpeed(us.speed(88.0)), 1e-9)
    }

    @Test
    fun `toMetricDistance round trips distance`() {
        assertEquals(42.0, us.toMetricDistance(us.distance(42.0)), 1e-9)
    }

    @Test
    fun `formatDistance in METRIC with Spanish locale uses comma`() {
        val es = UnitFormatter(UnitSystem.METRIC, Locale("es"))
        assertEquals("10,0 km", es.formatDistance(10.0))
    }

    @Test
    fun `fuelEconomy in US is mpg`() {
        assertEquals(23.52, us.fuelEconomy(10.0), 0.01)
        assertEquals("mpg", us.fuelEconomyLabel())
    }

    @Test
    fun `fuelEconomy in UK uses imperial gallons`() {
        assertEquals(28.25, uk.fuelEconomy(10.0), 0.01)
    }

    @Test
    fun `volume is gallons only in US`() {
        assertEquals(1.0, us.volume(3.785411784), 1e-9)
        assertEquals("gal", us.volumeLabel())
        assertEquals("L", uk.volumeLabel())
        assertEquals(3.0, uk.volume(3.0), 1e-9)
    }

    @Test
    fun `formatPressure uses kPa without decimals and psi with one`() {
        assertEquals("100 kPa", metric.formatPressure(100.0))
        assertEquals("14.5 psi", us.formatPressure(100.0))
    }

    @Test
    fun `metric conversions are identity`() {
        assertEquals(37.5, metric.speed(37.5), 0.0)
        assertEquals(37.5, metric.distance(37.5), 0.0)
        assertEquals(37.5, metric.temperature(37.5), 0.0)
        assertEquals(37.5, metric.pressure(37.5), 0.0)
        assertEquals(37.5, metric.fuelEconomy(37.5), 0.0)
        assertEquals(37.5, metric.toMetricTemperature(37.5), 0.0)
    }

    @Test
    fun `defaultFor GB is UK`() {
        assertEquals(UnitSystem.UK, UnitSystem.defaultFor(Region.GB))
        assertEquals(UnitSystem.US, UnitSystem.defaultFor(Region.US))
        assertEquals(UnitSystem.METRIC, UnitSystem.defaultFor(Region.CO))
        assertEquals(UnitSystem.METRIC, UnitSystem.defaultFor(Region.OTHER))
    }

    @Test
    fun `fromTag empty uses region default`() {
        assertEquals(UnitSystem.US, UnitSystem.fromTag("", Region.US))
    }

    @Test
    fun `fromTag explicit wins over region`() {
        assertEquals(UnitSystem.METRIC, UnitSystem.fromTag("METRIC", Region.US))
    }

    @Test
    fun `fromTag garbage falls back to region default`() {
        assertEquals(UnitSystem.METRIC, UnitSystem.fromTag("garbage", Region.CO))
    }
}
