package com.revscope.feature.workshop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.designsystem.LocalUnitSystem
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.obd.legal.DocumentStatusCalculator.Nivel
import com.revscope.core.obd.trip.MaintenanceCalculator
import com.revscope.core.obd.workshop.OdometerVerifier
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NBSP = " "

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class UnidadesTallerTest {

    @get:Rule
    val compose = createComposeRule()

    private fun enUnidades(system: UnitSystem, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalUnitSystem provides system) {
                RevScopeTheme { content() }
            }
        }
    }

    private fun estado(kmRestantes: Double) = MaintenanceCalculator.ItemStatus(
        item = MaintenanceItemEntity(vehicleProfileId = 1, nombre = "Oil", intervaloKm = 5000.0, ultimoServicioKm = 1000.0),
        kmRestantes = kmRestantes,
        nivel = Nivel.OK,
    )

    @Test
    fun `speed comparison shows the difference in mph`() {
        enUnidades(UnitSystem.US) {
            SpeedComparisonContent(true, 104.0, 98.4, 5.6, onReiniciar = {}, onVolver = {})
        }

        compose.onNodeWithText("+3.5 mph").assertIsDisplayed()
        compose.onNodeWithText("+5.7%").assertIsDisplayed()
    }

    @Test
    fun `speed comparison asks to speed up past the threshold in mph`() {
        enUnidades(UnitSystem.UK) {
            SpeedComparisonContent(true, 6.0, 5.0, null, onReiniciar = {}, onVolver = {})
        }

        compose.onNodeWithText("-- (speed up past 6${NBSP}mph)").assertIsDisplayed()
    }

    @Test
    fun `maintenance card shows interval and remaining distance in miles`() {
        enUnidades(UnitSystem.US) {
            MaintenanceItemCard(estado(500.0), odometroActual = 5500.0, onRegistrarServicio = {}, onEditarIntervalo = {})
        }

        compose.onNodeWithText("Due in 311${NBSP}mi").assertIsDisplayed()
        compose.onNodeWithText("Every 3107${NBSP}mi").assertIsDisplayed()
    }

    @Test
    fun `maintenance card shows overdue distance in miles`() {
        enUnidades(UnitSystem.UK) {
            MaintenanceItemCard(estado(-160.9344), odometroActual = 6160.0, onRegistrarServicio = {}, onEditarIntervalo = {})
        }

        compose.onNodeWithText("Overdue by 100${NBSP}mi").assertIsDisplayed()
    }

    @Test
    fun `odometer reading shows miles`() {
        val lectura = OdometerVerifier.Reading(epochMs = 0L, km = 16093.44)
        enUnidades(UnitSystem.US) {
            Column {
                UltimaLecturaCard(lectura, diagnosis = null, locale = Locale.US)
                HistorialRow(lectura, Locale.US)
            }
        }

        compose.onAllNodesWithText("10000${NBSP}mi").assertCountEquals(2)
    }

    @Test
    fun `mileage input is shown in miles and stored in km`() {
        val us = UnitFormatter(UnitSystem.US, Locale.US)

        assertEquals("10000", distanciaEditable(us, 16093.44))
        assertEquals(160.9344, distanciaMetrica(us, "100")!!, 1e-9)
        assertEquals(null, distanciaMetrica(us, "abc"))
    }

    @Test
    fun `mileage input stays in km for metric`() {
        val metric = UnitFormatter(UnitSystem.METRIC, Locale.US)

        assertEquals("5000", distanciaEditable(metric, 5000.4))
        assertEquals(100.0, distanciaMetrica(metric, "100")!!, 1e-9)
    }
}
