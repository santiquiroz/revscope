package com.revscope.feature.workshop.taller.multimetro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MultimetroScreen(onVolver: () -> Unit, viewModel: MultimetroViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }
    Box(Modifier.fillMaxSize()) {
        MultimetroContent(estado, accionesDe(viewModel, onVolver))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

private fun accionesDe(vm: MultimetroViewModel, onVolver: () -> Unit) = AccionesMultimetro(
    onVolver = onVolver,
    onSensor = vm::elegirSensor,
    onValor = vm::cambiarValor,
    onPedirColor = vm::pedirColor,
    onCambiarColor = vm::cambiarColor,
    onAceptarColor = vm::aceptarColor,
    onCerrarDialogo = vm::cerrarDialogo,
    onGuardarColores = vm::guardarColores,
    onGuardar = vm::guardarEnSesion,
    onReintentar = vm::reintentar,
)
