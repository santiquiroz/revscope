package com.revscope.feature.auto

import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnidadesCarTest {

    private fun formatter(system: UnitSystem) = UnitFormatter(system, Locale.US)

    @Test
    fun `la velocidad del panel de Android Auto sale en el sistema del usuario`() {
        assertEquals("100 km/h", textoVelocidad(100.0, formatter(UnitSystem.METRIC)))
        assertEquals("62 mph", textoVelocidad(100.0, formatter(UnitSystem.US)))
        assertEquals("62 mph", textoVelocidad(100.0, formatter(UnitSystem.UK)))
    }

    @Test
    fun `la temperatura del panel de Android Auto sale en el sistema del usuario`() {
        assertEquals("90 °C", textoTemperatura(90.0, formatter(UnitSystem.METRIC)))
        assertEquals("194 °F", textoTemperatura(90.0, formatter(UnitSystem.US)))
        assertEquals("90 °C", textoTemperatura(90.0, formatter(UnitSystem.UK)))
    }
}
