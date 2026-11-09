package com.revscope.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.designsystem.LocalUnitSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadaresAlertasUnidadesComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(system: UnitSystem, content: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalUnitSystem provides system) { content() } }
    }

    @Test
    fun `temperature field label uses fahrenheit in US`() {
        render(UnitSystem.US) { TempMaxField("221", {}) }
        compose.onNodeWithText("Max temp °F").assertExists()
    }

    @Test
    fun `temperature field label keeps celsius in UK`() {
        render(UnitSystem.UK) { TempMaxField("105", {}) }
        compose.onNodeWithText("Max temp °C").assertExists()
    }

    @Test
    fun `camera radius field label uses feet in US`() {
        render(UnitSystem.US) { CameraRadiusField("820", {}) }
        compose.onNodeWithText("Alert radius (ft)").assertExists()
    }

    @Test
    fun `camera radius hint shows the range in feet in US`() {
        render(UnitSystem.US) { CameraRadiusHint() }
        compose.onNodeWithText(
            "Distance at which the voice warning plays (328-3281 ft). Fewer feet = fewer early warnings.",
        ).assertExists()
    }

    @Test
    fun `camera description shows the download area in miles in US`() {
        render(UnitSystem.US) { CamerasDescription() }
        compose.onNodeWithText("Downloads speed cameras within 31 mi of you.", substring = true).assertExists()
    }
}
