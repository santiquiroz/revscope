package com.revscope.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.designsystem.RevScopeColors

/**
 * Pastilla de estado del viaje OBD bajo el encabezado de Conducir. Tocarla despliega las
 * acciones; finalizar pide confirmación para que un toque accidental manejando no corte el viaje.
 */
@Composable
internal fun ObdTripControls(
    estado: EstadoViaje,
    aviso: String?,
    onFinalizar: () -> Unit,
    onIniciar: () -> Unit,
    onDesconectar: () -> Unit,
) {
    var expandido by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TripStatePill(estado = estado, onClick = { expandido = !expandido })
        aviso?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = RevScopeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (estado == EstadoViaje.EnlaceSinViaje) {
            Spacer(Modifier.height(8.dp))
            IniciarViajeButton(onClick = onIniciar)
        }
        if (expandido) {
            Spacer(Modifier.height(8.dp))
            TripActions(estado = estado, onFinalizar = onFinalizar, onIniciar = onIniciar, onDesconectar = onDesconectar)
        }
    }
}

@Composable
private fun TripStatePill(estado: EstadoViaje, onClick: () -> Unit) {
    val grabando = estado is EstadoViaje.Grabando
    Text(
        text = if (grabando) "● Grabando viaje" else "● Conectado · sin viaje",
        color = if (grabando) RevScopeColors.Danger else RevScopeColors.Success,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun IniciarViajeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
    ) {
        Text("Iniciar viaje", color = RevScopeColors.Background, fontWeight = FontWeight.Bold)
    }
}

/** Acciones de viaje y enlace; también las usa el escáner de adaptador. */
@Composable
internal fun TripActions(
    estado: EstadoViaje,
    onFinalizar: () -> Unit,
    onIniciar: () -> Unit,
    onDesconectar: () -> Unit,
) {
    var confirmar by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (estado is EstadoViaje.Grabando) {
            SecondaryButton("Finalizar viaje (seguir conectado)", RevScopeColors.Accent) { confirmar = true }
        }
        if (estado == EstadoViaje.EnlaceSinViaje) {
            SecondaryButton("Iniciar viaje", RevScopeColors.Accent, onIniciar)
        }
        SecondaryButton("Desconectar adaptador", RevScopeColors.Danger, onDesconectar)
    }
    if (confirmar) {
        ConfirmarFinDeViajeDialog(
            onConfirm = {
                confirmar = false
                onFinalizar()
            },
            onDismiss = { confirmar = false },
        )
    }
}

@Composable
private fun SecondaryButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
    ) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConfirmarFinDeViajeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Finalizar el viaje?") },
        text = {
            Text(
                "Se guarda el viaje y el adaptador sigue conectado: puedes leer códigos de falla " +
                    "o iniciar otro viaje sin reconectar.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Finalizar viaje") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
