package com.revscope.feature.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.designsystem.FranjaSelectorVehiculo
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.connection.ConnectionState
import java.util.Locale

internal enum class EnlaceAdaptador(@StringRes val descripcion: Int) {
    CONECTADO(R.string.drive_link_connected),
    CONECTANDO(R.string.drive_link_connecting),
    ERROR(R.string.drive_link_error),
    SIN_ADAPTADOR(R.string.drive_link_none),
}

internal fun enlaceAdaptador(estado: ConnectionState): EnlaceAdaptador = when (estado) {
    is ConnectionState.Connected -> EnlaceAdaptador.CONECTADO
    ConnectionState.Connecting -> EnlaceAdaptador.CONECTANDO
    is ConnectionState.Error -> EnlaceAdaptador.ERROR
    ConnectionState.Disconnected -> EnlaceAdaptador.SIN_ADAPTADOR
}

private fun iconoEnlace(enlace: EnlaceAdaptador): ImageVector = when (enlace) {
    EnlaceAdaptador.CONECTADO -> Icons.Default.BluetoothConnected
    EnlaceAdaptador.CONECTANDO -> Icons.Default.BluetoothSearching
    EnlaceAdaptador.ERROR -> Icons.Default.BluetoothDisabled
    EnlaceAdaptador.SIN_ADAPTADOR -> Icons.Default.Bluetooth
}

private fun colorEnlace(enlace: EnlaceAdaptador): Color = when (enlace) {
    EnlaceAdaptador.CONECTADO -> RevScopeColors.Success
    EnlaceAdaptador.CONECTANDO -> RevScopeColors.Warning
    EnlaceAdaptador.ERROR -> RevScopeColors.Danger
    EnlaceAdaptador.SIN_ADAPTADOR -> RevScopeColors.TextSecondary
}

private const val VOLTAJE_BAJO = 11.8

/** Encabezado de Conducir: barra con el estado del adaptador y, debajo, el selector de vehículo. */
@Composable
internal fun DashboardTopBar(
    enlace: EnlaceAdaptador,
    voltaje: Double?,
    selectorVehiculo: @Composable () -> Unit,
    onAdaptador: () -> Unit,
    onModoPista: () -> Unit,
    onAjustes: () -> Unit,
) {
    Column {
        BarraConducir(enlace, voltaje, onAdaptador, onModoPista, onAjustes)
        FranjaSelectorVehiculo(selector = selectorVehiculo)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraConducir(
    enlace: EnlaceAdaptador,
    voltaje: Double?,
    onAdaptador: () -> Unit,
    onModoPista: () -> Unit,
    onAjustes: () -> Unit,
) {
    TopAppBar(
        title = {
            TextoAjustable(
                texto = "RevScope",
                estilo = RevScopeType.title,
                color = RevScopeColors.TextPrimary,
                alineacion = TextAlign.Start,
                minimo = 10.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onAdaptador) {
                Icon(
                    iconoEnlace(enlace),
                    contentDescription = stringResource(enlace.descripcion),
                    tint = colorEnlace(enlace),
                )
            }
        },
        actions = {
            voltaje?.let { VoltajeBateria(it) }
            IconButton(onClick = onModoPista) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = stringResource(R.string.drive_track_mode),
                    tint = RevScopeColors.TextSecondary,
                )
            }
            IconButton(onClick = onAjustes) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.drive_settings),
                    tint = RevScopeColors.TextSecondary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = RevScopeColors.Surface),
    )
}

@Composable
private fun VoltajeBateria(voltios: Double) {
    Text(
        // Locale por defecto (igual que el String.format de antes): la unidad y su número se
        // convierten en la tarea de unidades; las capturas en español se grabaron con punto.
        text = LocaleNumbers.decimal(voltios, 1, Locale.getDefault()) + " V",
        style = RevScopeType.bodySmall.conCifrasTabulares(),
        color = if (voltios < VOLTAJE_BAJO) RevScopeColors.Danger else RevScopeColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier.padding(end = 4.dp),
    )
}
