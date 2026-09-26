package com.revscope.feature.dtc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.FilaEtiquetaValor
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.viewmodel.ConnectionViewModel

data class DtcPantallaUi(
    val estado: DtcUiState,
    val freezeFrame: List<FreezeFrameItem> = emptyList(),
    val estadoMil: String? = null,
) {
    val ocupado: Boolean get() = estado is DtcUiState.Reading || estado is DtcUiState.Clearing
    val puedeBorrar: Boolean get() = (estado as? DtcUiState.HasCodes)?.codes?.isNotEmpty() == true
}

data class AccionesDtc(
    val onVolver: () -> Unit,
    val onLeer: () -> Unit,
    val onBorrar: () -> Unit,
    val onOpenAiValue: () -> Unit,
)

@Composable
fun DtcScreen(
    onNavigateBack: () -> Unit,
    connectionVm: ConnectionViewModel = hiltViewModel(),
    onOpenAiValue: () -> Unit = {},
    vm: DtcViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val freezeFrame by vm.freezeFrame.collectAsState()
    val estadoMil by vm.estadoMil.collectAsState()
    val confirmacion by vm.confirmacionBorrado.collectAsState()

    DtcContent(
        pantalla = DtcPantallaUi(state, freezeFrame, estadoMil),
        acciones = AccionesDtc(
            onVolver = onNavigateBack,
            onLeer = { vm.readDtcCodes(connectionVm) },
            onBorrar = { vm.solicitarBorrado(connectionVm) },
            onOpenAiValue = onOpenAiValue,
        ),
    )
    confirmacion?.let { pendiente ->
        ConfirmarBorrarDtcDialog(
            confirmacion = pendiente,
            onConfirmar = { declaraDetenido -> vm.confirmarBorrado(connectionVm, declaraDetenido) },
            onCancelar = vm::cancelarBorrado,
        )
    }
}

@Composable
internal fun DtcContent(pantalla: DtcPantallaUi, acciones: AccionesDtc) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = "Códigos DTC", onVolver = acciones.onVolver)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "acciones") { AccionesLectura(pantalla, acciones) }
            contenidoSegunEstado(pantalla, acciones)
        }
    }
}

@Composable
private fun AccionesLectura(pantalla: DtcPantallaUi, acciones: AccionesDtc) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = acciones.onLeer,
            enabled = !pantalla.ocupado,
            colors = ButtonDefaults.buttonColors(
                containerColor = RevScopeColors.Accent,
                contentColor = RevScopeColors.Background,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("Leer DTCs", style = RevScopeType.label, textAlign = TextAlign.Center) }
        OutlinedButton(
            onClick = acciones.onBorrar,
            enabled = pantalla.puedeBorrar,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            val color = if (pantalla.puedeBorrar) RevScopeColors.Danger else RevScopeColors.TextMuted
            Text("Borrar DTCs", color = color, style = RevScopeType.label, textAlign = TextAlign.Center)
        }
    }
}

private fun LazyListScope.contenidoSegunEstado(pantalla: DtcPantallaUi, acciones: AccionesDtc) {
    when (val s = pantalla.estado) {
        DtcUiState.Idle -> item(key = "vacio") { SinLectura() }
        DtcUiState.Reading -> item(key = "cargando") { LoadingContent("Leyendo códigos de falla…") }
        DtcUiState.Clearing -> item(key = "cargando") { LoadingContent("Borrando códigos y releyendo la ECU…") }
        is DtcUiState.Borrado -> item(key = "borrado") { ResultadoBorrado(s.resultado) }
        is DtcUiState.Error -> item(key = "error") { ErrorState(s.message, onReintentar = acciones.onLeer) }
        is DtcUiState.HasCodes -> lecturaConCodigos(s.codes, pantalla, acciones.onOpenAiValue)
    }
}

private fun LazyListScope.lecturaConCodigos(
    codes: List<DtcCodeUi>,
    pantalla: DtcPantallaUi,
    onOpenAiValue: () -> Unit,
) {
    pantalla.estadoMil?.let { mil ->
        item(key = "mil") { Text(mil, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
    }
    if (codes.isEmpty()) {
        item(key = "sin_codigos") {
            StatusContent("Sin códigos activos, pendientes ni permanentes", NivelEstado.OK)
        }
        return
    }
    if (pantalla.freezeFrame.isNotEmpty()) item(key = "freeze_frame") { FreezeFrameCard(pantalla.freezeFrame) }
    items(codes, key = { "${it.dtc.mode}-${it.dtc.code}" }) { item -> DtcItem(item, onOpenAiValue) }
}

@Composable
private fun SinLectura() {
    EmptyState(
        mensaje = "Todavía no se han leído los códigos. Con el contacto puesto y el adaptador conectado, " +
            "toca «Leer DTCs».",
        icono = Icons.AutoMirrored.Filled.ManageSearch,
    )
}

@Composable
private fun LoadingContent(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
        Text(
            label,
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ResultadoBorrado(resultado: ResultadoBorradoUi) {
    val nivel = when {
        resultado.rechazadoPorEcu -> NivelEstado.ATENCION
        resultado.despues.isNotEmpty() -> NivelEstado.FALLA
        else -> NivelEstado.OK
    }
    StatusContent(textoResultadoBorrado(resultado), nivel)
}

@Composable
internal fun StatusContent(message: String, nivel: NivelEstado) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        NivelBadge(nivel)
        Text(message, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
    }
}

@Composable
private fun FreezeFrameCard(items: List<FreezeFrameItem>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = RevScopeColors.Accent, modifier = Modifier.size(18.dp))
            Text(
                "Freeze frame: el motor al momento de la falla",
                color = RevScopeColors.Accent,
                style = RevScopeType.label,
                modifier = Modifier.weight(1f),
            )
        }
        items.forEach { FilaEtiquetaValor(it.label, it.value) }
    }
}

@Composable
private fun DtcItem(item: DtcCodeUi, onOpenAiValue: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        EncabezadoDtc(item)
        ExplicacionDtc(item, onOpenAiValue)
    }
}

@Composable
private fun EncabezadoDtc(item: DtcCodeUi) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = RevScopeColors.Danger, modifier = Modifier.size(18.dp))
        Text(
            text = item.dtc.code,
            style = RevScopeType.title,
            color = RevScopeColors.Danger,
            modifier = Modifier.weight(1f),
        )
        Text(etiquetaModo(item.dtc.mode), style = RevScopeType.bodySmall, color = RevScopeColors.TextSecondary)
    }
}

@Composable
private fun ExplicacionDtc(item: DtcCodeUi, onOpenAiValue: () -> Unit) {
    when {
        item.isLoadingExplanation -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = RevScopeColors.Accent)
            Text("Consultando explicación…", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
        item.explanation != null -> Text(item.explanation, color = RevScopeColors.TextPrimary, style = RevScopeType.body)
        else -> Column {
            Text("Sin explicación disponible.", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            TextButton(onClick = onOpenAiValue, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Configurar IA", color = RevScopeColors.Accent, style = RevScopeType.label)
            }
        }
    }
}

private fun etiquetaModo(mode: DtcMode): String = when (mode) {
    DtcMode.Active -> "Activo"
    DtcMode.Pending -> "Pendiente"
    DtcMode.Permanent -> "Permanente"
}
