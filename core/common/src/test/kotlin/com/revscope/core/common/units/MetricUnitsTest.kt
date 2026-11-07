package com.revscope.core.common.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MetricUnitsTest {

    private val us = UnitFormatter(UnitSystem.US, Locale.US)
    private val uk = UnitFormatter(UnitSystem.UK, Locale.US)
    private val metric = UnitFormatter(UnitSystem.METRIC, Locale.US)

    @Test
    fun `en US convierte velocidad, temperatura, presion y distancia`() {
        assertEquals(62.137, us.fromMetric(100.0, "km/h"), 0.001)
        assertEquals(194.0, us.fromMetric(90.0, "°C"), 0.001)
        assertEquals(14.504, us.fromMetric(100.0, "kPa"), 0.001)
        assertEquals(767.08, us.fromMetric(1234.5, "km"), 0.01)
    }

    @Test
    fun `en US las etiquetas pasan al sistema imperial`() {
        assertEquals("mph", us.labelForMetric("km/h"))
        assertEquals("°F", us.labelForMetric("°C"))
        assertEquals("psi", us.labelForMetric("kPa"))
        assertEquals("mi", us.labelForMetric("km"))
    }

    @Test
    fun `en UK la temperatura sigue en grados C`() {
        assertEquals(90.0, uk.fromMetric(90.0, "°C"), 0.0)
        assertEquals("°C", uk.labelForMetric("°C"))
        assertEquals("mph", uk.labelForMetric("km/h"))
    }

    @Test
    fun `las demas unidades y el sistema metrico quedan igual`() {
        assertEquals(3000.0, us.fromMetric(3000.0, "rpm"), 0.0)
        assertEquals("rpm", us.labelForMetric("rpm"))
        assertEquals(100.0, metric.fromMetric(100.0, "km/h"), 0.0)
        assertEquals("km/h", metric.labelForMetric("km/h"))
    }

    @Test
    fun `formatMetric formatea con el formatter y deja fuera las unidades sin conversion`() {
        assertEquals("767.1 mi", us.formatMetric(1234.5, "km"))
        assertEquals("62 mph", us.formatMetric(100.0, "km/h"))
        assertNull(us.formatMetric(3000.0, "rpm"))
    }
}
