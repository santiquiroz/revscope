package com.revscope.feature.dtc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.EmptyState
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import com.revscope.feature.dtc.R

data class DtcPantallaUi(
    val estado: DtcUiState,
    val detalle: DetalleDtc = DetalleDtc(),
    val vehiculo: String? = null,
) {
    val ocupado: Boolean get() = estado is DtcUiState.Reading || estado is DtcUiState.Clearing
    val hayCodigos: Boolean get() = (estado as? DtcUiState.HasCodes)?.codes?.isNotEmpty() == true
    val puedeBorrar: Boolean get() = hayCodigos && detalle.bloqueoBorrado == null
}

data class AccionesDtc(
    val onVolver: () -> Unit,
    val onLeer: () -> Unit,
    val onBorrar: () -> Unit,
    val onOpenAiValue: () -> Unit,
    val onAlternarGuia: (String) -> Unit = {},
    val onMarcarPaso: (String, Boolean) -> Unit = { _, _ -> },
    val onExplicarIa: (String) -> Unit = {},
    val onGuardarEnSesion: () -> Unit = {},
    val onPrueba: ((TipoPrueba) -> Unit)? = null,
    val onMultimetro: ((SensorMultimetro) -> Unit)? = null,
)

data class DestinosDtc(
    val onPrueba: ((TipoPrueba) -> Unit)? = null,
    val onMultimetro: ((SensorMultimetro) -> Unit)? = null,
)

@Composable
fun DtcScreen(
    onNavigateBack: () -> Unit,
    connectionVm: ConnectionViewModel = hiltViewModel(),
    onOpenAiValue: () -> Unit = {},
    destinos: DestinosDtc = DestinosDtc(),
    vm: DtcViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val detalle by vm.detalle.collectAsState()
    val confirmacion by vm.confirmacionBorrado.collectAsState()
    val perfil by connectionVm.activeProfile.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.observarVelocidad(connectionVm) }
    LaunchedEffect(detalle.mensaje) {
        detalle.mensaje?.let {
            snackbar.showSnackbar(it)
            vm.mensajeMostrado()
        }
    }
    Box(Modifier.fillMaxSize()) {
        DtcCenterContent(
            pantalla = DtcPantallaUi(state, detalle, perfil?.name),
            acciones = accionesDe(vm, connectionVm, onNavigateBack, onOpenAiValue, destinos),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    confirmacion?.let { pendiente ->
        ConfirmarBorrarDtcDialog(
            confirmacion = pendiente,
            onConfirmar = { declaraDetenido -> vm.confirmarBorrado(connectionVm, declaraDetenido) },
            onCancelar = vm::cancelarBorrado,
        )
    }
}

private fun accionesDe(
    vm: DtcViewModel,
    connectionVm: ConnectionViewModel,
    onVolver: () -> Unit,
    onOpenAiValue: () -> Unit,
    destinos: DestinosDtc,
) = AccionesDtc(
    onVolver = onVolver,
    onLeer = { vm.readDtcCodes(connectionVm) },
    onBorrar = { vm.solicitarBorrado(connectionVm) },
    onOpenAiValue = onOpenAiValue,
    onAlternarGuia = vm::alternarGuia,
    onMarcarPaso = vm::marcarPaso,
    onExplicarIa = { codigo -> vm.explicarConIa(codigo, connectionVm) },
    onGuardarEnSesion = vm::guardarEnSesionNueva,
    onPrueba = destinos.onPrueba,
    onMultimetro = destinos.onMultimetro,
)

@Composable
internal fun DtcCenterContent(pantalla: DtcPantallaUi, acciones: AccionesDtc) {
    val context = LocalContext.current
    val resolver = remember(context) { AndroidStringResolver(context) }
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(
            titulo = stringResource(R.string.dtc_title),
            subtitulo = pantalla.vehiculo,
            onVolver = acciones.onVolver,
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "acciones") { AccionesLectura(pantalla, acciones) }
            contenidoSegunEstado(pantalla, acciones, resolver)
        }
    }
}

@Composable
private fun AccionesLectura(pantalla: DtcPantallaUi, acciones: AccionesDtc) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = acciones.onLeer,
                enabled = !pantalla.ocupado,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RevScopeColors.Accent,
                    contentColor = RevScopeColors.Background,
                ),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.dtc_read_codes), style = RevScopeType.label, textAlign = TextAlign.Center) }
            OutlinedButton(
                onClick = acciones.onBorrar,
                enabled = pantalla.puedeBorrar && !pantalla.ocupado,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                val color = if (pantalla.puedeBorrar) RevScopeColors.Danger else RevScopeColors.TextMuted
                Text(stringResource(R.string.dtc_clear_codes), color = color, style = RevScopeType.label, textAlign = TextAlign.Center)
            }
        }
        if (pantalla.hayCodigos) pantalla.detalle.bloqueoBorrado?.let { MotivoBorradoDeshabilitado(it) }
    }
}

private fun LazyListScope.contenidoSegunEstado(
    pantalla: DtcPantallaUi,
    acciones: AccionesDtc,
    resolver: AndroidStringResolver,
) {
    when (val s = pantalla.estado) {
        DtcUiState.Idle -> item(key = "vacio") { SinLectura() }
        DtcUiState.Reading -> item(key = "cargando") { LoadingContent(stringResource(R.string.dtc_reading_codes)) }
        DtcUiState.Clearing -> item(key = "cargando") { LoadingContent(stringResource(R.string.dtc_clearing_and_rereading)) }
        is DtcUiState.Borrado -> item(key = "borrado") { ResultadoBorrado(s.resultado, resolver) }
        is DtcUiState.Error -> item(key = "error") { ErrorState(s.message, onReintentar = acciones.onLeer) }
        is DtcUiState.HasCodes -> lecturaConCodigos(s.codes, pantalla, acciones, resolver)
    }
}

private fun LazyListScope.lecturaConCodigos(
    codes: List<DtcCodeUi>,
    pantalla: DtcPantallaUi,
    acciones: AccionesDtc,
    resolver: AndroidStringResolver,
) {
    val detalle = pantalla.detalle
    detalle.mil?.let { mil -> item(key = "mil") { TarjetaMil(mil) } }
    detalle.avisoLectura?.let { aviso -> item(key = "aviso_lectura") { StatusContent(aviso, NivelEstado.ATENCION) } }
    detalle.sinCodigos?.takeIf { codes.isEmpty() }?.let { texto ->
        item(key = "sin_codigos") { StatusContent(texto, NivelEstado.OK) }
    }
    items(codes, key = { it.codigo }) { item ->
        TarjetaCodigoDtc(
            EstadoTarjetaDtc(
                item = item,
                guiaAbierta = item.codigo in detalle.guiasAbiertas,
                pasosMarcados = detalle.pasosMarcados,
                borradoHabilitado = pantalla.puedeBorrar,
            ),
            acciones,
            resolver,
        )
    }
    detalle.freezeFrame?.let { ff ->
        item(key = "freeze_frame") { FreezeFrameCard(ff, enSesion = detalle.lecturaEnSesion) }
    }
    item(key = "sesion") { FranjaSesion(detalle, acciones.onGuardarEnSesion) }
}

@Composable
private fun SinLectura() {
    EmptyState(
        mensaje = stringResource(R.string.dtc_not_read_yet),
        icono = Icons.AutoMirrored.Filled.ManageSearch,
    )
}

@Composable
private fun ResultadoBorrado(resultado: ResultadoBorradoUi, resolver: AndroidStringResolver) {
    val nivel = when {
        resultado.rechazadoPorEcu -> NivelEstado.ATENCION
        resultado.despues.isNotEmpty() -> NivelEstado.FALLA
        else -> NivelEstado.OK
    }
    StatusContent(textoResultadoBorrado(resultado, resolver), nivel)
}
