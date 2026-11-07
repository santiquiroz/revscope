package com.revscope.feature.settings

import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RadaresAlertasUnidadesTest {

    private val metric = UnitFormatter(UnitSystem.METRIC, Locale.US)
    private val us = UnitFormatter(UnitSystem.US, Locale.US)
    private val uk = UnitFormatter(UnitSystem.UK, Locale.UK)
    private val en = XmlStringResolver.en(R::class.java)
    private val es = XmlStringResolver.es(R::class.java)

    @Test
    fun `temperature threshold is shown in fahrenheit for US and stored back in celsius`() {
        assertEquals(221, temperatureFieldValue(105, us))
        assertEquals(105, temperatureFieldToCelsius(221, us))
    }

    @Test
    fun `temperature threshold stays in celsius for UK and metric`() {
        assertEquals(105, temperatureFieldValue(105, uk))
        assertEquals(105, temperatureFieldToCelsius(105, uk))
        assertEquals(105, temperatureFieldValue(105, metric))
    }

    @Test
    fun `camera radius is shown in feet for US and UK and stored back in meters`() {
        assertEquals(820, radiusFieldValue(250, us))
        assertEquals(250, radiusFieldToMeters(820, us))
        assertEquals(820, radiusFieldValue(250, uk))
        assertEquals("ft", radiusFieldUnit(us).label)
    }

    @Test
    fun `camera radius stays in meters for metric`() {
        assertEquals(333, radiusFieldValue(333, metric))
        assertEquals(333, radiusFieldToMeters(333, metric))
        assertEquals("m", radiusFieldUnit(metric).label)
    }

    @Test
    fun `camera download distance is converted to miles`() {
        assertEquals(31, cameraDownloadDistance(us))
        assertEquals(50, cameraDownloadDistance(metric))
    }

    @Test
    fun `saved radius message uses feet in US`() {
        assertEquals(
            "Alert radius: 820 ft",
            en.get(R.string.settings_camera_alert_radius_saved, radiusFieldValue(250, us), radiusFieldUnit(us).label),
        )
    }

    @Test
    fun `invalid radius message names feet in US`() {
        assertEquals(
            "Invalid radius — enter a value in feet",
            en.get(R.string.settings_camera_alert_radius_invalid, en.get(radiusUnitWordRes(us))),
        )
    }

    @Test
    fun `out of range message shows the temperature range in fahrenheit for US`() {
        assertEquals(
            "Thresholds out of range (temp 140–302, voltage 8–15, RPM 3000–20000)",
            en.get(
                R.string.settings_alert_thresholds_out_of_range,
                temperatureFieldValue(60, us),
                temperatureFieldValue(150, us),
            ),
        )
    }

    @Test
    fun `spanish metric messages keep the original wording`() {
        assertEquals(
            "Radio de aviso: 250 m",
            es.get(R.string.settings_camera_alert_radius_saved, radiusFieldValue(250, metric), radiusFieldUnit(metric).label),
        )
        assertEquals(
            "Radio inválido — usa un número en metros",
            es.get(R.string.settings_camera_alert_radius_invalid, es.get(radiusUnitWordRes(metric))),
        )
        assertEquals(
            "Umbrales fuera de rango (temp 60-150, volt 8-15, RPM 3000-20000)",
            es.get(R.string.settings_alert_thresholds_out_of_range, 60, 150),
        )
    }
}
