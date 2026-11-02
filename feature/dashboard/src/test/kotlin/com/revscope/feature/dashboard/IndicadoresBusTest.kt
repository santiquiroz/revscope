package com.revscope.feature.dashboard

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.perfilcan.ClavesSenal
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IndicadoresBusTest {

    private val ahora = 100_000L

    private fun lectura(clave: String, valor: Double, edadMs: Long = 0) = clave to ObdReading(clave, valor, "", ahora - edadMs)

    @Test
    fun `sin lecturas del bus no hay fila`() {
        val indicadores = indicadoresBus(mapOf(lectura(ClavesSenal.RPM, 1_500.0), lectura("0D", 30.0)), ahora)

        assertFalse(indicadores.visible)
        assertFalse(indicadores.alerta)
    }

    @Test
    fun `pata abajo con motor en marcha es alerta`() {
        val indicadores = indicadoresBus(
            mapOf(lectura(ClavesSenal.PATA_LATERAL, 1.0), lectura(ClavesSenal.MOTOR_EN_MARCHA, 1.0)),
            ahora,
        )

        assertTrue(indicadores.visible)
        assertEquals(true, indicadores.pataAbajo)
        assertTrue(indicadores.alerta)
    }

    @Test
    fun `pata abajo con motor apagado no es alerta`() {
        val indicadores = indicadoresBus(
            mapOf(lectura(ClavesSenal.PATA_LATERAL, 1.0), lectura(ClavesSenal.MOTOR_EN_MARCHA, 0.0)),
            ahora,
        )

        assertEquals(true, indicadores.pataAbajo)
        assertFalse(indicadores.alerta)
    }

    @Test
    fun `sin señal de motor en marcha usa las RPM`() {
        val indicadores = indicadoresBus(mapOf(lectura(ClavesSenal.PATA_LATERAL, 1.0), lectura(ClavesSenal.RPM, 1_400.0)), ahora)

        assertTrue(indicadores.alerta)
    }

    @Test
    fun `kill switch en cero es OFF y embrague en uno es apretado`() {
        val indicadores = indicadoresBus(
            mapOf(lectura(ClavesSenal.KILL_SWITCH, 0.0), lectura(ClavesSenal.EMBRAGUE, 1.0)),
            ahora,
        )

        assertEquals(true, indicadores.killOff)
        assertEquals(true, indicadores.embrague)
    }

    @Test
    fun `una bandera vieja no se muestra`() {
        val indicadores = indicadoresBus(mapOf(lectura(ClavesSenal.PATA_LATERAL, 1.0, edadMs = 10_000)), ahora)

        assertFalse(indicadores.visible)
    }

    @Test
    fun filaPataAbajo() = MatrizCaptura.componente("FilaIndicadoresBus_pata_abajo") {
        FilaIndicadoresBus(IndicadoresBus(pataAbajo = true, embrague = false, killOff = false, alerta = true))
    }
}
