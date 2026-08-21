package com.revscope.feature.settings

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.intelligence.provider.AI_PROVIDER_CUSTOM
import com.revscope.core.intelligence.provider.AI_PROVIDER_NODO

// Cada archivo de esta pantalla mantiene su propia copia privada de esta paleta —
// mismo patrón ya usado en OfflineMapSection.kt y Mode22ScannerScreen.kt: un `internal`
// compartido chocaría en tiempo de compilación con esos `private val` del mismo nombre.
private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        color = AccentColor,
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimaryColor, fontSize = 13.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextMutedColor, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgColor,
                checkedTrackColor = AccentColor,
            ),
        )
    }
}

@Composable
internal fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, color = TextPrimaryColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("›", color = TextMutedColor, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimaryColor,
    unfocusedTextColor = TextPrimaryColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = SurfaceHighColor,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = TextMutedColor,
    cursorColor = AccentColor,
)

/** Ni el endpoint genérico ni Nodo traen búsqueda web del lado del servidor. */
internal fun aiProviderSupportsWebSearch(provider: String): Boolean =
    provider != AI_PROVIDER_CUSTOM && provider != AI_PROVIDER_NODO
