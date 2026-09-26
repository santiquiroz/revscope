package com.revscope.feature.workshop.taller

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import androidx.compose.runtime.collectAsState

data class DestinosSesion(
    val onVolver: () -> Unit,
    val onLeerCodigos: () -> Unit,
    val onCaptura: () -> Unit,
    val onChequeo: () -> Unit,
    val onPruebaGuiada: (() -> Unit)? = null,
    val onMultimetro: (() -> Unit)? = null,
    val onGenerarInforme: (() -> Unit)? = null,
)

@Composable
fun TallerHubScreen(
    acciones: AccionesHub,
    selectorVehiculo: @Composable () -> Unit = {},
    viewModel: TallerHubViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    TallerHubContent(estado, acciones.copy(onVerTodas = viewModel::alternarVerTodas), selectorVehiculo)
}

@Composable
fun NuevaSesionScreen(
    onVolver: () -> Unit,
    onSesionAbierta: (Long) -> Unit,
    viewModel: NuevaSesionViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    LaunchedEffect(viewModel) { viewModel.sesionAbierta.collect(onSesionAbierta) }
    NuevaSesionContent(
        estado,
        AccionesNuevaSesion(
            onVolver = onVolver,
            onSintoma = viewModel::alternarSintoma,
            onSintomasTexto = viewModel::cambiarSintomasTexto,
            onOdometro = viewModel::cambiarOdometro,
            onNotas = viewModel::cambiarNotas,
            onBase = viewModel::elegirBase,
            onAbrir = viewModel::abrir,
            onConfirmarCierre = viewModel::confirmarCierreYAbrir,
            onCancelarCierre = viewModel::cancelarCierre,
        ),
    )
}

@Composable
fun SesionTallerScreen(destinos: DestinosSesion, viewModel: SesionTallerViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Una sola salida: tras eliminar llegan la señal y el estado «eliminada», y dos popBackStack sacarían también el hub.
    LaunchedEffect(viewModel) {
        merge(viewModel.salir, viewModel.estado.filter { it.eliminada }.map { }).first()
        destinos.onVolver()
    }
    LaunchedEffect(estado.ui.mensaje) {
        estado.ui.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }
    Box(Modifier.fillMaxSize()) {
        SesionTallerContent(estado, accionesSesion(viewModel, destinos))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

private fun accionesSesion(vm: SesionTallerViewModel, destinos: DestinosSesion) = AccionesSesion(
    onVolver = destinos.onVolver,
    onMenu = vm::mostrarMenu,
    onGenerarInforme = destinos.onGenerarInforme,
    onPedirCierre = vm::pedirCierre,
    onPedirEliminacion = vm::pedirEliminacion,
    onAlternarComparacion = vm::alternarComparacion,
    onAlternarEvento = vm::alternarEvento,
    onHojaAgregar = vm::mostrarHojaAgregar,
    onLeerCodigos = alCerrarHoja(vm, destinos.onLeerCodigos),
    onCaptura = alCerrarHoja(vm, destinos.onCaptura),
    onChequeo = alCerrarHoja(vm, destinos.onChequeo),
    onInstantanea = vm::tomarInstantanea,
    onPedirNota = vm::pedirNota,
    onPruebaGuiada = destinos.onPruebaGuiada?.let { alCerrarHoja(vm, it) },
    onMultimetro = destinos.onMultimetro?.let { alCerrarHoja(vm, it) },
    onCambiarNota = vm::cambiarNota,
    onGuardarNota = vm::guardarNota,
    onConfirmarCierre = vm::confirmarCierre,
    onConfirmarEliminacion = vm::confirmarEliminacion,
    onCancelarDialogo = vm::cancelarDialogo,
)

private fun alCerrarHoja(vm: SesionTallerViewModel, destino: () -> Unit): () -> Unit = {
    vm.mostrarHojaAgregar(false)
    destino()
}
