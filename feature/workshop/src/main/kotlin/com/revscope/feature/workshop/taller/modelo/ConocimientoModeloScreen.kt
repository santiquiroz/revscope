package com.revscope.feature.workshop.taller.modelo

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ConocimientoModeloScreen(
    onVolver: () -> Unit,
    onReferencias: () -> Unit,
    viewModel: ConocimientoModeloViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val portapapeles = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensaje) {
        estado.mensaje?.let {
            snackbar.showSnackbar(it)
            viewModel.mensajeMostrado()
        }
    }
    Box(Modifier.fillMaxSize()) {
        ConocimientoModeloContent(
            estado,
            AccionesConocimientoModelo(
                onVolver = onVolver,
                onReferencias = onReferencias,
                onCopiar = { referencia ->
                    portapapeles.setText(AnnotatedString(referencia))
                    viewModel.referenciaCopiada(referencia)
                },
                onEditarCable = viewModel::editarCable,
                onCambiarColor = viewModel::cambiarColor,
                onGuardarColor = viewModel::guardarColor,
                onCerrarDialogo = viewModel::cerrarDialogo,
                onReintentar = viewModel::reintentar,
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}
