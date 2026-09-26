package com.revscope.core.designsystem

import androidx.compose.ui.graphics.Color

object RevScopeColors {
    val Background  = Color(0xFF0A0A0F)
    val Surface     = Color(0xFF12121A)
    val SurfaceHigh = Color(0xFF1C1C28)
    val Accent      = Color(0xFFE8FF00)
    val AccentDim   = Color(0xFF8C9900)
    val Warning     = Color(0xFFFF8C00)
    val Danger      = Color(0xFFFF3040)
    val Success     = Color(0xFF00E676)
    val TextPrimary = Color(0xFFF0F0F8)

    // Texto informativo secundario: 5,8:1 sobre SurfaceHigh (TextMuted se queda en 3,45:1).
    val TextSecondary = Color(0xFF9097B0)

    // Solo decoración, bordes y texto deshabilitado: no llega a 4,5:1 sobre las superficies.
    val TextMuted   = Color(0xFF6B7089)
}
