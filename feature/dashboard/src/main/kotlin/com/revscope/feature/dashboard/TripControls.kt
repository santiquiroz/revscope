package com.revscope.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType

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
    expandidoInicial: Boolean = false,
) {
    var expandido by remember { mutableStateOf(expandidoInicial) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TripStatePill(estado = estado, expandido = expandido, onClick = { expandido = !expandido })
        aviso?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                color = RevScopeColors.Accent,
                style = RevScopeType.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
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
private fun TripStatePill(estado: EstadoViaje, expandido: Boolean, onClick: () -> Unit) {
    val grabando = estado is EstadoViaje.Grabando
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = RevScopeColors.SurfaceHigh,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { stateDescription = if (expandido) "acciones desplegadas" else "acciones plegadas" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (grabando) "● Grabando viaje" else "● Conectado · sin viaje",
                color = if (grabando) RevScopeColors.Danger else RevScopeColors.Success,
                style = RevScopeType.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                if (expandido) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = RevScopeColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun IniciarViajeButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
    var confirmarFinalizacion by remember { mutableStateOf(false) }
    var confirmarDesconexion by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (estado is EstadoViaje.Grabando) {
            SecondaryButton("Finalizar viaje (seguir conectado)", RevScopeColors.Accent) { confirmarFinalizacion = true }
        }
        if (estado == EstadoViaje.EnlaceSinViaje) {
            SecondaryButton("Iniciar viaje", RevScopeColors.Accent, onIniciar)
        }
        SecondaryButton("Desconectar adaptador", RevScopeColors.Danger) {
            if (estado is EstadoViaje.Grabando) confirmarDesconexion = true else onDesconectar()
        }
    }
    if (confirmarFinalizacion) {
        ConfirmarAccionViajeDialog(
            titulo = "¿Finalizar el viaje?",
            texto = "Se guarda el viaje y el adaptador sigue conectado: puedes leer códigos de falla " +
                "o iniciar otro viaje sin reconectar.",
            confirmar = "Finalizar viaje",
            descartar = "Cancelar",
            onConfirm = {
                confirmarFinalizacion = false
                onFinalizar()
            },
            onDismiss = { confirmarFinalizacion = false },
        )
    }
    if (confirmarDesconexion) {
        ConfirmarAccionViajeDialog(
            titulo = "¿Desconectar el adaptador?",
            texto = "También se cierra el viaje en curso; queda guardado en el historial.",
            confirmar = "Desconectar",
            descartar = "Cancelar",
            onConfirm = {
                confirmarDesconexion = false
                onDesconectar()
            },
            onDismiss = { confirmarDesconexion = false },
        )
    }
}

@Composable
private fun SecondaryButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
    ) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ConfirmarAccionViajeDialog(
    titulo: String,
    texto: String,
    confirmar: String,
    descartar: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(descartar) } },
    )
}
