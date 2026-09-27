package com.revscope.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun CapacidadesEcuCard(
    protocolo: String,
    pids: List<String>,
    pidsPorRango: Map<String, List<String>> = agruparPidsPorRango(pids),
    tasaEsperada: String,
    explicacion: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val contenido: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = RevScopeColors.Accent)
                Text(
                    "Capacidades de la ECU",
                    color = RevScopeColors.TextPrimary,
                    style = RevScopeType.title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            DatoCapacidad("Protocolo", protocolo)
            DatoCapacidad("PIDs anunciados", "${pids.size}", cifrasTabulares = true)
            ListaPidsPorRango(pidsPorRango)
            DatoCapacidad("Tasa esperada", tasaEsperada)
            Text(explicacion, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
    }
    if (onClick == null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = RevScopeColors.Surface,
            modifier = modifier.fillMaxWidth(),
            content = contenido,
        )
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = RevScopeColors.Surface,
            modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
            content = contenido,
        )
    }
}

@Composable
private fun ListaPidsPorRango(rangos: Map<String, List<String>>) {
    val anunciados = rangos.filterValues { it.isNotEmpty() }
    if (anunciados.isEmpty()) {
        Text(
            "La ECU todavía no ha anunciado su mapa de PIDs.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        return
    }
    anunciados.forEach { (rango, pids) ->
        Text(
            "$rango · ${pids.joinToString()}",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall.conCifrasTabulares(),
        )
    }
}

@Composable
private fun DatoCapacidad(etiqueta: String, valor: String, cifrasTabulares: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(etiqueta, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        Text(
            valor,
            color = RevScopeColors.TextPrimary,
            style = if (cifrasTabulares) RevScopeType.label.conCifrasTabulares() else RevScopeType.label,
        )
    }
}

private fun agruparPidsPorRango(pids: List<String>): Map<String, List<String>> = linkedMapOf(
    "01 00" to pids.enRango(0x01..0x20),
    "01 20" to pids.enRango(0x21..0x40),
    "01 40" to pids.enRango(0x41..0x60),
    "01 60" to pids.enRango(0x61..0x80),
)

private fun List<String>.enRango(rango: IntRange): List<String> = filter { pid ->
    pid.toIntOrNull(16)?.let(rango::contains) == true
}
