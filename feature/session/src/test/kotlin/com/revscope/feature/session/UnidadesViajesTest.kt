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
import org.junit.Assert.assertFalse
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

    private val conSprints = viaje.copy(best0to100Ms = 8_000, best0to60mphMs = 4_800)

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
    fun `en US el resumen de ruta va en millas y mph`() {
        montar(UnitSystem.US) { Text(routeSummary(12.3, 112.0, rememberUnitFormatter())) }

        compose.onNodeWithText("GPS route — 7.6${NBSP}mi · max 70${NBSP}mph (GPS)").assertExists()
    }

    @Test
    fun `en US el sprint es 0-60 mph con su tiempo y sin fila 0-100`() {
        montar(UnitSystem.US) { SprintRow(conSprints, rememberUnitFormatter()) }

        compose.onNodeWithText("🏁 0–60 mph").assertExists()
        compose.onNodeWithText("4.80s").assertExists()
        compose.onNodeWithText("🏁 0–60 km/h").assertDoesNotExist()
        compose.onNodeWithText("🏁 0–100 km/h").assertDoesNotExist()
        compose.onNodeWithText("8.00s").assertDoesNotExist()
    }

    @Test
    fun `en UK sin tiempo en mph la fila muestra el vacio`() {
        montar(UnitSystem.UK) { SprintRow(conSprints.copy(best0to60mphMs = null), rememberUnitFormatter()) }

        compose.onNodeWithText("🏁 0–60 mph").assertExists()
        compose.onNodeWithText("—").assertExists()
    }

    @Test
    fun `en metrico se ven las dos filas en km por hora`() {
        montar(UnitSystem.METRIC) { SprintRow(conSprints, rememberUnitFormatter()) }

        compose.onNodeWithText("🏁 0–60 km/h").assertExists()
        compose.onNodeWithText("4.50s").assertExists()
        compose.onNodeWithText("🏁 0–100 km/h").assertExists()
        compose.onNodeWithText("8.00s").assertExists()
        compose.onNodeWithText("🏁 0–60 mph").assertDoesNotExist()
    }

    @Test
    fun `en US la comparacion muestra millas y mph`() {
        val runA = SessionCompareViewModel.RunData(
            viaje.copy(best0to60mphMs = 4_800), 50, null, null, emptyList(), emptyList(),
        )
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
        compose.onNodeWithText("🏁 0–60 mph").assertExists()
        compose.onNodeWithText("4.80s").assertExists()
        compose.onNodeWithText("4.50s").assertDoesNotExist()
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
            best0to60mphMs = 4_800,
        )

        val us = TripShareCard.stats(data, en, UnitFormatter(UnitSystem.US, Locale.US)).toMap()
        val metric = TripShareCard.stats(data, en, UnitFormatter(UnitSystem.METRIC, Locale.US)).toMap()

        assertEquals("23.9${NBSP}mi", us["DISTANCE"])
        assertEquals("70${NBSP}mph", us["MAX SPEED"])
        assertEquals("4.80s", us["0–60 mph"])
        assertFalse(us.containsKey("0–100"))
        assertEquals("8.00s", metric["0–100"])
        assertFalse(metric.containsKey("0–60 mph"))
    }

    private companion object {
        const val NBSP = " "
    }
}
