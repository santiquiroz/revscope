package com.revscope.feature.workshop.taller.prueba

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PruebaGuiadaScreen(
    onVolver: () -> Unit,
    onVerGuia: ((String) -> Unit)? = null,
    viewModel: PruebaGuiadaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.salir.collect { onVolver() } }
    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }
    BackHandler(onBack = viewModel::atras)
    PantallaEncendida(estado.enCurso)
    Box(Modifier.fillMaxSize()) {
        PruebaGuiadaContent(estado, accionesDe(viewModel, onVerGuia))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

// Con las manos en el acelerador nadie toca la pantalla: no debe apagarse a mitad de un paso.
@Composable
private fun PantallaEncendida(activa: Boolean) {
    val vista = LocalView.current
    DisposableEffect(activa) {
        vista.keepScreenOn = activa
        onDispose { vista.keepScreenOn = false }
    }
}

private fun accionesDe(vm: PruebaGuiadaViewModel, onVerGuia: ((String) -> Unit)?) = AccionesPrueba(
    onVolver = vm::volver,
    onElegir = vm::elegir,
    onEmpezar = vm::empezar,
    onAvanzar = vm::avanzar,
    onRepetirPaso = vm::repetirPaso,
    onVoz = vm::cambiarVoz,
    onPedirCancelar = vm::pedirCancelar,
    onConfirmarCancelar = vm::confirmarCancelar,
    onCerrarDialogo = vm::cerrarDialogo,
    onPedirVref = vm::pedirVref,
    onGuardarVref = vm::guardarVref,
    onReintentar = vm::reintentar,
    onTerminar = vm::terminar,
    onGuardarEnSesion = vm::guardarEnSesion,
    onVerGuia = onVerGuia,
)
