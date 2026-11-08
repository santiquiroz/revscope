package com.revscope.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.revscope.core.common.i18n.LITERS_PER_US_GALLON
import com.revscope.core.common.i18n.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreciosCombustibleTest {

    @get:Rule
    val compose = createComposeRule()

    private val cop = Currency.getInstance("COP")
    private val gbp = Currency.getInstance("GBP")
    private val usd = Currency.getInstance("USD")
    private val noActions = FuelPriceActions({}, {}, {}, {})

    @Test
    fun `CO muestra el precio guardado por galon sin decimales`() {
        assertEquals("16000", fuelPriceFieldText(16_000.0, Region.CO, cop))
    }

    @Test
    fun `GB muestra el precio por litro con dos decimales`() {
        assertEquals("1.46", fuelPriceFieldText(1.459 * LITERS_PER_US_GALLON, Region.GB, gbp))
    }

    @Test
    fun `sin precio el campo queda vacio`() {
        assertEquals("", fuelPriceFieldText(null, Region.US, usd))
    }

    @Test
    fun `GB guarda por galon lo que se ingresa por litro y vacio es sin precio`() {
        val stored = fuelPricesToStore(listOf("1,50", "", "1.6"), Region.GB)!!
        assertEquals(1.5 * LITERS_PER_US_GALLON, stored[0]!!, 1e-9)
        assertNull(stored[1])
        assertEquals(1.6 * LITERS_PER_US_GALLON, stored[2]!!, 1e-9)
    }

    @Test
    fun `CO mantiene el rango obligatorio de 1000 a 50000`() {
        assertNull(fuelPricesToStore(listOf("16000", "", "10500"), Region.CO))
        assertNull(fuelPricesToStore(listOf("500", "20000", "10500"), Region.CO))
        assertEquals(listOf(16_000.0, 20_000.0, 10_500.0), fuelPricesToStore(listOf("16000", "20000", "10500"), Region.CO))
        assertEquals(R.string.settings_fuel_price_out_of_range, invalidFuelPriceMessage(Region.CO))
    }

    @Test
    fun `US rechaza precios no positivos`() {
        assertNull(fuelPricesToStore(listOf("0", "", ""), Region.US))
        assertNull(fuelPricesToStore(listOf("abc", "", ""), Region.US))
        assertEquals(R.string.settings_fuel_price_invalid, invalidFuelPriceMessage(Region.US))
    }

    @Test
    fun `GB pide precio por litro en libras con la pista corta`() {
        compose.setContent {
            CombustibleContenido(FuelPriceFormat(Region.GB, gbp), FuelPriceFields("", "", ""), noActions)
        }
        compose.onNodeWithText("Regular price per liter (GBP)").assertExists()
        compose.onNodeWithText("Diesel price per liter (GBP)").assertExists()
        compose.onNodeWithText("Enter what you pay at your station.").assertExists()
    }

    @Test
    fun `US pide precio por galon en dolares`() {
        compose.setContent {
            CombustibleContenido(FuelPriceFormat(Region.US, usd), FuelPriceFields("", "", ""), noActions)
        }
        compose.onNodeWithText("Regular price per gallon (USD)").assertExists()
    }

    @Test
    fun `al escribir se notifica el cambio`() {
        var corriente = ""
        val actions = FuelPriceActions({ corriente = it }, {}, {}, {})
        compose.setContent {
            CombustibleContenido(FuelPriceFormat(Region.US, usd), FuelPriceFields("", "", ""), actions)
        }
        compose.onNodeWithText("Regular price per gallon (USD)").performTextReplacement("3.5")
        assertEquals("3.5", corriente)
    }

    @Test
    @Config(qualifiers = "es")
    fun `CO en espanol conserva los textos de hoy`() {
        compose.setContent {
            CombustibleContenido(FuelPriceFormat(Region.CO, cop), FuelPriceFields("16000", "20000", "10500"), noActions)
        }
        compose.onNodeWithText("Precio galón corriente (COP)").assertExists()
        compose.onNodeWithText("Precio galón extra (COP)").assertExists()
        compose.onNodeWithText("Precio galón diésel / ACPM (COP)").assertExists()
        compose.onNodeWithText("datos.gov.co", substring = true).assertExists()
    }
}
