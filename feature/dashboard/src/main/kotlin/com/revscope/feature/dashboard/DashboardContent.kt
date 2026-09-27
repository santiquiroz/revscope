package com.revscope.feature.dashboard

import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.TextoAjustable
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.intelligence.efficiency.TripScore
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.feature.dashboard.gauges.BoostBar
import com.revscope.feature.dashboard.gauges.GearDisplay
import com.revscope.feature.dashboard.gauges.RpmGauge
import com.revscope.feature.dashboard.gauges.SpeedGauge
import com.revscope.feature.dashboard.gauges.TempGauge

internal data class LecturasConducir(
    val rpm: Float? = null,
    val velocidad: Float? = null,
    val temperatura: Float? = null,
    val boost: Float? = null,
    val marcha: Int? = null,
    val motivoRpm: String? = null,
    val motivoVelocidad: String? = null,
    val motivoTemperatura: String? = null,
    val motivoBoost: String? = null,
    val motivoMarcha: String? = null,
)

internal data class EscalasConducir(
    val maxRpm: Int = 8000,
    val redlineRpm: Int = 6500,
    val maxVelocidad: Int = 260,
    val marchas: Int = 6,
    val marchasCalibradas: Boolean = false,
)

internal data class EstadoConducirUi(
    val versionNueva: String? = null,
    val avisoAlDia: String? = null,
    val alertaActiva: String? = null,
    val ofrecerViajeGps: Boolean = false,
    val viajeGpsActivo: Boolean = false,
    val modoGpsHero: Boolean = false,
    val conectado: Boolean = false,
    val estadoViaje: EstadoViaje = EstadoViaje.SinEnlace,
    val avisoViaje: String? = null,
    val velocidadPorGps: Boolean = false,
    val lecturas: LecturasConducir = LecturasConducir(),
    val escalas: EscalasConducir = EscalasConducir(),
    val puntaje: TripScore = TripScore.empty(),
) {
    val gaugesAtenuados: Boolean get() = viajeGpsActivo || modoGpsHero
    val avisarSinAdaptador: Boolean get() = viajeGpsActivo && !modoGpsHero
}

internal data class AccionesConducir(
    val onDescargarActualizacion: () -> Unit = {},
    val onDescartarActualizacion: () -> Unit = {},
    val onAbrirAlDia: () -> Unit = {},
    val onIniciarViajeGps: () -> Unit = {},
    val onFinalizarViajeGps: () -> Unit = {},
    val onAbrirMapa: () -> Unit = {},
    val onConfigurarAdaptador: () -> Unit = {},
    val onFinalizarViaje: () -> Unit = {},
    val onIniciarViaje: () -> Unit = {},
    val onDesconectar: () -> Unit = {},
    val onAlternarFuenteVelocidad: () -> Unit = {},
)

@Composable
internal fun DashboardContent(
    estado: EstadoConducirUi,
    acciones: AccionesConducir,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvisosConducir(estado, acciones)
        AccionesDeViaje(estado, acciones)
        if (estado.modoGpsHero) {
            ConfigureAdapterCta(onClick = acciones.onConfigurarAdaptador)
        }
        DashboardGaugesContent(estado, acciones.onAlternarFuenteVelocidad)
        TripScoreBar(tripScore = estado.puntaje)
    }
}

@Composable
private fun AvisosConducir(estado: EstadoConducirUi, acciones: AccionesConducir) {
    estado.versionNueva?.let { version ->
        UpdateBanner(
            version = version,
            onDownload = acciones.onDescargarActualizacion,
            onDismiss = acciones.onDescartarActualizacion,
        )
    }
    estado.avisoAlDia?.let { mensaje -> BannerPeligro(mensaje, onClick = acciones.onAbrirAlDia) }
    estado.alertaActiva?.let { mensaje -> BannerPeligro(mensaje) }
}

// Texto Background sobre Danger (5,4:1): el TextPrimary de antes daba 3,2:1.
@Composable
internal fun BannerPeligro(mensaje: String, onClick: (() -> Unit)? = null) {
    val accion = onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(RevScopeColors.Danger, RoundedCornerShape(8.dp))
            .then(accion)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = RevScopeColors.Background)
        Text(
            mensaje,
            color = RevScopeColors.Background,
            style = RevScopeType.label,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = RevScopeColors.Background)
        }
    }
}

@Composable
private fun AccionesDeViaje(estado: EstadoConducirUi, acciones: AccionesConducir) {
    if (estado.ofrecerViajeGps) {
        GpsTripButton(
            isActive = estado.viajeGpsActivo,
            onStart = acciones.onIniciarViajeGps,
            onStop = acciones.onFinalizarViajeGps,
        )
        if (estado.modoGpsHero) OpenMapButton(onClick = acciones.onAbrirMapa)
    }
    if (estado.conectado) {
        ObdTripControls(
            estado = estado.estadoViaje,
            aviso = estado.avisoViaje,
            onFinalizar = acciones.onFinalizarViaje,
            onIniciar = acciones.onIniciarViaje,
            onDesconectar = acciones.onDesconectar,
        )
    }
}

@Composable
internal fun DashboardGaugesContent(estado: EstadoConducirUi, onAlternarFuenteVelocidad: () -> Unit = {}) {
    val atenuado = atenuadoSi(estado.gaugesAtenuados)
    val lecturas = estado.lecturas
    val escalas = estado.escalas
    val apilarGauges = LocalDensity.current.fontScale >= 1.8f
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RpmGauge(
            rpm = lecturas.rpm,
            maxRpm = escalas.maxRpm,
            redlineRpm = escalas.redlineRpm,
            modifier = Modifier.padding(vertical = 8.dp).then(atenuado),
        )
        MotivoGauge(lecturas.motivoRpm)
        if (estado.avisarSinAdaptador) {
            Text(
                "RPM, temperatura, marcha y boost necesitan un adaptador OBD2",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        if (apilarGauges) {
            PanelVelocidad(estado, onAlternarFuenteVelocidad, Modifier.fillMaxWidth())
            PanelMarcha(estado, atenuado, Modifier.fillMaxWidth())
            PanelTemperatura(estado, atenuado, Modifier.fillMaxWidth())
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelVelocidad(estado, onAlternarFuenteVelocidad, Modifier.weight(1f))
                PanelMarcha(estado, atenuado, Modifier.weight(0.6f))
                PanelTemperatura(estado, atenuado, Modifier.weight(0.6f))
            }
        }
        BoostBar(
            boostKpa = lecturas.boost,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).then(atenuado),
        )
        MotivoGauge(lecturas.motivoBoost)
    }
}

@Composable
private fun PanelVelocidad(
    estado: EstadoConducirUi,
    onAlternarFuenteVelocidad: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SpeedGauge(speed = estado.lecturas.velocidad, maxSpeed = estado.escalas.maxVelocidad)
        MotivoGauge(estado.lecturas.motivoVelocidad)
        if (estado.conectado) {
            SpeedSourceChip(useGps = estado.velocidadPorGps, onToggle = onAlternarFuenteVelocidad)
        }
    }
}

@Composable
private fun PanelMarcha(estado: EstadoConducirUi, atenuado: Modifier, modifier: Modifier) {
    Column(modifier = modifier.then(atenuado), horizontalAlignment = Alignment.CenterHorizontally) {
        GearDisplay(
            gear = estado.lecturas.marcha,
            isCalibrated = estado.escalas.marchasCalibradas,
            gearCount = estado.escalas.marchas,
        )
        MotivoGauge(estado.lecturas.motivoMarcha)
    }
}

@Composable
private fun PanelTemperatura(estado: EstadoConducirUi, atenuado: Modifier, modifier: Modifier) {
    Column(modifier = modifier.then(atenuado), horizontalAlignment = Alignment.CenterHorizontally) {
        TempGauge(tempCelsius = estado.lecturas.temperatura)
        MotivoGauge(estado.lecturas.motivoTemperatura)
    }
}

@Composable
private fun MotivoGauge(motivo: String?) {
    motivo?.let {
        Text(
            text = it,
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

private const val DIMMED_GAUGE_ALPHA = 0.35f

private fun atenuadoSi(atenuado: Boolean): Modifier =
    if (atenuado) Modifier.alpha(DIMMED_GAUGE_ALPHA) else Modifier

@Composable
private fun GpsTripButton(isActive: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    var confirmarFin by remember { mutableStateOf(false) }
    Button(
        onClick = { if (isActive) confirmarFin = true else onStart() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) RevScopeColors.Danger else RevScopeColors.Accent,
        ),
    ) {
        Text(
            text = if (isActive) "Finalizar viaje GPS" else "Iniciar viaje GPS",
            color = RevScopeColors.Background,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
    if (confirmarFin) {
        ConfirmarAccionViajeDialog(
            titulo = "¿Finalizar el viaje?",
            texto = "El viaje GPS se guarda en el historial.",
            confirmar = "Finalizar viaje",
            descartar = "Seguir grabando",
            onConfirm = {
                confirmarFin = false
                onStop()
            },
            onDismiss = { confirmarFin = false },
        )
    }
}

@Composable
private fun OpenMapButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
    ) {
        Text(text = "Ver mapa", color = RevScopeColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

/** Única CTA del grupo de gauges apagados en modo GPS: lleva a vincular un adaptador. */
@Composable
internal fun ConfigureAdapterCta(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = RevScopeColors.Accent)
            Text(
                "RPM, temperatura, marcha y boost necesitan un adaptador OBD2.",
                color = RevScopeColors.TextSecondary,
                style = RevScopeType.body,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            // Con letra al 200 % en 360 dp no cabe entero: se achica un poco antes que partirse.
            TextoAjustable(
                texto = "Configurar adaptador",
                estilo = RevScopeType.label,
                color = RevScopeColors.Accent,
                minimo = 10.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = RevScopeColors.Accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Aviso de nueva versión en GitHub: descargar (abre el navegador en el APK) o descartar. */
@Composable
internal fun UpdateBanner(version: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .padding(start = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                Text("Nueva versión $version disponible", color = RevScopeColors.Accent, style = RevScopeType.label)
                Text(
                    "Descárgala para actualizar",
                    color = RevScopeColors.TextSecondary,
                    style = RevScopeType.bodySmall,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Descartar aviso", tint = RevScopeColors.TextSecondary)
            }
        }
        TextButton(onClick = onDownload, modifier = Modifier.align(Alignment.End).padding(end = 4.dp)) {
            Text("Descargar", color = RevScopeColors.Accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun TripScoreBar(tripScore: TripScore) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.SurfaceHigh, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${tripScore.style.emoji} ${tripScore.style.label}",
            style = RevScopeType.label,
            color = RevScopeColors.TextPrimary,
        )
        Text(
            text = "Puntaje: ${tripScore.overall}/100",
            style = RevScopeType.label.conCifrasTabulares(),
            fontWeight = FontWeight.Bold,
            color = RevScopeColors.Accent,
        )
    }
}

/** Fuente de la velocidad bajo el velocímetro: toca para alternar entre la ECU y el GPS. */
@Composable
private fun SpeedSourceChip(useGps: Boolean, onToggle: () -> Unit) {
    ChipSeleccion(
        texto = if (useGps) "GPS" else "OBD",
        seleccionado = useGps,
        onClick = onToggle,
        modifier = Modifier.semantics {
            contentDescription = "Fuente de la velocidad"
            stateDescription = if (useGps) "GPS del teléfono" else "ECU por OBD"
        },
    )
}
