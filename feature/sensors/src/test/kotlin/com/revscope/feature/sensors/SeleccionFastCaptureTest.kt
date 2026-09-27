package com.revscope.feature.sensors

import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.taller.pid.DisponibilidadPid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeleccionFastCaptureTest {

    @Test
    fun `retira los PIDs seleccionados cuando la ECU confirma que no los soporta`() {
        val estados = mapOf(
            "11" to disponibilidad("11", EstadoSoporte.NoSoportado),
            "0C" to disponibilidad("0C", EstadoSoporte.Soportado),
        )

        val resultado = reconciliarPids(listOf("11", "0C")) { estados.getValue(it) }

        assertEquals(listOf("0C"), resultado.aceptados)
        assertEquals(listOf("11"), resultado.rechazados.map { it.pid })
    }

    @Test
    fun `conserva la seleccion mientras el mapa de PIDs es desconocido`() {
        val resultado = reconciliarPids(
            seleccion = listOf("11"),
            disponibilidad = { disponibilidad(it, EstadoSoporte.Desconocido) },
        )

        assertEquals(listOf("11"), resultado.aceptados)
        assertEquals(emptyList<DisponibilidadPid>(), resultado.rechazados)
    }

    @Test
    fun `permite desmarcar un PID aunque haya pasado a no soportado`() {
        val noSoportado = disponibilidad("11", EstadoSoporte.NoSoportado)

        val resultado = seleccionTrasAlternar(listOf("11", "0C"), "11", noSoportado)

        assertEquals(listOf("0C"), resultado)
    }

    @Test
    fun `impide seleccionar un PID no soportado`() {
        val noSoportado = disponibilidad("11", EstadoSoporte.NoSoportado)

        assertNull(seleccionTrasAlternar(emptyList(), "11", noSoportado))
    }

    @Test
    fun `limpia el aviso cuando el soporte del PID vuelve a ser desconocido`() {
        val aviso = disponibilidad("11", EstadoSoporte.NoSoportado)

        val resultado = resolverAvisoPidNoDisponible(aviso) {
            disponibilidad(it, EstadoSoporte.Desconocido)
        }

        assertNull(resultado)
    }

    @Test
    fun `limpia el aviso cuando el PID pasa a soportado`() {
        val aviso = disponibilidad("11", EstadoSoporte.NoSoportado)

        val resultado = resolverAvisoPidNoDisponible(aviso) {
            disponibilidad(it, EstadoSoporte.Soportado)
        }

        assertNull(resultado)
    }

    @Test
    fun `conserva el aviso mientras el PID siga no soportado`() {
        val aviso = disponibilidad("11", EstadoSoporte.NoSoportado)

        val resultado = resolverAvisoPidNoDisponible(aviso) {
            disponibilidad(it, EstadoSoporte.NoSoportado)
        }

        assertEquals(aviso, resultado)
    }

    @Test
    fun `una seleccion valida limpia el aviso anterior`() {
        val aviso = disponibilidad("11", EstadoSoporte.NoSoportado)

        val resultado = resolverAvisoPidNoDisponible(
            actual = aviso,
            seleccionValida = true,
            disponibilidad = { disponibilidad(it, EstadoSoporte.NoSoportado) },
        )

        assertNull(resultado)
    }

    private fun disponibilidad(pid: String, estado: EstadoSoporte) =
        DisponibilidadPid.resolver(pid, "parámetro", estado)
}
