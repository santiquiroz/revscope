package com.revscope.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.designsystem.AccionEstado
import com.revscope.core.designsystem.ConfirmarDestructivoDialog
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.FranjaSelectorVehiculo
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

data class HistorialUi(
    val sesiones: List<SessionEntity>,
    val perfiles: List<VehicleProfileEntity>,
    val filtro: Long?,
    val candidatoComparar: Long?,
)

data class AccionesHistorial(
    val onAbrir: (Long) -> Unit,
    val onComparar: (Long) -> Unit,
    val onBorrar: (Long) -> Unit,
    val onFiltrar: (Long?) -> Unit,
    val onIniciarViaje: () -> Unit,
)

@Composable
fun SessionHistoryScreen(
    onOpenSession: (Long) -> Unit = {},
    onCompareSessions: (Long, Long) -> Unit = { _, _ -> },
    onIniciarViaje: () -> Unit = {},
    selectorVehiculo: @Composable () -> Unit = {},
    vm: SessionViewModel = hiltViewModel(),
) {
    val compareCandidate by vm.compareCandidate.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val filter by vm.filter.collectAsState()
    val resolver = AndroidStringResolver(LocalContext.current)

    SessionHistoryContent(
        historial = HistorialUi(sessions, profiles, filter, compareCandidate),
        resolver = resolver,
        acciones = AccionesHistorial(
            onAbrir = onOpenSession,
            onComparar = { id -> vm.toggleCompare(id, onCompareSessions) },
            onBorrar = vm::deleteSession,
            onFiltrar = vm::setFilter,
            onIniciarViaje = onIniciarViaje,
        ),
        selectorVehiculo = selectorVehiculo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionHistoryContent(
    historial: HistorialUi,
    resolver: StringResolver,
    acciones: AccionesHistorial,
    selectorVehiculo: @Composable () -> Unit = {},
) {
    var porBorrar by remember { mutableStateOf<SessionEntity?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RevScopeColors.Background),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.trips_history_title), color = RevScopeColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = RevScopeColors.Surface),
        )
        FranjaSelectorVehiculo(selector = selectorVehiculo)

        if (historial.perfiles.isNotEmpty()) {
            VehicleFilterRow(
                profiles = historial.perfiles,
                selected = historial.filtro,
                onSelect = acciones.onFiltrar,
            )
        }

        if (historial.sesiones.isEmpty()) {
            HistorialVacio(historial.filtro, acciones)
        } else {
            ListaSesiones(historial, acciones, onPedirBorrado = { porBorrar = it })
        }
    }
    porBorrar?.let { sesion ->
        ConfirmarBorrarViajeDialog(
            sesion = sesion,
            resolver = resolver,
            onConfirmar = {
                porBorrar = null
                acciones.onBorrar(sesion.id)
            },
            onCancelar = { porBorrar = null },
        )
    }
}

@Composable
private fun HistorialVacio(filtro: Long?, acciones: AccionesHistorial) {
    val accion = if (filtro == null) {
        AccionEstado(stringResource(R.string.trips_start_trip), acciones.onIniciarViaje)
    } else {
        AccionEstado(stringResource(R.string.trips_view_all_trips)) { acciones.onFiltrar(null) }
    }
    val mensaje = if (filtro == null) {
        stringResource(R.string.trips_no_trips_saved)
    } else {
        stringResource(R.string.trips_no_trips_for_filter)
    }
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        EmptyState(mensaje = mensaje, accion = accion, icono = Icons.Default.Route)
    }
}

@Composable
private fun ListaSesiones(
    historial: HistorialUi,
    acciones: AccionesHistorial,
    onPedirBorrado: (SessionEntity) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (historial.candidatoComparar != null) {
            item(key = "compare_hint") {
                Text(
                    stringResource(R.string.trips_trip_a_selected),
                    color = RevScopeColors.Accent,
                    style = RevScopeType.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        items(historial.sesiones, key = { it.id }) { session ->
            SessionItem(
                session = session,
                isCompareCandidate = historial.candidatoComparar == session.id,
                onClick = { acciones.onAbrir(session.id) },
                onCompare = { acciones.onComparar(session.id) },
                onDelete = { onPedirBorrado(session) },
            )
        }
    }
}

@Composable
internal fun ConfirmarBorrarViajeDialog(
    sesion: SessionEntity,
    resolver: StringResolver,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    ConfirmarDestructivoDialog(
        titulo = resolver.get(R.string.trips_delete_trip_title),
        mensaje = descripcionViaje(sesion, resolver),
        avisos = listOf(resolver.get(R.string.trips_delete_trip_message)),
        textoConfirmar = resolver.get(R.string.trips_delete_trip_confirm),
        onConfirmar = onConfirmar,
        onCancelar = onCancelar,
    )
}

internal fun descripcionViaje(sesion: SessionEntity, resolver: StringResolver): String =
    resolver.get(
        R.string.trips_trip_of,
        SimpleDateFormat("dd MMM yyyy  HH:mm", resolver.locale).format(Date(sesion.startedAt)), // i18n-ignore
        LocaleNumbers.decimal(sesion.distanceKm.toDouble(), 1, resolver.locale),
    )

@Composable
internal fun SessionItem(
    session: SessionEntity,
    isCompareCandidate: Boolean,
    onClick: () -> Unit,
    onCompare: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd MMM yyyy  HH:mm", locale) } // i18n-ignore
    val durationMs = (session.endedAt ?: System.currentTimeMillis()) - session.startedAt
    val durationMin = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val durationSec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCompareCandidate) RevScopeColors.SurfaceHigh else RevScopeColors.Surface,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(session.startedAt)),
                color = RevScopeColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = session.adapterName,
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatChip(
                    label = stringResource(R.string.trips_duration),
                    value = stringResource(R.string.trips_duration_format, durationMin, durationSec),
                )
                StatChip(label = stringResource(R.string.trips_chip_max_rpm), value = session.maxRpm.toString())
                StatChip(label = stringResource(R.string.trips_chip_max_speed), value = session.maxSpeed.toString())
                StatChip(
                    label = stringResource(R.string.trips_chip_distance),
                    value = LocaleNumbers.decimal(session.distanceKm.toDouble(), 1, locale),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onCompare,
            modifier = Modifier.semantics { selected = isCompareCandidate },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = if (isCompareCandidate) stringResource(R.string.trips_remove_from_comparison) else stringResource(R.string.trips_compare),
                tint = if (isCompareCandidate) RevScopeColors.Accent else RevScopeColors.TextSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.trips_delete_trip_confirm), tint = RevScopeColors.Danger)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = RevScopeColors.Accent, style = RevScopeType.label.conCifrasTabulares(), fontWeight = FontWeight.Bold)
        Text(label, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleFilterRow(
    profiles: List<VehicleProfileEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "filter_all") {
            VehicleFilterChip(
                label = stringResource(R.string.trips_filter_all),
                icon = null,
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(profiles, key = { it.id }) { profile ->
            VehicleFilterChip(
                label = profile.name,
                icon = if (profile.type == "MOTORCYCLE") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
                selected = selected == profile.id,
                onClick = { onSelect(profile.id) },
            )
        }
        item(key = "filter_none") {
            VehicleFilterChip(
                label = stringResource(R.string.trips_filter_no_vehicle),
                icon = null,
                selected = selected == SessionViewModel.NO_VEHICLE_FILTER,
                onClick = { onSelect(SessionViewModel.NO_VEHICLE_FILTER) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleFilterChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = RevScopeType.bodySmall) },
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = RevScopeColors.Surface,
            labelColor = RevScopeColors.TextSecondary,
            iconColor = RevScopeColors.TextSecondary,
            selectedContainerColor = RevScopeColors.Accent,
            selectedLabelColor = RevScopeColors.Background,
            selectedLeadingIconColor = RevScopeColors.Background,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = RevScopeColors.SurfaceHigh,
            selectedBorderColor = RevScopeColors.Accent,
        ),
    )
}
