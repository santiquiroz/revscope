package com.revscope.app.navigation

import androidx.compose.ui.graphics.Color
import com.revscope.app.R
import com.revscope.core.common.i18n.StringResolver
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
fun connectionStatusLabel(state: ConnectionState, resolver: StringResolver): String = when (state) {
    is ConnectionState.Connected -> state.deviceName
    ConnectionState.Connecting -> resolver.get(R.string.app_connection_connecting)
    is ConnectionState.Error -> resolver.get(R.string.app_connection_error)
    ConnectionState.Disconnected -> resolver.get(R.string.app_connection_none)
}

// El punto de color no le dice nada a TalkBack: la descripción nombra el estado.
fun estadoEnlaceAccesible(state: ConnectionState, resolver: StringResolver): String = when (state) {
    is ConnectionState.Connected -> resolver.get(R.string.app_connection_connected_a11y, state.deviceName)
    else -> connectionStatusLabel(state, resolver)
}

fun vehiculoEnEncabezado(
    state: ConnectionState,
    perfil: VehicleProfileEntity?,
    resolver: StringResolver,
): VehiculoEnEncabezado =
    VehiculoEnEncabezado(
        nombre = perfil?.name,
        esMoto = perfil?.type == "MOTORCYCLE",
        estadoEnlace = estadoEnlaceAccesible(state, resolver),
        colorEstado = connectionStatusColor(state),
    )
