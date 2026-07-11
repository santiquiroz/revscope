package com.revscope.core.obd.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerAutoSyncTest {

    @Test
    fun `sincroniza cuando el ecu difiere del odometro app mas del umbral`() {
        val debe = OdometerAutoSync.debeSincronizar(
            ecuKm = 12_345.0,
            odometroAppKm = 2.0,
            sumaSesionesKm = 2.0,
            esFalla = false,
        )

        assertTrue(debe)
    }

    @Test
    fun `no sincroniza cuando el diagnostico es falla`() {
        val debe = OdometerAutoSync.debeSincronizar(
            ecuKm = 12_345.0,
            odometroAppKm = 2.0,
            sumaSesionesKm = 2.0,
            esFalla = true,
        )

        assertFalse(debe)
    }

    @Test
    fun `no sincroniza con lectura ecu en cero`() {
        val debe = OdometerAutoSync.debeSincronizar(
            ecuKm = 0.0,
            odometroAppKm = 100.0,
            sumaSesionesKm = 50.0,
            esFalla = false,
        )

        assertFalse(debe)
    }

    @Test
    fun `no sincroniza cuando el ecu reporta menos km que las sesiones registradas`() {
        // Base quedaría negativa: lectura sospechosa, no corromper el perfil.
        val debe = OdometerAutoSync.debeSincronizar(
            ecuKm = 80.0,
            odometroAppKm = 111.0,
            sumaSesionesKm = 111.0,
            esFalla = false,
        )

        assertFalse(debe)
    }

    @Test
    fun `no sincroniza cuando la diferencia es menor al umbral`() {
        val debe = OdometerAutoSync.debeSincronizar(
            ecuKm = 12_345.5,
            odometroAppKm = 12_345.0,
            sumaSesionesKm = 100.0,
            esFalla = false,
        )

        assertFalse(debe)
    }

    @Test
    fun `sincroniza en el borde exacto del umbral`() {
        val debe = OdometerAutoSync.debeSincronizar(
            ecuKm = 12_346.0,
            odometroAppKm = 12_345.0,
            sumaSesionesKm = 100.0,
            esFalla = false,
        )

        assertTrue(debe)
    }

    @Test
    fun `sincroniza hacia abajo cuando la app sobreconto y el ecu sigue por encima de las sesiones`() {
        val debe = OdometerAutoSync.debeSincronizar(
            ecuKm = 10_000.0,
            odometroAppKm = 10_500.0,
            sumaSesionesKm = 400.0,
            esFalla = false,
        )

        assertTrue(debe)
    }

    @Test
    fun `nueva base es la lectura ecu menos la suma de sesiones`() {
        assertEquals(12_234.0, OdometerAutoSync.nuevaBase(ecuKm = 12_345.0, sumaSesionesKm = 111.0), 0.0001)
    }

    @Test
    fun `nueva base con sesiones en cero es la lectura ecu completa`() {
        assertEquals(12_345.0, OdometerAutoSync.nuevaBase(ecuKm = 12_345.0, sumaSesionesKm = 0.0), 0.0001)
    }
}
