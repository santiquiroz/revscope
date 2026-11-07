package com.revscope.feature.map.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.designsystem.LocalUnitSystem
import com.revscope.core.navigation.LatLon
import com.revscope.core.navigation.Maneuver
import com.revscope.core.navigation.NavigationState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS")
class NavigationBannerUnidadesTest {

    @get:Rule
    val compose = createComposeRule()

    private val state = NavigationState(
        maneuver = Maneuver("turn", "right", "Carrera 52"),
        maneuverIndex = 1,
        distanceToManeuverM = 150,
        distanceRemainingM = 5_000,
        durationRemainingS = 600,
        snapped = LatLon(6.24, -75.58),
        offRoute = false,
        arrived = false,
    )

    @Test
    fun `en US el banner y la barra de progreso muestran pies millas y mph`() {
        compose.setContent {
            CompositionLocalProvider(LocalUnitSystem provides UnitSystem.US) {
                NavigationBanner(state = state, onStop = {})
                NavigationProgressBar(state = state, speedKmh = 50)
            }
        }

        compose.onNodeWithText("490 ft").assertExists()
        compose.onNodeWithText("3.1 mi").assertExists()
        compose.onNodeWithText("31 mph").assertExists()
    }
}
