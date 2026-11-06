package com.revscope.feature.workshop.taller

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.CapacidadesEcuCard
import com.revscope.feature.workshop.R

@Composable
fun TallerHubContent(
    estado: TallerHubEstado,
    acciones: AccionesHub,
    selectorVehiculo: @Composable () -> Unit = {},
) {
    val conectado = estado.adaptador.conectado
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item(key = "encabezado") { EncabezadoHub(selectorVehiculo) }
        item(key = "adaptador") { LineaAdaptador(estado.adaptador, acciones.onConectarAdaptador) }
        item(key = "principal") { TarjetaPrincipal(estado, acciones) }
        if (estado.adaptador.conectado) {
            item(key = "capacidades") {
                CapacidadesEcuCard(
                    protocolo = estado.capacidades.protocolo,
                    pids = estado.capacidades.pidsAnunciados,
                    pidsPorRango = estado.capacidades.pidsPorRango,
                    tasaEsperada = estado.capacidades.tasaMedidaHz?.let {
                        stringResource(
                            R.string.workshop_hub_measured_rate,
                            LocaleNumbers.decimal(it.toDouble(), 1, LocalConfiguration.current.locales[0]),
                            estado.capacidades.tasaEsperada,
                        )
                    } ?: estado.capacidades.tasaEsperada,
                    explicacion = estado.capacidades.explicacion,
                )
            }
        }
        item(key = "rapidas-titulo") { EncabezadoSeccion(stringResource(R.string.workshop_hub_quick_tools)) }
        item(key = "rapidas") { RejillaHerramientas(HerramientasTaller.rapidas(acciones), conectado) }
        sesionesAnteriores(estado, acciones)
        item(key = "mas-titulo") { EncabezadoSeccion(stringResource(R.string.workshop_hub_more_tools)) }
        HerramientasTaller.mas(acciones).forEach { grupo ->
            item(key = "grupo-${grupo.titulo}") { SubtituloGrupo(stringResource(grupo.titulo)) }
            items(grupo.herramientas, key = { "mas-${it.titulo}" }) { FilaHerramienta(it, conectado) }
        }
    }
}

@Composable
private fun EncabezadoHub(selectorVehiculo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.workshop_hub_title),
            color = RevScopeColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        selectorVehiculo()
    }
}

private data class AspectoAdaptador(
    val icono: ImageVector,
    val color: Color,
    val texto: Int,
    val argumentos: List<Any> = emptyList(),
    val conectar: Boolean,
)

private fun aspecto(adaptador: EstadoAdaptador): AspectoAdaptador = when (adaptador) {
    is EstadoAdaptador.Conectado ->
        AspectoAdaptador(
            Icons.Filled.BluetoothConnected,
            RevScopeColors.Success,
            R.string.workshop_hub_adapter_connected,
            listOf(adaptador.dispositivo),
            conectar = false,
        )
    EstadoAdaptador.Conectando ->
        AspectoAdaptador(
            Icons.Filled.BluetoothSearching,
            RevScopeColors.Warning,
            R.string.workshop_hub_adapter_connecting,
            conectar = false,
        )
    EstadoAdaptador.Desconectado ->
        AspectoAdaptador(
            Icons.Filled.BluetoothDisabled,
            RevScopeColors.TextSecondary,
            R.string.workshop_hub_adapter_missing,
            conectar = true,
        )
    is EstadoAdaptador.ConError ->
        AspectoAdaptador(
            Icons.Filled.ErrorOutline,
            RevScopeColors.Danger,
            R.string.workshop_hub_adapter_error,
            listOf(adaptador.mensaje),
            conectar = true,
        )
}

@Composable
private fun LineaAdaptador(adaptador: EstadoAdaptador, onConectar: () -> Unit) {
    val a = aspecto(adaptador)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(a.icono, contentDescription = null, tint = a.color, modifier = Modifier.size(tamanoIcono()))
        Text(
            stringResource(a.texto, *a.argumentos.toTypedArray()),
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.body,
            modifier = Modifier.weight(1f),
        )
        if (a.conectar) {
            TextButton(onClick = onConectar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.workshop_hub_connect), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        }
    }
}

@Composable
private fun tamanoIcono() = with(LocalDensity.current) { 20.sp.toDp() }

@Composable
private fun TarjetaPrincipal(estado: TallerHubEstado, acciones: AccionesHub) {
    val abierta = estado.sesionAbierta
    when {
        estado.cargando -> Unit
        estado.vehiculo == null -> TarjetaSinVehiculo(acciones.onElegirVehiculo)
        abierta != null -> TarjetaContinuar(abierta, acciones)
        else -> TarjetaIniciar(estado.esMoto, acciones.onIniciarDiagnostico)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaContinuar(sesion: TarjetaSesionAbierta, acciones: AccionesHub) {
    TarjetaTaller {
        Text(stringResource(R.string.workshop_hub_open_session), color = RevScopeColors.Accent, style = RevScopeType.label)
        Text(sesion.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(
            stringResource(
                R.string.workshop_hub_session_since,
                sesion.desde,
                pluralStringResource(R.plurals.workshop_hub_event_count, sesion.eventos, sesion.eventos),
            ),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        if (sesion.codigos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sesion.codigos.forEach { PastillaCodigo(it) }
            }
        }
        sesion.ultimoEvento?.let {
            Text(stringResource(R.string.workshop_hub_latest_event, it), color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        BotonPrincipalTaller(
            stringResource(R.string.workshop_hub_continue_session),
            onClick = { acciones.onAbrirSesion(sesion.id) },
        )
    }
}

@Composable
private fun TarjetaIniciar(esMoto: Boolean, onIniciar: () -> Unit) {
    TarjetaTaller {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(28.dp))
            Text(
                stringResource(if (esMoto) R.string.workshop_hub_vehicle_problem_motorcycle else R.string.workshop_hub_vehicle_problem_car),
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.title,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.workshop_hub_start_diagnosis_description),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        BotonPrincipalTaller(stringResource(R.string.workshop_hub_start_diagnosis), onClick = onIniciar)
    }
}

@Composable
private fun TarjetaSinVehiculo(onElegir: () -> Unit) {
    TarjetaTaller {
        Text(stringResource(R.string.workshop_hub_choose_vehicle_title), color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(
            stringResource(R.string.workshop_hub_sessions_saved_by_vehicle),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        BotonPrincipalTaller(stringResource(R.string.workshop_hub_choose_vehicle), onClick = onElegir)
    }
}

@Composable
private fun RejillaHerramientas(herramientas: List<HerramientaTaller>, conectado: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnas = HerramientasTaller.columnas(maxWidth.value, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            herramientas.chunked(columnas).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    fila.forEach { MosaicoHerramienta(it, conectado, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columnas - fila.size) { Column(Modifier.weight(1f)) {} }
                }
            }
        }
    }
}

@Composable
private fun MosaicoHerramienta(herramienta: HerramientaTaller, conectado: Boolean, modifier: Modifier) {
    val habilitada = conectado || !herramienta.requiereAdaptador
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RevScopeColors.Surface,
        modifier = modifier.heightIn(min = 96.dp).clickable(enabled = habilitada, onClick = herramienta.onAbrir),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
            Icon(
                herramienta.icono,
                contentDescription = null,
                tint = if (habilitada) RevScopeColors.Accent else RevScopeColors.TextSecondary,
                modifier = Modifier.size(26.dp),
            )
            Text(stringResource(herramienta.titulo), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            if (habilitada) {
                Text(stringResource(herramienta.descripcion), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            } else {
                AvisoRequiereAdaptador()
            }
        }
    }
}

@Composable
private fun AvisoRequiereAdaptador() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.LinkOff,
            contentDescription = null,
            tint = RevScopeColors.Warning,
            modifier = Modifier.size(with(LocalDensity.current) { 14.sp.toDp() }),
        )
        Text(stringResource(R.string.workshop_hub_adapter_required), color = RevScopeColors.Warning, style = RevScopeType.bodySmall)
    }
}

private fun LazyListScope.sesionesAnteriores(estado: TallerHubEstado, acciones: AccionesHub) {
    if (estado.vehiculo == null) return
    item(key = "anteriores-titulo") { EncabezadoSeccion(stringResource(R.string.workshop_hub_previous_sessions)) }
    if (estado.anteriores.isEmpty()) {
        item(key = "anteriores-vacio") {
            Text(
                stringResource(R.string.workshop_hub_no_closed_sessions),
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body,
            )
        }
        return
    }
    items(estado.anterioresVisibles, key = { "sesion-${it.id}" }) { FilaSesion(it) { acciones.onAbrirSesion(it.id) } }
    if (estado.hayMasAnteriores) {
        item(key = "anteriores-todas") {
            TextButton(onClick = acciones.onVerTodas, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(
                        if (estado.verTodas) R.string.workshop_hub_show_fewer else R.string.workshop_hub_show_all,
                        estado.anteriores.size,
                    ),
                    color = RevScopeColors.TextPrimary,
                    style = RevScopeType.label,
                )
            }
        }
    }
}

@Composable
private fun FilaSesion(sesion: FilaSesionAnterior, onAbrir: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RevScopeColors.Surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onAbrir),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = RevScopeColors.TextSecondary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(sesion.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                Text(sesion.fecha, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = RevScopeColors.TextSecondary)
        }
    }
}

@Composable
private fun SubtituloGrupo(titulo: String) {
    Text(titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label, modifier = Modifier.semantics { heading() })
}

@Composable
private fun FilaHerramienta(herramienta: HerramientaTaller, conectado: Boolean) {
    val habilitada = conectado || !herramienta.requiereAdaptador
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RevScopeColors.Surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = habilitada, onClick = herramienta.onAbrir),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                herramienta.icono,
                contentDescription = null,
                tint = if (habilitada) RevScopeColors.Accent else RevScopeColors.TextSecondary,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(herramienta.titulo), color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                if (habilitada) {
                    Text(stringResource(herramienta.descripcion), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
                } else {
                    AvisoRequiereAdaptador()
                }
            }
        }
    }
}
