package com.revscope.feature.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.common.i18n.Region
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class CostoViajeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `en US sin precio no hay fila de costo`() {
        compose.setContent { FuelCostCard(cost = null, region = Region.US, Locale.US, Locale.US) }

        compose.onNodeWithText("Fuel cost").assertDoesNotExist()
    }

    @Test
    fun `en CO el costo sale en COP sin decimales`() {
        val colombia = Locale("es", "CO")
        val esperado = NumberFormat.getCurrencyInstance(colombia).apply {
            currency = Currency.getInstance("COP")
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }.format(8_453.0)

        compose.setContent { FuelCostCard(cost = 8_453.4, region = Region.CO, colombia, Locale("es")) }

        compose.onNodeWithText("Fuel cost").assertExists()
        compose.onNodeWithText(esperado).assertExists()
    }

    @Test
    fun `en US con costo sale en dolares`() {
        compose.setContent { FuelCostCard(cost = 4.2, region = Region.US, Locale.US, Locale.US) }

        compose.onNodeWithText("$4.20").assertExists()
    }
}
