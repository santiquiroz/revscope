package com.revscope.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextMutedColor = Color(0xFF6B7089)

/** Header tappable de una sección colapsable de Ajustes: título + contador + chevron animado. */
@Composable
internal fun SettingsSectionHeader(
    title: String,
    itemCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "settingsSectionChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            color = AccentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text("$itemCount", color = TextMutedColor, fontSize = 12.sp)
        Text(
            "⌄",
            color = TextMutedColor,
            fontSize = 16.sp,
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}
