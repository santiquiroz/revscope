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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.telemetry.captura.CapturaRapida
import com.revscope.core.obd.taller.grafica.ResolutorVref
import com.revscope.core.obd.telemetry.captura.EstadisticasCaptura
import com.revscope.core.obd.telemetry.captura.EstadoCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.SeleccionPids
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.taller.pid.DisponibilidadPid
import com.revscope.core.obd.telemetry.captura.MuestreadorVoltaje
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
            stringResource(R.string.sensors_fast_capture_description),
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
        pidNoDisponible?.let { AvisoPidNoDisponible(it, vm::nombreDe) }
        mensaje?.let { AvisoDescartable(texto = it, onDescartar = vm::descartarMensaje, color = RevScopeColors.Warning) }
        if (activa != null) {
            activa.limiteHz?.let { AvisoLimite(it.toString()) }
            Medicion(stats, activa.inicio.pidsAceptados, vm::nombreDe)
            GraficaCaptura(vm)
        }
        resumen?.takeIf { activa == null }?.let { ResumenUltima(it, vm) }
    }
}

@Composable
internal fun AvisoPidNoDisponible(disponibilidad: DisponibilidadPid, nombreDe: (String) -> String) {
    val nombre = if (LocalConfiguration.current.locales[0].language == "es") {
        nombreDe(disponibilidad.pid).lowercase()
    } else {
        stringResource(R.string.sensors_this_parameter)
    }
    val motivo = when (disponibilidad.estado) {
        EstadoSoporte.NoSoportado -> stringResource(
            R.string.sensors_pid_not_reported,
            nombre,
            disponibilidad.pid,
        )
        EstadoSoporte.Desconocido -> stringResource(R.string.sensors_ecu_pids_unknown)
        EstadoSoporte.Soportado -> disponibilidad.motivo
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.sensors_pid_unavailable), color = RevScopeColors.Warning, style = RevScopeType.label)
        motivo?.let {
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
            pluralStringResource(
                R.plurals.sensors_pid_count,
                seleccion.size,
                seleccion.size,
                FastCaptureViewModel.MAX_PIDS,
            ),
            color = RevScopeColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(stringResource(R.string.sensors_pedal_throttle_preset), seleccion == SeleccionPids.PEDAL_Y_MARIPOSA, onElegirPedalYMariposa)
            candidatos.forEach { def ->
                val disponibilidadPid = disponibilidad(def.pid)
                val noDisponible = disponibilidadPid.estado == EstadoSoporte.NoSoportado
                Chip(
                    texto = stringResource(
                        if (noDisponible) R.string.sensors_pid_item_unavailable else R.string.sensors_pid_item,
                        def.pid,
                        def.nameEs,
                    ),
                    seleccionado = def.pid in seleccion,
                    onClick = { onAlternar(def.pid) },
                )
            }
        }
        Text(
            stringResource(R.string.sensors_pid_request_explanation),
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
        Text(
            stringResource(if (activa) R.string.sensors_stop_capture else R.string.sensors_start_fast_capture),
            fontWeight = FontWeight.Bold,
        )
    }
    if (!activa && !habilitado) {
        Text(stringResource(R.string.sensors_connect_adapter_to_capture), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@Composable
private fun AvisoLimite(texto: String) {
    Text(
        stringResource(R.string.sensors_capture_limit, texto),
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
    val locale = LocalConfiguration.current.locales[0]
    val faltante = stringResource(R.string.sensors_missing_value)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.sensors_measured_rate), color = RevScopeColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (stats == null) {
            Text(stringResource(R.string.sensors_measuring), color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            return@Column
        }
        pids.forEach { pid ->
            val hz = stats.hzPorPid[pid] ?: 0.0
            Text(
                stringResource(R.string.sensors_pid_rate, pid, nombreDe(pid), LocaleNumbers.decimal(hz, 1, locale)),
                color = RevScopeColors.TextPrimary,
                fontSize = 13.sp,
            )
        }
        Text(
            stringResource(
                R.string.sensors_request_latency,
                LocaleNumbers.decimal(stats.peticionesPorS, 1, locale),
                ms(stats.latenciaP50Ms, locale, faltante),
                ms(stats.latenciaP95Ms, locale, faltante),
            ),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        stats.limitadoPor?.let {
            Text(stringResource(R.string.sensors_limited_by, it), color = RevScopeColors.Warning, fontSize = 12.sp)
        }
    }
}

private fun ms(valor: Double?, locale: java.util.Locale, faltante: String): String =
    valor?.let { "${LocaleNumbers.decimal(it, 0, locale)} ms" } ?: faltante // i18n-ignore

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
            valorInicial = LocaleNumbers.decimal(ui.referencia.usada.voltios, 2, LocalConfiguration.current.locales[0]),
            leer = ResolutorVref::leer,
            onGuardar = vm::guardarVref,
            onCerrar = vm::cerrarVref,
            medida = ui.referencia.medida?.let { "${LocaleNumbers.decimal(it.voltios, 2, LocalConfiguration.current.locales[0])} V" },
            textoRestablecer = vm.textoRestablecerVref(ui.referencia),
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
        motivoVisible = vm::motivoVisible,
        onExportarLargo = { scope.launch { vm.exportarLargo(context) } },
        onExportarAncho = { scope.launch { vm.exportarAncho(context) } },
    )
}

@Composable
internal fun FastCaptureResumen(
    resumen: ResumenCaptura,
    nombreDe: (String) -> String,
    motivoVisible: (String) -> String,
    onExportarLargo: () -> Unit,
    onExportarAncho: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val faltante = stringResource(R.string.sensors_missing_value)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.sensors_last_capture), color = RevScopeColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(
                R.string.sensors_capture_summary,
                LocaleNumbers.decimal(resumen.duracionMs / 1_000.0, 1, locale),
                motivoVisible(resumen.motivoFin),
                ms(resumen.latenciaP50Ms, locale, faltante),
            ),
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
        )
        resumen.porPid.forEach { p ->
            Text(
                pluralStringResource(
                    R.plurals.sensors_pid_sample_summary,
                    p.n,
                    p.pid,
                    nombreDe(p.pid),
                    p.n,
                    LocaleNumbers.decimal(p.hz, 1, locale),
                    LocaleNumbers.decimal(p.min, 1, locale),
                    LocaleNumbers.decimal(p.max, 1, locale),
                ),
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.bodySmall.conCifrasTabulares(),
            )
        }
        // Dos botones de ancho completo: en una fila, el primero aplastaba al segundo.
        BotonCsv(stringResource(R.string.sensors_export_csv_samples), habilitado = resumen.rutaCsv != null, onClick = onExportarLargo)
        BotonCsv(stringResource(R.string.sensors_export_csv_batches), habilitado = true, onClick = onExportarAncho)
    }

}

internal fun motivoVisible(motivo: String, resolver: StringResolver): String {
    val motivoEnlace = CapturaRapida.MOTIVO_ENLACE
    return when {
        motivo == CapturaRapida.MOTIVO_USUARIO -> resolver.get(R.string.sensors_capture_reason_user)
        motivo == MuestreadorVoltaje.MOTIVO_DURACION -> resolver.get(R.string.sensors_capture_reason_duration)
        motivo == MuestreadorVoltaje.MOTIVO_SIN_RESPUESTA -> resolver.get(R.string.sensors_capture_reason_no_response)
        motivo == motivoEnlace -> resolver.get(R.string.sensors_capture_reason_link_lost)
        motivo.startsWith("$motivoEnlace: ") ->
            resolver.get(R.string.sensors_capture_reason_link_lost) + motivo.removePrefix(motivoEnlace)
        else -> motivo
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
