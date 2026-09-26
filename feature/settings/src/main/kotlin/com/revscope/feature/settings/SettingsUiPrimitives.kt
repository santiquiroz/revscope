package com.revscope.feature.settings

import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.RevScopeColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.intelligence.provider.AI_PROVIDER_CUSTOM
import com.revscope.core.intelligence.provider.AI_PROVIDER_NODO

// Cada archivo de esta pantalla mantiene su propia copia privada de esta paleta —
// mismo patrón ya usado en OfflineMapSection.kt y Mode22ScannerScreen.kt: un `internal`
// compartido chocaría en tiempo de compilación con esos `private val` del mismo nombre.

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        color = RevScopeColors.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = RevScopeColors.TextPrimary, fontSize = 13.sp)
            if (subtitle != null) {
                Text(subtitle, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RevScopeColors.Background,
                checkedTrackColor = RevScopeColors.Accent,
            ),
        )
    }
}

@Composable
internal fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, color = RevScopeColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("›", color = RevScopeColors.TextSecondary, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RevScopeColors.TextPrimary,
    unfocusedTextColor = RevScopeColors.TextPrimary,
    focusedBorderColor = RevScopeColors.Accent,
    unfocusedBorderColor = RevScopeColors.SurfaceHigh,
    focusedLabelColor = RevScopeColors.Accent,
    unfocusedLabelColor = RevScopeColors.TextSecondary,
    cursorColor = RevScopeColors.Accent,
)

/** Ni el endpoint genérico ni Nodo traen búsqueda web del lado del servidor. */
internal fun aiProviderSupportsWebSearch(provider: String): Boolean =
    provider != AI_PROVIDER_CUSTOM && provider != AI_PROVIDER_NODO
