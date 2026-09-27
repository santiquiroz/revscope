package com.revscope.feature.sensors

import com.revscope.core.designsystem.RevScopeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.designsystem.AvisoDescartable
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.DialogoReferenciaVoltaje
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.grafica.FormatoPosicion
import com.revscope.core.obd.taller.grafica.ResolutorVref
import com.revscope.core.obd.telemetry.captura.EstadisticasCaptura
import com.revscope.core.obd.telemetry.captura.EstadoCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.SeleccionPids
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.taller.pid.DisponibilidadPid
import kotlinx.coroutines.launch


@Composable
fun FastCaptureContent(vm: FastCaptureViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsState()
    val conectado by vm.conectado.collectAsState()
    val seleccion by vm.seleccion.collectAsState()
    val stats by vm.estadisticas.collectAsState()
    val resumen by vm.ultimoResumen.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val pidNoDisponible by vm.pidNoDisponible.collectAsState()
    val capacidades by vm.capacidadesEcu.collectAsState()
    val activa = estado as? EstadoCaptura.Activa
    val candidatos = remember(capacidades) { vm.candidatos() }
    val disponibilidad = remember(capacidades) { vm::disponibilidad }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Lee solo los PIDs elegidos lo más rápido que permita el adaptador; los demás gauges quedan en pausa. " +
                "Para un pedal: contacto puesto, motor apagado o en ralentí, y barre el pedal despacio.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        if (activa == null) {
            SelectorPids(
                seleccion = seleccion,
                candidatos = candidatos,
                disponibilidad = disponibilidad,
                onElegirPedalYMariposa = vm::elegirPedalYMariposa,
                onAlternar = vm::alternar,
            )
        }
        BotonCaptura(activa != null, conectado && seleccion.isNotEmpty(), vm::iniciar, vm::detener)
        pidNoDisponible?.let { AvisoPidNoDisponible(it) }
        mensaje?.let { AvisoDescartable(texto = it, onDescartar = vm::descartarMensaje, color = RevScopeColors.Warning) }
        if (activa != null) {
            activa.limiteHz?.let { AvisoLimite("Limitada a $it Hz para cuidar batería y temperatura del teléfono") }
            Medicion(stats, activa.inicio.pidsAceptados, vm::nombreDe)
            GraficaCaptura(vm)
        }
        resumen?.takeIf { activa == null }?.let { ResumenUltima(it, vm) }
    }
}

@Composable
internal fun AvisoPidNoDisponible(disponibilidad: DisponibilidadPid) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("No disponible en esta ECU", color = RevScopeColors.Warning, style = RevScopeType.label)
        disponibilidad.motivo?.let {
            Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
        }
    }
}

@Composable
internal fun SelectorPids(
    seleccion: List<String>,
    candidatos: List<PidDefinition>,
    disponibilidad: (String) -> DisponibilidadPid,
    onElegirPedalYMariposa: () -> Unit,
    onAlternar: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "PIDs (${seleccion.size}/${FastCaptureViewModel.MAX_PIDS})",
            color = RevScopeColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("Pedal y mariposa (49, 4A, 11)", seleccion == SeleccionPids.PEDAL_Y_MARIPOSA, onElegirPedalYMariposa)
            candidatos.forEach { def ->
                val disponibilidadPid = disponibilidad(def.pid)
                val noDisponible = disponibilidadPid.estado == EstadoSoporte.NoSoportado
                Chip(
                    texto = "${def.pid} ${def.nameEs}" + if (noDisponible) " · no disponible" else "",
                    seleccionado = def.pid in seleccion,
                    onClick = { onAlternar(def.pid) },
                )
            }
        }
        Text(
            "Cada petición lleva hasta 3 PIDs de 1 byte (o menos si alguno es de 2 bytes, como RPM): " +
                "más PIDs = menos Hz por PID.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
    }
}

@Composable
private fun Chip(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    ChipSeleccion(texto = texto, seleccionado = seleccionado, onClick = onClick)
}

@Composable
private fun BotonCaptura(activa: Boolean, habilitado: Boolean, onIniciar: () -> Unit, onDetener: () -> Unit) {
    Button(
        onClick = if (activa) onDetener else onIniciar,
        enabled = activa || habilitado,
        colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent, contentColor = RevScopeColors.Background),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (activa) "Detener captura" else "Iniciar captura rápida", fontWeight = FontWeight.Bold)
    }
    if (!activa && !habilitado) {
        Text("Conecta el adaptador para capturar.", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@Composable
private fun AvisoLimite(texto: String) {
    Text(
        texto,
        color = RevScopeColors.Warning,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}

@Composable
private fun Medicion(stats: EstadisticasCaptura?, pids: List<String>, nombreDe: (String) -> String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Tasa medida", color = RevScopeColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (stats == null) {
            Text("Midiendo…", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            return@Column
        }
        pids.forEach { pid ->
            val hz = stats.hzPorPid[pid] ?: 0.0
            Text("$pid ${nombreDe(pid)}: ${"%.1f".format(hz)} Hz", color = RevScopeColors.TextPrimary, fontSize = 13.sp)
        }
        Text(
            "${"%.1f".format(stats.peticionesPorS)} peticiones/s · latencia p50 ${ms(stats.latenciaP50Ms)} · " +
                "p95 ${ms(stats.latenciaP95Ms)}",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        stats.limitadoPor?.let { Text("Limitado por: $it", color = RevScopeColors.Warning, fontSize = 12.sp) }
    }
}

private fun ms(valor: Double?): String = valor?.let { "${"%.0f".format(it)} ms" } ?: "—"

@Composable
private fun GraficaCaptura(vm: FastCaptureViewModel) {
    val ui by vm.grafica.collectAsState()
    val dialogo by vm.dialogoVref.collectAsState()
    GraficaCapturaContent(
        ui = ui,
        acciones = AccionesGrafica(
            onUnidad = vm::elegirUnidad,
            onVentana = vm::elegirVentana,
            onPausa = vm::pausar,
            onCambiarVref = vm::pedirVref,
        ),
    )
    if (dialogo) {
        DialogoReferenciaVoltaje(
            valorInicial = FormatoTaller.numero(ui.referencia.usada.voltios, 2),
            leer = ResolutorVref::leer,
            onGuardar = vm::guardarVref,
            onCerrar = vm::cerrarVref,
            medida = ui.referencia.medida?.let { FormatoPosicion.voltios(it.voltios) },
            textoRestablecer = ResolutorVref.textoRestablecer(ui.referencia),
        )
    }
}

@Composable
private fun ResumenUltima(resumen: ResumenCaptura, vm: FastCaptureViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FastCaptureResumen(
        resumen = resumen,
        nombreDe = vm::nombreDe,
        onExportarLargo = { scope.launch { vm.exportarLargo(context) } },
        onExportarAncho = { scope.launch { vm.exportarAncho(context) } },
    )
}

@Composable
internal fun FastCaptureResumen(
    resumen: ResumenCaptura,
    nombreDe: (String) -> String,
    onExportarLargo: () -> Unit,
    onExportarAncho: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Última captura", color = RevScopeColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "${"%.1f".format(resumen.duracionMs / 1_000.0)} s · ${resumen.motivoFin} · latencia p50 ${ms(resumen.latenciaP50Ms)}",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        resumen.porPid.forEach { p ->
            Text(
                "${p.pid} ${nombreDe(p.pid)}: ${p.n} muestras · ${"%.1f".format(p.hz)} Hz · " +
                    "${"%.1f".format(p.min)}–${"%.1f".format(p.max)}",
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.bodySmall.conCifrasTabulares(),
            )
        }
        // Dos botones de ancho completo: en una fila, el primero aplastaba al segundo.
        BotonCsv("CSV (una fila por muestra)", habilitado = resumen.rutaCsv != null, onClick = onExportarLargo)
        BotonCsv("CSV ancho (una fila por lote)", habilitado = true, onClick = onExportarAncho)
    }
}

@Composable
private fun BotonCsv(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(texto, color = if (habilitado) RevScopeColors.Accent else RevScopeColors.TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
