package com.revscope.feature.sensors

import com.revscope.core.obd.pid.PidDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorSelectionTest {

    @Test
    fun `conserva el PID si sigue disponible tras recibir capacidades`() {
        val disponibles = listOf(pid("05"), pid("0C"))

        assertEquals("0C", pidSeleccionadoValido("0C", disponibles))
    }

    @Test
    fun `elige el primer PID disponible si la ECU no anuncia el seleccionado`() {
        val disponibles = listOf(pid("05"), pid("0D"))

        assertEquals("05", pidSeleccionadoValido("0C", disponibles))
    }

    @Test
    fun `sin PIDs disponibles no inventa una seleccion`() {
        assertNull(pidSeleccionadoValido("0C", emptyList()))
    }

    private fun pid(codigo: String) = PidDefinition(
        mode = "01",
        pid = codigo,
        name = codigo,
        nameEs = codigo,
        bytes = 1,
        formula = "A",
        unit = "",
        min = 0.0,
        max = 255.0,
        priority = 1,
    )
}
