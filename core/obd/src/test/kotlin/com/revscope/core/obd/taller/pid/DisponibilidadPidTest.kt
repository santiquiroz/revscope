package com.revscope.core.obd.taller.pid

import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.pid.PidRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisponibilidadPidTest {

    private val registry = PidRegistry(
        """
        [{
          "mode": "01", "pid": "05",
          "name": "Coolant Temperature", "nameEs": "Temperatura del motor",
          "bytes": 1, "formula": "A-40", "unit": "°C",
          "min": -40, "max": 215, "priority": 2
        }]
        """.trimIndent(),
    )

    @Test
    fun `before bitmap availability is unknown and polling remains allowed`() {
        val result = DisponibilidadPid.resolver(
            pid = "05",
            nombre = "la temperatura del motor",
            registry = registry,
        )

        assertEquals(EstadoSoporte.Desconocido, result.estado)
        assertEquals("Aún no se conocen los PIDs que anuncia la ECU.", result.motivo)
        assertTrue(result.puedeConsultarse)
    }

    @Test
    fun `after bitmap an omitted PID is unsupported with a useful reason`() {
        registry.setSupportedPids(setOf("0C"))

        val result = DisponibilidadPid.resolver(
            pid = "05",
            nombre = "la temperatura del motor",
            registry = registry,
        )

        assertEquals(EstadoSoporte.NoSoportado, result.estado)
        assertEquals("Esta ECU no reporta la temperatura del motor (PID 05).", result.motivo)
    }

    @Test
    fun `supported PID has no unavailability reason`() {
        registry.setSupportedPids(setOf("05"))

        val result = DisponibilidadPid.resolver(
            pid = "05",
            nombre = "la temperatura del motor",
            registry = registry,
        )

        assertEquals(EstadoSoporte.Soportado, result.estado)
        assertNull(result.motivo)
        assertTrue(result.puedeConsultarse)
    }
}
