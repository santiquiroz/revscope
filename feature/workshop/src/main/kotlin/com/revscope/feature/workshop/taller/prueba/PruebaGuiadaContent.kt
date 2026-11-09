package com.revscope.feature.workshop.taller.prueba

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.ConfirmarDestructivoDialog
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.feature.workshop.R
import com.revscope.feature.workshop.taller.BotonPrincipalTaller

data class AccionesPrueba(
    val onVolver: () -> Unit = {},
    val onElegir: (TipoPrueba) -> Unit = {},
    val onEmpezar: () -> Unit = {},
    val onAvanzar: () -> Unit = {},
    val onRepetirPaso: () -> Unit = {},
    val onVoz: (Boolean) -> Unit = {},
    val onPedirCancelar: () -> Unit = {},
    val onConfirmarCancelar: () -> Unit = {},
    val onCerrarDialogo: () -> Unit = {},
    val onPedirVref: () -> Unit = {},
    val onGuardarVref: (Double?) -> Unit = {},
    val onPedirDesfase: () -> Unit = {},
    val onGuardarDesfase: (Double?) -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onTerminar: () -> Unit = {},
    val onGuardarEnSesion: () -> Unit = {},
    val onVerGuia: ((String) -> Unit)? = null,
)

@Composable
fun PruebaGuiadaContent(estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        when (val fase = estado.fase) {
            is FasePantalla.Elegir -> ElegirPrueba(fase, acciones)
            is FasePantalla.Preparacion -> PreparacionPrueba(fase, estado, acciones)
            is FasePantalla.Paso -> PasoPrueba(fase.paso, estado, acciones)
            is FasePantalla.Analizando -> Analizando(estado, acciones)
            is FasePantalla.Resultado -> ResultadoPrueba(fase.resultado, estado, acciones)
            is FasePantalla.Cancelada -> Interrumpida(estado, stringResource(R.string.workshop_prueba_pantalla_cancelada), fase.motivo, acciones)
            is FasePantalla.Fallida -> Fallida(fase, estado, acciones)
        }
    }
    DialogosPrueba(estado, acciones)
}

// ── Estructura común ────────────────────────────────────────────────────────

// Durante la prueba la salida es «Cancelar» (✕ con nombre), no «Volver»: cancelar puede descartar lo grabado.
@Composable
internal fun BarraPrueba(titulo: String, subtitulo: String?, onCancelar: () -> Unit) {
    Surface(color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).heightIn(min = 56.dp).padding(end = 16.dp),
        ) {
            IconButton(onClick = onCancelar) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(TextosPrueba.CANCELAR), tint = RevScopeColors.TextPrimary)
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title, modifier = Modifier.semantics { heading() })
                subtitulo?.let { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall) }
            }
        }
    }
}

@Composable
internal fun ColumnScope.CuerpoDesplazable(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        content = contenido,
    )
}

// La única CTA lima de la fase, fija abajo para alcanzarla con el pulgar.
@Composable
internal fun PieConAccion(texto: String, onClick: () -> Unit, habilitado: Boolean = true, aviso: String? = null) {
    Surface(color = RevScopeColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        ) {
            aviso?.let { Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.body) }
            BotonPrincipalTaller(texto, onClick, habilitado = habilitado)
        }
    }
}

@Composable
internal fun Seccion(titulo: String) {
    Text(
        titulo.uppercase(),
        color = RevScopeColors.TextSecondary,
        style = RevScopeType.label,
        modifier = Modifier.semantics { heading() },
    )
}

// ── Fases cortas ────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.Analizando(estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    BarraPrueba(estado.titulo(), estado.vehiculo, acciones.onPedirCancelar)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
    ) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
        Text(
            stringResource(R.string.workshop_prueba_pantalla_analizando),
            color = RevScopeColors.TextPrimary,
            style = RevScopeType.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun ColumnScope.Interrumpida(estado: PruebaGuiadaUi, titulo: String, motivo: String, acciones: AccionesPrueba) {
    BarraConVolver(titulo = estado.titulo(), subtitulo = estado.vehiculo, onVolver = acciones.onTerminar)
    CuerpoDesplazable {
        Text(titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        Text(motivo, color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        Text(stringResource(R.string.workshop_prueba_pantalla_nada_guardado), color = RevScopeColors.TextSecondary, style = RevScopeType.body)
    }
    PieConAccion(stringResource(R.string.workshop_prueba_pantalla_empezar_de_nuevo), acciones.onReintentar)
}

@Composable
private fun ColumnScope.Fallida(fase: FasePantalla.Fallida, estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    BarraConVolver(titulo = estado.titulo(), subtitulo = estado.vehiculo, onVolver = acciones.onTerminar)
    val mensaje = stringResource(R.string.workshop_prueba_pantalla_fallida, fase.motivo)
    Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        if (fase.reintentable) {
            ErrorState(mensaje, acciones.onReintentar)
        } else {
            Text(mensaje, color = RevScopeColors.TextPrimary, style = RevScopeType.body, textAlign = TextAlign.Center)
        }
    }
    if (!fase.reintentable) PieConAccion(stringResource(R.string.workshop_prueba_pantalla_volver), acciones.onTerminar)
}

// ── Diálogos ────────────────────────────────────────────────────────────────

@Composable
private fun DialogosPrueba(estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    when (estado.dialogo) {
        DialogoPrueba.CANCELAR -> ConfirmarCancelar(estado, acciones)
        DialogoPrueba.VREF -> DialogoVref(estado, acciones.onGuardarVref, acciones.onCerrarDialogo)
        DialogoPrueba.DESFASE -> DialogoDesfase(estado, acciones.onGuardarDesfase, acciones.onCerrarDialogo)
        null -> Unit
    }
}

@Composable
private fun ConfirmarCancelar(estado: PruebaGuiadaUi, acciones: AccionesPrueba) {
    val paso = (estado.fase as? FasePantalla.Paso)?.paso
    ConfirmarDestructivoDialog(
        titulo = stringResource(R.string.workshop_prueba_pantalla_descartar_titulo),
        textoConfirmar = stringResource(R.string.workshop_prueba_pantalla_descartar),
        textoCancelar = stringResource(R.string.workshop_prueba_pantalla_seguir),
        onConfirmar = acciones.onConfirmarCancelar,
        onCancelar = acciones.onCerrarDialogo,
        avisos = listOfNotNull(stringResource(R.string.workshop_prueba_pantalla_aviso_detiene), paso?.let { loQueSePierde(it) }),
    )
}

@Composable
private fun loQueSePierde(paso: PasoUi): String =
    if (paso.grabados > 0) {
        stringResource(R.string.workshop_prueba_pantalla_aviso_pasos_hechos, paso.grabados, paso.total)
    } else {
        stringResource(R.string.workshop_prueba_pantalla_aviso_paso_en_curso)
    }

@Composable
internal fun PruebaGuiadaUi.titulo(): String =
    fase.tipo?.let { stringResource(it.tituloRes) } ?: stringResource(R.string.workshop_prueba_pantalla_titulo)
