package com.revscope.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.connection.ConnectionState

@Composable
fun ConnectionChip(state: ConnectionState, onClick: () -> Unit) {
    val (color, label) = when (state) {
        is ConnectionState.Connected -> Color(0xFF4CAF50) to state.deviceName
        ConnectionState.Connecting -> Color(0xFFFFC107) to "Conectando…"
        is ConnectionState.Error -> Color(0xFFFF5252) to "Error de enlace"
        ConnectionState.Disconnected -> Color(0xFF6B7089) to "Sin conexión"
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xE61C1C28),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = Color(0xFFB0B4C8),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
