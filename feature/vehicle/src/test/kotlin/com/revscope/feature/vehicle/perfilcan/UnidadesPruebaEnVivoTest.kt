package com.revscope.feature.vehicle.perfilcan

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.designsystem.LocalUnitSystem
import com.revscope.core.designsystem.RevScopeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnidadesPruebaEnVivoTest {

    @get:Rule
    val compose = createComposeRule()

    private fun formatter(system: UnitSystem) = UnitFormatter(system, Locale.US)

    private fun prueba(valor: String) = PruebaEnVivoUi(
        clave = "velocidad",
        nombre = "Velocidad",
        instruccion = "Mueve el vehículo",
        detalle = "0x158 · bits 0-15",
        valor = valor,
        disponible = true,
        motivoSinPrueba = null,
    )

    @Test
    fun `en US la prueba en vivo muestra la velocidad en mph`() {
        compose.setContent {
            CompositionLocalProvider(LocalUnitSystem provides UnitSystem.US) {
                RevScopeTheme { PruebaEnVivoContenido(prueba("80 km/h"), onResponder = {}) }
            }
        }

        compose.onNodeWithText("50 mph").assertExists()
    }

    @Test
    fun `en US la temperatura y la presion del bus se convierten`() {
        val us = formatter(UnitSystem.US)

        assertEquals("194 °F", valorEnUnidades("90 °C", us))
        assertEquals("14.5 psi", valorEnUnidades("100 kPa", us))
        assertEquals("1.8 psi", valorEnUnidades("12.50 kPa", us))
    }

    @Test
    fun `el numero del bus se lee con el separador de miles del idioma`() {
        val us = formatter(UnitSystem.US)
        val usEnEspanol = UnitFormatter(UnitSystem.US, Locale.forLanguageTag("es-CO"))

        assertEquals("767.1 mi", valorEnUnidades("1,234.5 km", us))
        assertEquals("767,1 mi", valorEnUnidades("1.234,5 km", usEnEspanol))
        assertEquals("1,8 psi", valorEnUnidades("12,50 kPa", usEnEspanol))
    }

    @Test
    fun `en UK la temperatura sigue en grados C y la velocidad pasa a mph`() {
        val uk = formatter(UnitSystem.UK)

        assertEquals("90 °C", valorEnUnidades("90 °C", uk))
        assertEquals("50 mph", valorEnUnidades("80 km/h", uk))
    }

    @Test
    fun `en metrico y para otras unidades el texto queda igual`() {
        assertEquals("80 km/h", valorEnUnidades("80 km/h", formatter(UnitSystem.METRIC)))
        assertEquals("3000 rpm", valorEnUnidades("3000 rpm", formatter(UnitSystem.US)))
        assertEquals("P", valorEnUnidades("P", formatter(UnitSystem.US)))
    }
}
