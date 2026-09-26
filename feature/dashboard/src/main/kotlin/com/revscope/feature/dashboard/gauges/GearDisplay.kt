package com.revscope.feature.dashboard.gauges

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable

private val ALTO_MARCHA = 104.dp

@Composable
fun GearDisplay(
    gear: Int,
    isCalibrated: Boolean,
    gearCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    val label = if (gear == 0) "N" else gear.toString()
    val color = when {
        gear <= 0 -> RevScopeColors.TextMuted
        gear <= gearCount / 3 -> RevScopeColors.Success
        gear <= gearCount * 2 / 3 -> RevScopeColors.Accent
        gear <= gearCount -> RevScopeColors.Warning
        else -> RevScopeColors.TextMuted
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextoAjustable(
            texto = label,
            estilo = RevScopeType.numeros.copy(fontSize = 96.sp, lineHeight = 96.sp, fontWeight = FontWeight.ExtraBold),
            color = color,
            minimo = 24.sp,
            modifier = Modifier.fillMaxWidth().height(ALTO_MARCHA),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            imageVector = if (isCalibrated) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = if (isCalibrated) "Marchas calibradas" else "Calibrando marchas",
            tint = if (isCalibrated) RevScopeColors.Success else RevScopeColors.TextMuted,
        )
    }
}
