package com.revscope.app.navigation

import androidx.compose.ui.graphics.Color
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.VehiculoEnEncabezado
import com.revscope.core.obd.connection.ConnectionState

/** Status dot color for a given connection state. Shared by the header selector and the picker sheet. */
fun connectionStatusColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> RevScopeColors.Success
    ConnectionState.Connecting -> RevScopeColors.Warning
    is ConnectionState.Error -> RevScopeColors.Danger
    ConnectionState.Disconnected -> RevScopeColors.TextMuted
}

/** Human-readable connection status. Shared by the header selector and the picker sheet's adapter row. */
fun connectionStatusLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> state.deviceName
    ConnectionState.Connecting -> "Conectando…"
    is ConnectionState.Error -> "Error de enlace"
    ConnectionState.Disconnected -> "Sin conexión"
}

fun vehiculoEnEncabezado(state: ConnectionState, perfil: VehicleProfileEntity?): VehiculoEnEncabezado =
    VehiculoEnEncabezado(
        nombre = perfil?.name,
        esMoto = perfil?.type == "MOTORCYCLE",
        estadoEnlace = connectionStatusLabel(state),
        colorEstado = connectionStatusColor(state),
    )
