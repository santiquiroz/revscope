package com.revscope.feature.workshop

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticosChequeoTest {

    private val strings = XmlStringResolver.es(
        com.revscope.core.obd.R::class.java,
        resDirs = listOf("../../core/obd/src/main/res"),
    )

    @Test
    fun `readiness no soportado no produce un diagnostico contradictorio`() {
        val estado = ReadinessParser.ReadinessStatus(
            milOn = false,
            dtcCount = 0,
            isDiesel = false,
            monitors = emptyList(),
        )

        val diagnosticos = DiagnosticosChequeo.readiness(EstadoSoporte.NoSoportado, estado, strings)

        assertTrue(diagnosticos.isEmpty())
    }

    @Test
    fun `O2 no soportado no se evalua aunque quede una lectura anterior`() {
        val lecturas = mapOf("14" to lectura("14", 0.1, "V"))

        val diagnosticos = DiagnosticosChequeo.mezcla(
            lecturas = lecturas,
            muestrasO2 = List(40) { 0.1 },
            estadoSoporte = { pid -> if (pid == "14") EstadoSoporte.NoSoportado else EstadoSoporte.Soportado },
            resolver = strings,
        )

        assertFalse(diagnosticos.any { it.area == "Sensor O2" })
    }

    @Test
    fun `sin lectura de RPM no se diagnostica el voltaje como motor apagado`() {
        val diagnosticos = DiagnosticosChequeo.mezcla(
            lecturas = mapOf("VBAT" to lectura("VBAT", 11.2, "V")),
            muestrasO2 = emptyList(),
            estadoSoporte = { EstadoSoporte.Soportado },
            resolver = strings,
        )

        assertFalse(diagnosticos.any { it.area == "Eléctrico" })
    }

    @Test
    fun `RPM no soportada invalida su lectura anterior y aparece como no disponible`() {
        val resolver: (String) -> EstadoSoporte = { pid ->
            if (pid == "0C") EstadoSoporte.NoSoportado else EstadoSoporte.Soportado
        }
        val lecturas = mapOf(
            "0C" to lectura("0C", 0.0, "rpm"),
            "VBAT" to lectura("VBAT", 11.2, "V"),
        )

        val diagnosticos = DiagnosticosChequeo.mezcla(lecturas, emptyList(), resolver, strings)
        val noDisponibles = DiagnosticosChequeo.parametrosNoDisponibles(resolver)

        assertFalse(diagnosticos.any { it.area == "Eléctrico" })
        assertTrue(noDisponibles.contains("RPM del motor"))
    }

    private fun lectura(pid: String, valor: Double, unidad: String) = ObdReading(pid, valor, unidad)
}
