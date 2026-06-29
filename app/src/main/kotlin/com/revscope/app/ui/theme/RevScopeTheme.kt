package com.revscope.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// RevScope color palette — Dark luxury + Racing HUD
val Background = Color(0xFF0A0A0F)
val Surface = Color(0xFF12121A)
val Accent = Color(0xFFE8FF00)       // Racing yellow — active gauges
val Warning = Color(0xFFFF8C00)      // High temperatures
val Danger = Color(0xFFFF3040)       // Critical alerts
val Success = Color(0xFF00E676)      // Systems OK
val TextPrimary = Color(0xFFF0F0F8)  // Warm white
val TextMuted = Color(0xFF6B7089)    // Muted labels

private val RevScopeColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    secondary = Success,
    onSecondary = Background,
    error = Danger,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1A1A26),
    onSurfaceVariant = TextMuted,
)

@Composable
fun RevScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RevScopeColorScheme,
        typography = RevScopeTypography,
        content = content
    )
}
