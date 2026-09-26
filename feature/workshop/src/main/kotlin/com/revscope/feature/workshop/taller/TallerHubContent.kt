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
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType

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
        item(key = "rapidas-titulo") { EncabezadoSeccion("Herramientas rápidas") }
        item(key = "rapidas") { RejillaHerramientas(HerramientasTaller.rapidas(acciones), conectado) }
        sesionesAnteriores(estado, acciones)
        item(key = "mas-titulo") { EncabezadoSeccion("Más herramientas") }
        HerramientasTaller.mas(acciones).forEach { grupo ->
            item(key = "grupo-${grupo.titulo}") { SubtituloGrupo(grupo.titulo) }
            items(grupo.herramientas, key = { "mas-${it.titulo}" }) { FilaHerramienta(it, conectado) }
        }
    }
}

@Composable
private fun EncabezadoHub(selectorVehiculo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Taller",
            color = RevScopeColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        selectorVehiculo()
    }
}

private data class AspectoAdaptador(val icono: ImageVector, val color: Color, val texto: String, val conectar: Boolean)

private fun aspecto(adaptador: EstadoAdaptador): AspectoAdaptador = when (adaptador) {
    is EstadoAdaptador.Conectado ->
        AspectoAdaptador(
            Icons.Filled.BluetoothConnected,
            RevScopeColors.Success,
            "Adaptador conectado · ${adaptador.dispositivo}",
            false,
        )
    EstadoAdaptador.Conectando ->
        AspectoAdaptador(Icons.Filled.BluetoothSearching, RevScopeColors.Warning, "Conectando con el adaptador…", false)
    EstadoAdaptador.Desconectado ->
        AspectoAdaptador(
            Icons.Filled.BluetoothDisabled,
            RevScopeColors.TextSecondary,
            "Sin adaptador: las herramientas marcadas lo requieren",
            true,
        )
    is EstadoAdaptador.ConError ->
        AspectoAdaptador(
            Icons.Filled.ErrorOutline,
            RevScopeColors.Danger,
            "Error del adaptador: ${adaptador.mensaje}",
            true,
        )
}

@Composable
private fun LineaAdaptador(adaptador: EstadoAdaptador, onConectar: () -> Unit) {
    val a = aspecto(adaptador)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(a.icono, contentDescription = null, tint = a.color, modifier = Modifier.size(tamanoIcono()))
        Text(a.texto, color = RevScopeColors.TextPrimary, style = RevScopeType.body, modifier = Modifier.weight(1f))
        if (a.conectar) {
            TextButton(onClick = onConectar, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Conectar", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
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
        Text("SESIÓN ABIERTA", color = RevScopeColors.Accent, style = RevScopeType.label)
        Text(sesion.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(
            "Desde el ${sesion.desde} · ${eventosTexto(sesion.eventos)}",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        if (sesion.codigos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sesion.codigos.forEach { PastillaCodigo(it) }
            }
        }
        sesion.ultimoEvento?.let {
            Text("Último: $it", color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        }
        BotonPrincipalTaller("Continuar sesión", onClick = { acciones.onAbrirSesion(sesion.id) })
    }
}

private fun eventosTexto(n: Int): String = if (n == 1) "1 evento" else "$n eventos"

@Composable
private fun TarjetaIniciar(esMoto: Boolean, onIniciar: () -> Unit) {
    TarjetaTaller {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(28.dp))
            Text(
                if (esMoto) "¿Qué le pasa a la moto?" else "¿Qué le pasa al vehículo?",
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.title,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Anota los síntomas y abre una sesión: cada lectura de códigos, captura y chequeo queda en una línea de " +
                "tiempo con hora, lista para comparar y para el informe.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        BotonPrincipalTaller("Iniciar diagnóstico", onClick = onIniciar)
    }
}

@Composable
private fun TarjetaSinVehiculo(onElegir: () -> Unit) {
    TarjetaTaller {
        Text("Elige el vehículo que vas a revisar", color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(
            "Las sesiones de diagnóstico se guardan por vehículo.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        BotonPrincipalTaller("Elegir vehículo", onClick = onElegir)
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
            Text(herramienta.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            if (habilitada) {
                Text(herramienta.descripcion, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
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
        Text("Requiere adaptador", color = RevScopeColors.Warning, style = RevScopeType.bodySmall)
    }
}

private fun LazyListScope.sesionesAnteriores(estado: TallerHubEstado, acciones: AccionesHub) {
    if (estado.vehiculo == null) return
    item(key = "anteriores-titulo") { EncabezadoSeccion("Sesiones anteriores") }
    if (estado.anteriores.isEmpty()) {
        item(key = "anteriores-vacio") {
            Text(
                "Todavía no hay sesiones cerradas de este vehículo.",
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
                    if (estado.verTodas) "Ver menos" else "Ver todas (${estado.anteriores.size})",
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
                Text(herramienta.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
                if (habilitada) {
                    Text(herramienta.descripcion, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
                } else {
                    AvisoRequiereAdaptador()
                }
            }
        }
    }
}
