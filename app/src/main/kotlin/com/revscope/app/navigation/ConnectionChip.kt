package com.revscope.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState

private val PillLabelColor = Color(0xFFB0B4C8)

/** Status dot color for a given connection state. Shared by the pill and the picker sheet. */
fun connectionStatusColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> Color(0xFF4CAF50)
    ConnectionState.Connecting -> Color(0xFFFFC107)
    is ConnectionState.Error -> Color(0xFFFF5252)
    ConnectionState.Disconnected -> Color(0xFF6B7089)
}

/** Human-readable connection status. Shared by the pill and the picker sheet's adapter row. */
fun connectionStatusLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> state.deviceName
    ConnectionState.Connecting -> "Conectando…"
    is ConnectionState.Error -> "Error de enlace"
    ConnectionState.Disconnected -> "Sin conexión"
}

private fun vehicleTypeIcon(profile: VehicleProfileEntity?) =
    if (profile?.type == "MOTORCYCLE") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar

/**
 * Floating vehicle switcher pill: connection-status dot + vehicle type icon + active
 * profile name + chevron. Tapping opens the vehicle picker sheet for a hot swap.
 */
@Composable
fun VehicleSwitcherPill(
    connectionState: ConnectionState,
    activeProfile: VehicleProfileEntity?,
    onClick: () -> Unit,
) {
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
                    .background(connectionStatusColor(connectionState), CircleShape)
            )
            Icon(
                imageVector = vehicleTypeIcon(activeProfile),
                contentDescription = null,
                tint = PillLabelColor,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(14.dp),
            )
            Text(
                text = activeProfile?.name ?: "Sin vehículo",
                color = PillLabelColor,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Cambiar vehículo",
                tint = PillLabelColor,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(14.dp),
            )
        }
    }
}
