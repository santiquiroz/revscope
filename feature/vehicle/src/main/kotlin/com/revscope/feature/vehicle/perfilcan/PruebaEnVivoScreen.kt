package com.revscope.feature.vehicle.perfilcan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.feature.vehicle.R

@Composable
fun PruebaEnVivoScreen(
    vehiculoId: Long,
    clave: String,
    onVolver: () -> Unit,
    vm: PerfilCanViewModel = hiltViewModel(),
) {
    LaunchedEffect(vehiculoId, clave) { vm.abrirPrueba(vehiculoId, clave) }
    val prueba by vm.prueba.collectAsState()
    LaunchedEffect(prueba?.coincide) { if (prueba?.coincide != null) onVolver() }
    Column(modifier = Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = prueba?.nombre ?: stringResource(R.string.vehicle_can_live_test_title), onVolver = onVolver)
        prueba?.let { PruebaEnVivoContenido(it, onResponder = vm::responderPrueba) }
    }
}

@Composable
fun PruebaEnVivoContenido(prueba: PruebaEnVivoUi, onResponder: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TarjetaEnVivo(prueba)
        prueba.motivoSinPrueba?.let { Text(it, color = RevScopeColors.Warning, fontSize = 14.sp) }
        Text(
            stringResource(R.string.vehicle_can_test_question),
            color = RevScopeColors.TextPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        BotonesRespuesta(prueba, onResponder)
        Text(
            prueba.detalle,
            color = RevScopeColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TarjetaEnVivo(prueba: PruebaEnVivoUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.vehicle_can_live_label, prueba.instruccion.uppercase()),
            color = RevScopeColors.TextSecondary,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        ValorGrande(prueba.valor, prueba.disponible)
    }
}

@Composable
private fun ValorGrande(valor: String?, disponible: Boolean) {
    val conValor = valor != null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .background(
                if (conValor) RevScopeColors.Accent else RevScopeColors.SurfaceHigh,
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = valor ?: if (disponible) stringResource(R.string.vehicle_can_waiting) else "—",
            color = if (conValor) RevScopeColors.Background else RevScopeColors.TextMuted,
            fontSize = if (conValor) 56.sp else 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BotonesRespuesta(prueba: PruebaEnVivoUi, onResponder: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { onResponder(false) },
            enabled = prueba.disponible,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.vehicle_can_test_no), color = RevScopeColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = { onResponder(true) },
            enabled = prueba.disponible && prueba.valor != null,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
        ) {
            Text(stringResource(R.string.vehicle_can_test_yes), color = RevScopeColors.Background, fontWeight = FontWeight.Bold)
        }
    }
}
