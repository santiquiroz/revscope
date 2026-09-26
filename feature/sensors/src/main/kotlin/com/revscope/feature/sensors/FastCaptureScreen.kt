package com.revscope.feature.sensors

import com.revscope.core.designsystem.RevScopeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.revscope.core.designsystem.AvisoDescartable
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.telemetry.captura.EstadisticasCaptura
import com.revscope.core.obd.telemetry.captura.EstadoCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.SeleccionPids
import kotlinx.coroutines.launch


@Composable
fun FastCaptureContent(vm: FastCaptureViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsState()
    val conectado by vm.conectado.collectAsState()
    val seleccion by vm.seleccion.collectAsState()
    val stats by vm.estadisticas.collectAsState()
    val resumen by vm.ultimoResumen.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val activa = estado as? EstadoCaptura.Activa

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
        if (activa == null) SelectorPids(vm, seleccion)
        BotonCaptura(activa != null, conectado && seleccion.isNotEmpty(), vm::iniciar, vm::detener)
        mensaje?.let { AvisoDescartable(texto = it, onDescartar = vm::descartarMensaje, color = RevScopeColors.Warning) }
        if (activa != null) {
            activa.limiteHz?.let { AvisoLimite("Limitada a $it Hz para cuidar batería y temperatura del teléfono") }
            Medicion(stats, activa.inicio.pidsAceptados, vm::nombreDe)
            GraficaCaptura(vm, activa.inicio.pidsAceptados)
        }
        resumen?.takeIf { activa == null }?.let { ResumenUltima(it, vm) }
    }
}

@Composable
private fun SelectorPids(vm: FastCaptureViewModel, seleccion: List<String>) {
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
            Chip("Pedal y mariposa (49, 4A, 11)", seleccion == SeleccionPids.PEDAL_Y_MARIPOSA, vm::elegirPedalYMariposa)
            vm.candidatos().forEach { def ->
                Chip("${def.pid} ${def.nameEs}", def.pid in seleccion) { vm.alternar(def.pid) }
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
private fun GraficaCaptura(vm: FastCaptureViewModel, pids: List<String>) {
    val series by vm.series.collectAsState()
    val modelProducer = remember(pids) { CartesianChartModelProducer() }
    val conDatos = series.filterValues { it.size >= 2 }

    LaunchedEffect(conDatos) {
        if (conDatos.isEmpty()) return@LaunchedEffect
        val origen = conDatos.values.minOf { it.first().first }
        modelProducer.runTransaction {
            lineSeries {
                conDatos.values.forEach { puntos ->
                    series(x = puntos.map { (it.first - origen) / 1_000.0 }, y = puntos.map { it.second })
                }
            }
        }
    }

    Text(
        "Últimos 10 s · ${conDatos.keys.joinToString { "$it ${vm.nombreDe(it)}" }}",
        color = RevScopeColors.TextSecondary,
        style = RevScopeType.bodySmall,
    )
    if (conDatos.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text("Esperando muestras…", color = RevScopeColors.TextSecondary, fontSize = 13.sp)
        }
        return
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(title = "s"),
        ),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(240.dp),
    )
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
