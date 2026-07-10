package com.revscope.core.obd.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerVerifierTest {

    @Test
    fun `primera lectura es linea base ok`() {
        val nueva = OdometerVerifier.Reading(epochMs = 1_000L, km = 12_345.0)

        val d = OdometerVerifier.evaluar(historial = emptyList(), nueva = nueva, distanciaAppKm = 0.0)

        assertEquals(DiagnosticRules.Nivel.OK, d.nivel)
        assertTrue(d.titulo.contains("Línea base"))
    }

    @Test
    fun `odometro retrocedido es falla`() {
        val anterior = OdometerVerifier.Reading(epochMs = 1_000L, km = 50_000.0)
        val nueva = OdometerVerifier.Reading(epochMs = 2_000L, km = 49_800.0)

        val d = OdometerVerifier.evaluar(historial = listOf(anterior), nueva = nueva, distanciaAppKm = 0.0)

        assertEquals(DiagnosticRules.Nivel.FALLA, d.nivel)
        assertTrue(d.causaProbable.contains("manipulación", ignoreCase = true))
    }

    @Test
    fun `odometro que avanza menos del 80 porciento de la distancia app es atencion`() {
        val anterior = OdometerVerifier.Reading(epochMs = 1_000L, km = 10_000.0)
        // ECU avanzó 70 km, la app registró 100 km recorridos → 70% < 80%
        val nueva = OdometerVerifier.Reading(epochMs = 2_000L, km = 10_070.0)

        val d = OdometerVerifier.evaluar(historial = listOf(anterior), nueva = nueva, distanciaAppKm = 100.0)

        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
        assertTrue(d.causaProbable.contains("avanza menos", ignoreCase = true))
    }

    @Test
    fun `odometro exactamente en el 80 porciento es ok`() {
        val anterior = OdometerVerifier.Reading(epochMs = 1_000L, km = 10_000.0)
        val nueva = OdometerVerifier.Reading(epochMs = 2_000L, km = 10_080.0)

        val d = OdometerVerifier.evaluar(historial = listOf(anterior), nueva = nueva, distanciaAppKm = 100.0)

        assertEquals(DiagnosticRules.Nivel.OK, d.nivel)
    }

    @Test
    fun `odometro consistente con la distancia app es ok`() {
        val anterior = OdometerVerifier.Reading(epochMs = 1_000L, km = 10_000.0)
        val nueva = OdometerVerifier.Reading(epochMs = 2_000L, km = 10_150.0)

        val d = OdometerVerifier.evaluar(historial = listOf(anterior), nueva = nueva, distanciaAppKm = 100.0)

        assertEquals(DiagnosticRules.Nivel.OK, d.nivel)
    }

    @Test
    fun `sin distancia app registrada no genera falso positivo`() {
        val anterior = OdometerVerifier.Reading(epochMs = 1_000L, km = 10_000.0)
        val nueva = OdometerVerifier.Reading(epochMs = 2_000L, km = 10_050.0)

        val d = OdometerVerifier.evaluar(historial = listOf(anterior), nueva = nueva, distanciaAppKm = 0.0)

        assertEquals(DiagnosticRules.Nivel.OK, d.nivel)
    }

    @Test
    fun `odometro sin avance mientras la app registro distancia es atencion`() {
        val anterior = OdometerVerifier.Reading(epochMs = 1_000L, km = 10_000.0)
        val nueva = OdometerVerifier.Reading(epochMs = 2_000L, km = 10_000.0)

        val d = OdometerVerifier.evaluar(historial = listOf(anterior), nueva = nueva, distanciaAppKm = 50.0)

        assertEquals(DiagnosticRules.Nivel.ATENCION, d.nivel)
    }

    @Test
    fun `compara siempre contra la ultima lectura del historial`() {
        val historial = listOf(
            OdometerVerifier.Reading(epochMs = 1_000L, km = 9_000.0),
            OdometerVerifier.Reading(epochMs = 2_000L, km = 10_000.0),
        )
        val nueva = OdometerVerifier.Reading(epochMs = 3_000L, km = 9_500.0)

        val d = OdometerVerifier.evaluar(historial = historial, nueva = nueva, distanciaAppKm = 0.0)

        assertEquals(DiagnosticRules.Nivel.FALLA, d.nivel)
    }

    @Test
    fun `agregar al historial respeta el tope de 50 entradas`() {
        val historial = (1..50).map { OdometerVerifier.Reading(epochMs = it.toLong(), km = it.toDouble()) }
        val nueva = OdometerVerifier.Reading(epochMs = 51L, km = 51.0)

        val actualizado = OdometerVerifier.agregarAlHistorial(historial, nueva)

        assertEquals(50, actualizado.size)
        assertEquals(2L, actualizado.first().epochMs)
        assertEquals(51L, actualizado.last().epochMs)
    }

    @Test
    fun `agregar al historial vacio crea una entrada`() {
        val nueva = OdometerVerifier.Reading(epochMs = 1L, km = 100.0)

        val actualizado = OdometerVerifier.agregarAlHistorial(emptyList(), nueva)

        assertEquals(listOf(nueva), actualizado)
    }
}
