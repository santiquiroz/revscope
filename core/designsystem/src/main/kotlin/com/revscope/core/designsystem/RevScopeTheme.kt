package com.revscope.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RevScopeColorScheme = darkColorScheme(
    primary = RevScopeColors.Accent,
    onPrimary = RevScopeColors.Background,
    secondary = RevScopeColors.Success,
    onSecondary = RevScopeColors.Background,
    error = RevScopeColors.Danger,
    onError = RevScopeColors.Background,
    background = RevScopeColors.Background,
    onBackground = RevScopeColors.TextPrimary,
    surface = RevScopeColors.Surface,
    onSurface = RevScopeColors.TextPrimary,
    surfaceVariant = Color(0xFF1A1A26),
    onSurfaceVariant = RevScopeColors.TextSecondary,
)

@Composable
fun RevScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RevScopeColorScheme,
        typography = RevScopeTypography,
        content = content,
    )
}
