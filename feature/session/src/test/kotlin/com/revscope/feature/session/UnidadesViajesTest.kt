package com.revscope.feature.session

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.designsystem.LocalUnitSystem
import com.revscope.core.designsystem.rememberUnitFormatter
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class UnidadesViajesTest {

    @get:Rule
    val compose = createComposeRule()

    private val viaje = SessionEntity(
        vehicleProfileId = 1,
        startedAt = 1_790_000_000_000,
        endedAt = 1_790_000_000_000 + 30 * 60_000,
        adapterName = "Android-Vlink",
        maxRpm = 9_000,
        maxSpeed = 112,
        distanceKm = 38.4f,
        best0to60Ms = 4_500,
    )

    private fun montar(system: UnitSystem, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalUnitSystem provides system, content = content)
        }
    }

    @Test
    fun `en US el historial muestra mph y millas`() {
        montar(UnitSystem.US) {
            SessionItem(session = viaje, isCompareCandidate = false, onClick = {}, onCompare = {}, onDelete = {})
        }

        compose.onNodeWithText("Max mph").assertExists()
        compose.onNodeWithText("70").assertExists()
        compose.onNodeWithText("mi").assertExists()
        compose.onNodeWithText("23.9").assertExists()
    }

    @Test
    fun `en US la descripcion del viaje a borrar va en millas`() {
        val en = XmlStringResolver.en(R::class.java)

        val description = descripcionViaje(viaje, en, UnitFormatter(UnitSystem.US, en.locale))

        assertTrue(description.endsWith("23.9 mi"))
    }

    @Test
    fun `en US el reporte muestra mph y grados Fahrenheit`() {
        montar(UnitSystem.US) { SpeedTempRow(112, 50f, 95, rememberUnitFormatter()) }

        compose.onNodeWithText("70${NBSP}mph").assertExists()
        compose.onNodeWithText("31${NBSP}mph").assertExists()
        compose.onNodeWithText("203${NBSP}°F").assertExists()
    }

    @Test
    fun `en UK el reporte muestra mph y grados Celsius`() {
        montar(UnitSystem.UK) { SpeedTempRow(112, 50f, 95, rememberUnitFormatter()) }

        compose.onNodeWithText("70${NBSP}mph").assertExists()
        compose.onNodeWithText("95${NBSP}°C").assertExists()
    }

    @Test
    fun `en US el resumen de ruta y los sprints van en millas y mph`() {
        montar(UnitSystem.US) {
            val units = rememberUnitFormatter()
            Text(routeSummary(12.3, 112.0, units))
            Text(sprintLabel(R.string.trips_0_60, SPRINT_SHORT_KMH, units))
            Text(sprintLabel(R.string.trips_0_100, SPRINT_LONG_KMH, units))
        }

        compose.onNodeWithText("GPS route — 7.6${NBSP}mi · max 70${NBSP}mph (GPS)").assertExists()
        compose.onNodeWithText("🏁 0–37${NBSP}mph").assertExists()
        compose.onNodeWithText("🏁 0–62${NBSP}mph").assertExists()
    }

    @Test
    fun `en US la comparacion muestra millas y mph`() {
        val runA = SessionCompareViewModel.RunData(viaje, 50, null, null, emptyList(), emptyList())
        val runB = SessionCompareViewModel.RunData(
            viaje.copy(maxSpeed = 80, distanceKm = 12.3f), 30, null, null, emptyList(), emptyList(),
        )
        montar(UnitSystem.US) { CompareContent(runA, runB, showLean = false) }

        compose.onNodeWithText("23.9${NBSP}mi").assertExists()
        compose.onNodeWithText("7.6${NBSP}mi").assertExists()
        compose.onNodeWithText("70${NBSP}mph").assertExists()
        compose.onNodeWithText("50${NBSP}mph").assertExists()
        compose.onNodeWithText("31${NBSP}mph").assertExists()
        compose.onNodeWithText("19${NBSP}mph").assertExists()
        compose.onNodeWithText("🏁 0–37${NBSP}mph").assertExists()
    }

    @Test
    fun `en US la tarjeta compartible va en millas y mph`() {
        val en = XmlStringResolver.en(R::class.java)
        val data = TripShareCard.CardData(
            startedAt = viaje.startedAt,
            durationMs = 30 * 60_000,
            distanceKm = 38.4f,
            maxSpeed = 112,
            avgSpeedKmh = 50f,
            maxRpm = 9_000,
            best0to100Ms = 8_000,
            maxLateralG = null,
            maxLeanDeg = null,
            maxBpm = null,
            lapCount = 0,
            bestLapMs = null,
            track = emptyList(),
        )

        val stats = TripShareCard.stats(data, en, UnitFormatter(UnitSystem.US, Locale.US)).toMap()

        assertEquals("23.9${NBSP}mi", stats["DISTANCE"])
        assertEquals("70${NBSP}mph", stats["MAX SPEED"])
        assertEquals("8.00s", stats["0–62${NBSP}mph"])
    }

    private companion object {
        const val NBSP = " "
    }
}
