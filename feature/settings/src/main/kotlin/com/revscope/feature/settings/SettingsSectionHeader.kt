package com.revscope.feature.settings

import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.RevScopeColors
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


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
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .clickable(onClickLabel = if (expanded) "Plegar" else "Desplegar", role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Desplegada" else "Plegada" }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            color = RevScopeColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text("$itemCount", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = RevScopeColors.TextSecondary,
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}
