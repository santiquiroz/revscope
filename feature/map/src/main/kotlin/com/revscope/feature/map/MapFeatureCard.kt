package com.revscope.feature.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tarjeta compacta al tocar un ícono del mapa (fix D) — mismo idiom visual que el resto de
 * banners del screen (Surface oscura + acento amarillo). Ocupa el slot inferior que en su
 * ausencia usan [com.revscope.feature.map.navigation.NavigationProgressBar] o [RouteInfoChip]
 * — ver LiveMapScreen, que la prioriza sobre ambos mientras está abierta.
 */
@Composable
internal fun MapFeatureCard(description: MapFeatureDescription, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = Color(0xF2121218), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column {
                Text(description.title, color = Color(0xFFE8FF00), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                description.subtitle?.let {
                    Text(it, color = Color(0xFFF0F0F8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar descripción", tint = Color(0xFF6B7089))
            }
        }
    }
}
