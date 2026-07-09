package com.revscope.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val DangerColor = Color(0xFFFF3040)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

private val dateFormat = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    onOpenSession: (Long) -> Unit = {},
    onCompareSessions: (Long, Long) -> Unit = { _, _ -> },
    vm: SessionViewModel = hiltViewModel(),
) {
    val compareCandidate by vm.compareCandidate.collectAsState()
    val sessions by vm.sessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        TopAppBar(
            title = { Text("Historial", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
        )

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Sin sesiones registradas", color = TextMutedColor, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (compareCandidate != null) {
                    item(key = "compare_hint") {
                        Text(
                            "⚖ Viaje A elegido — toca ⚖ en otro viaje para comparar",
                            color = AccentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        isCompareCandidate = compareCandidate == session.id,
                        onClick = { onOpenSession(session.id) },
                        onCompare = { vm.toggleCompare(session.id, onCompareSessions) },
                        onDelete = { vm.deleteSession(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionEntity,
    isCompareCandidate: Boolean,
    onClick: () -> Unit,
    onCompare: () -> Unit,
    onDelete: () -> Unit,
) {
    val durationMs = (session.endedAt ?: System.currentTimeMillis()) - session.startedAt
    val durationMin = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationSec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCompareCandidate) SurfaceHighColor else SurfaceColor,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(session.startedAt)),
                color = TextPrimaryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = session.adapterName,
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatChip(label = "Duración", value = "%dm %ds".format(durationMin, durationSec))
                StatChip(label = "Max RPM", value = session.maxRpm.toString())
                StatChip(label = "Max km/h", value = session.maxSpeed.toString())
                StatChip(label = "km", value = "%.1f".format(session.distanceKm))
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onCompare) {
            Text(
                "⚖",
                fontSize = 18.sp,
                color = if (isCompareCandidate) AccentColor else TextMutedColor,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Borrar sesión", tint = DangerColor)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMutedColor, fontSize = 10.sp)
    }
}
