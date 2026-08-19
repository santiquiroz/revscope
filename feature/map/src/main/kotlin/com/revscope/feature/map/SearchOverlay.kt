package com.revscope.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.data.db.entities.SavedPlaceEntity
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
    savedPlaces: List<SavedPlaceEntity>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (PlaceResult) -> Unit,
    onSelectSaved: (SavedPlaceEntity) -> Unit,
    onSaveFavorite: (PlaceResult) -> Unit,
    onRemoveSaved: (Long) -> Unit,
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

        val specials = savedPlaces.filter { it.type == "HOME" || it.type == "WORK" }
        val favorites = savedPlaces.filter { it.type == "FAVORITE" }
        if (query.isEmpty() && (specials.isNotEmpty() || favorites.isNotEmpty())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                (specials + favorites).forEach { place ->
                    PlaceChip(place, onSelectSaved)
                    Spacer(Modifier.width(8.dp))
                }
            }
        }

        val recents = savedPlaces.filter { it.type == "RECENT" }.take(6)
        if (query.isEmpty() && recents.isNotEmpty()) {
            Surface(
                color = PanelColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column {
                    recents.forEach { place -> RecentRow(place, onSelectSaved, onRemoveSaved) }
                }
            }
        }

        val showPanel = query.isNotEmpty() && (searching || results.isNotEmpty() || query.trim().length >= 3)
        if (!showPanel) return@Column

        Surface(
            color = PanelColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            when {
                results.isNotEmpty() -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(results) { place -> ResultRow(place, onSelect, onSaveFavorite) }
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
private fun PlaceChip(place: SavedPlaceEntity, onSelect: (SavedPlaceEntity) -> Unit) {
    Surface(
        color = PanelColor,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.height(48.dp).clickable { onSelect(place) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = when (place.type) {
                    "HOME" -> Icons.Default.Home
                    "WORK" -> Icons.Default.Work
                    else -> Icons.Default.Star
                },
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when (place.type) { "HOME" -> "Casa"; "WORK" -> "Trabajo"; else -> place.name },
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RecentRow(
    place: SavedPlaceEntity,
    onSelect: (SavedPlaceEntity) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(start = 14.dp),
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        Text(
            place.name,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp),
        )
        IconButton(onClick = { onRemove(place.id) }) {
            Icon(Icons.Default.Close, contentDescription = "Quitar de recientes", tint = TextMuted)
        }
    }
}

@Composable
private fun ResultRow(place: PlaceResult, onSelect: (PlaceResult) -> Unit, onSaveFavorite: (PlaceResult) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(place) }
            .background(Color.Transparent)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(place.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (place.subtitle.isNotEmpty()) {
                Text(place.subtitle, color = TextMuted, fontSize = 12.sp)
            }
        }
        IconButton(onClick = { onSaveFavorite(place) }) {
            Icon(Icons.Default.Star, contentDescription = "Guardar favorito", tint = TextMuted)
        }
    }
}
