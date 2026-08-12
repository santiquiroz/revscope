package com.revscope.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.feature.map.search.PlaceResult

private val PanelColor = Color(0xE6121218)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimary = Color(0xFFF0F0F8)
private val TextMuted = Color(0xFF6B7089)

/**
 * Campo de búsqueda de direcciones sobre el mapa. Elegir un resultado fija el destino por el
 * mismo camino que el long-press, así que el cálculo de ruta y el chip de ETA ya existentes
 * siguen funcionando sin cambios.
 */
@Composable
fun SearchOverlay(
    query: String,
    results: List<PlaceResult>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (PlaceResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Buscar dirección o lugar", color = TextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = PanelColor,
                unfocusedContainerColor = PanelColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = Color(0xFF2A2A3A),
                cursorColor = AccentColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        val showPanel = query.isNotEmpty() && (searching || results.isNotEmpty() || query.trim().length >= 3)
        if (!showPanel) return@Column

        Surface(
            color = PanelColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            when {
                results.isNotEmpty() -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(results) { place -> ResultRow(place, onSelect) }
                }
                searching -> Text(
                    "Buscando…",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp),
                )
                else -> Text(
                    "Sin resultados — revisa la escritura o si hay internet",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultRow(place: PlaceResult, onSelect: (PlaceResult) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(place) }
            .background(Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(place.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (place.subtitle.isNotEmpty()) {
            Text(place.subtitle, color = TextMuted, fontSize = 12.sp)
        }
    }
}
