package com.revscope.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem

val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.METRIC }

@Composable
fun rememberUnitFormatter(): UnitFormatter {
    val system = LocalUnitSystem.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(system, locale) { UnitFormatter(system, locale) }
}
