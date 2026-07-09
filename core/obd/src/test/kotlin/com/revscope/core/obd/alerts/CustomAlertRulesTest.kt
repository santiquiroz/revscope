package com.revscope.core.obd.alerts

import com.revscope.core.obd.model.ObdReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAlertRulesTest {

    @Test
    fun `lectura por debajo del minimo genera mensaje con nombre y valor`() {
        val rules = CustomAlertRules.parse(
            """[{"pid":"0A","min":200,"max":400,"nombre":"Presión de combustible"}]"""
        )
        val reading = ObdReading(pid = "0A", value = 150.0, unit = "kPa")
        assertEquals(
            "Presión de combustible fuera de rango: 150 kPa",
            CustomAlertRules.evaluate(reading, rules),
        )
    }

    @Test
    fun `lectura por encima del maximo genera mensaje con nombre y valor`() {
        val rules = CustomAlertRules.parse(
            """[{"pid":"0A","min":200,"max":400,"nombre":"Presión de combustible"}]"""
        )
        val reading = ObdReading(pid = "0A", value = 450.0, unit = "kPa")
        assertEquals(
            "Presión de combustible fuera de rango: 450 kPa",
            CustomAlertRules.evaluate(reading, rules),
        )
    }

    @Test
    fun `lectura dentro de rango no genera alerta`() {
        val rules = CustomAlertRules.parse(
            """[{"pid":"0A","min":200,"max":400,"nombre":"Presión de combustible"}]"""
        )
        val reading = ObdReading(pid = "0A", value = 300.0, unit = "kPa")
        assertNull(CustomAlertRules.evaluate(reading, rules))
    }

    @Test
    fun `regla sin minimo solo evalua el maximo`() {
        val rules = CustomAlertRules.parse("""[{"pid":"0B","max":100,"nombre":"Carga del motor"}]""")
        val dentro = ObdReading(pid = "0B", value = 10.0, unit = "%")
        val fuera = ObdReading(pid = "0B", value = 120.0, unit = "%")
        assertNull(CustomAlertRules.evaluate(dentro, rules))
        assertEquals("Carga del motor fuera de rango: 120 %", CustomAlertRules.evaluate(fuera, rules))
    }

    @Test
    fun `regla sin maximo solo evalua el minimo`() {
        val rules = CustomAlertRules.parse("""[{"pid":"0C","min":800,"nombre":"RPM ralenti"}]""")
        val dentro = ObdReading(pid = "0C", value = 900.0, unit = "rpm")
        val fuera = ObdReading(pid = "0C", value = 500.0, unit = "rpm")
        assertNull(CustomAlertRules.evaluate(dentro, rules))
        assertEquals("RPM ralenti fuera de rango: 500 rpm", CustomAlertRules.evaluate(fuera, rules))
    }

    @Test
    fun `mensaje conserva un decimal cuando el valor no es entero`() {
        val rules = CustomAlertRules.parse("""[{"pid":"0A","max":230,"nombre":"Presión"}]""")
        val reading = ObdReading(pid = "0A", value = 233.4, unit = "kPa")
        assertEquals("Presión fuera de rango: 233.4 kPa", CustomAlertRules.evaluate(reading, rules))
    }

    @Test
    fun `json invalido retorna lista vacia`() {
        assertTrue(CustomAlertRules.parse("esto no es json").isEmpty())
    }

    @Test
    fun `json vacio retorna lista vacia`() {
        assertTrue(CustomAlertRules.parse("").isEmpty())
        assertTrue(CustomAlertRules.parse("   ").isEmpty())
    }

    @Test
    fun `pid sin regla asociada no genera alerta`() {
        val rules = CustomAlertRules.parse(
            """[{"pid":"0A","min":200,"nombre":"Presión de combustible"}]"""
        )
        val reading = ObdReading(pid = "FF", value = 10.0, unit = "")
        assertNull(CustomAlertRules.evaluate(reading, rules))
    }

    @Test
    fun `pid de la regla es insensible a mayusculas`() {
        val rules = CustomAlertRules.parse("""[{"pid":"0a","max":100,"nombre":"Test"}]""")
        val reading = ObdReading(pid = "0A", value = 150.0, unit = "u")
        assertEquals("Test fuera de rango: 150 u", CustomAlertRules.evaluate(reading, rules))
    }
}
