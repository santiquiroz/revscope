package com.revscope.core.obd.diagnostics

import com.revscope.core.obd.model.ObdReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReglasBorradoDtcTest {

    private val ahora = 100_000L

    private fun velocidad(kmh: Double, edadMs: Long) = ObdReading("0D", kmh, "km/h", timestamp = ahora - edadMs)

    @Test
    fun `sin confirmacion se rechaza aunque este detenido`() {
        val rechazo = ReglasBorradoDtc.evaluar(confirmado = false, velocidad(0.0, 100), ahora)

        assertEquals(RechazoBorradoDtc.SinConfirmar, rechazo)
    }

    @Test
    fun `una velocidad de hace mas de dos segundos no sirve para saber si esta detenido`() {
        assertEquals(
            RechazoBorradoDtc.SinVelocidadReciente,
            ReglasBorradoDtc.evaluar(confirmado = true, velocidad(0.0, 2_001), ahora),
        )
        assertEquals(
            RechazoBorradoDtc.SinVelocidadReciente,
            ReglasBorradoDtc.evaluar(confirmado = true, velocidad = null, ahoraMs = ahora),
        )
    }

    @Test
    fun `en movimiento se rechaza con la velocidad`() {
        val rechazo = ReglasBorradoDtc.evaluar(confirmado = true, velocidad(35.4, 200), ahora)

        assertEquals(RechazoBorradoDtc.EnMovimiento(35), rechazo)
    }

    @Test
    fun `detenido y confirmado se permite`() {
        assertNull(ReglasBorradoDtc.evaluar(confirmado = true, velocidad(0.0, 2_000), ahora))
    }
}
