package com.revscope.feature.dtc

import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnidadesDtcTest {

    private val resolverEs = com.revscope.core.uitesting.XmlStringResolver.es(
        com.revscope.feature.dtc.R::class.java,
        resDirs = listOf("src/main/res"),
    )
    private val resolverEn = com.revscope.core.uitesting.XmlStringResolver.en(
        com.revscope.feature.dtc.R::class.java,
        resDirs = listOf("src/main/res"),
    )

    private fun formatter(system: UnitSystem, locale: Locale = Locale.US) = UnitFormatter(system, locale)

    @Test
    fun `en US el aviso de movimiento dice la velocidad en mph`() {
        assertEquals(
            "The vehicle is moving at 22 mph. Stop before clearing the codes.",
            textoRechazoUi(RechazoBorradoDtc.EnMovimiento(35), resolverEn, formatter(UnitSystem.US)),
        )
    }

    @Test
    fun `en metrico el aviso de movimiento queda igual que antes`() {
        assertEquals(
            "El vehículo va a 35 km/h. Detente antes de borrar los códigos.",
            textoRechazoUi(RechazoBorradoDtc.EnMovimiento(35), resolverEs, formatter(UnitSystem.METRIC, resolverEs.locale)),
        )
    }

    @Test
    fun `en US el freeze frame muestra mph, grados F y psi`() {
        val us = formatter(UnitSystem.US)

        assertEquals("24.9 mph", valorFreezeFrame(40.0, "km/h", us, Locale.US))
        assertEquals("89.6 °F", valorFreezeFrame(32.0, "°C", us, Locale.US))
        assertEquals("14.6 psi", valorFreezeFrame(101.0, "kPa", us, Locale.US))
        assertEquals("1454 rpm", valorFreezeFrame(1454.0, "rpm", us, Locale.US))
    }

    @Test
    fun `en UK el freeze frame deja la temperatura en grados C`() {
        val uk = formatter(UnitSystem.UK)

        assertEquals("32 °C", valorFreezeFrame(32.0, "°C", uk, Locale.US))
        assertEquals("24.9 mph", valorFreezeFrame(40.0, "km/h", uk, Locale.US))
    }

    @Test
    fun `en metrico el freeze frame conserva el formato`() {
        val es = Locale.forLanguageTag("es-CO")
        val metrico = formatter(UnitSystem.METRIC, es)

        assertEquals("32 °C", valorFreezeFrame(32.0, "°C", metrico, es))
        assertEquals("2,4 %", valorFreezeFrame(2.4, "%", metrico, es))
    }
}
