package com.revscope.feature.workshop.taller.multimetro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.conCifrasTabulares
import com.revscope.core.obd.taller.multimetro.EstadoCelda
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.feature.workshop.taller.TarjetaTaller

object DisposicionMultimetro {
    const val ANCHO_CABLE_DP = 92f
    const val ANCHO_CONDICION_DP = 64f
    const val SEPARACION_DP = 4f
    const val RELLENO_TARJETA_DP = 16f

    // Con letra grande las columnas no caben: cada cable pasa a su propia tarjeta en vez de cortar números.
    fun usaTabla(anchoDisponibleDp: Float, escalaLetra: Float, condiciones: Int): Boolean {
        val necesario = (ANCHO_CABLE_DP + (ANCHO_CONDICION_DP + SEPARACION_DP) * condiciones) * escalaLetra
        return necesario <= anchoDisponibleDp - 2 * RELLENO_TARJETA_DP
    }
}

@Composable
internal fun MedicionesPlantilla(plantilla: PlantillaUi, onValor: (FuncionCable, String, String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tabla = DisposicionMultimetro.usaTabla(maxWidth.value, LocalDensity.current.fontScale, plantilla.condiciones.size)
        if (tabla) TablaCables(plantilla, onValor) else TarjetasCables(plantilla, onValor)
    }
}

// ── Tabla: cables × condiciones ─────────────────────────────────────────────

private val ANCHO_CABLE = (DisposicionMultimetro.ANCHO_CABLE_DP - DisposicionMultimetro.SEPARACION_DP).dp

@Composable
private fun TablaCables(plantilla: PlantillaUi, onValor: (FuncionCable, String, String) -> Unit) {
    TarjetaTaller {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DisposicionMultimetro.SEPARACION_DP.dp)) {
            Text("Cable", color = RevScopeColors.TextSecondary, style = RevScopeType.label, modifier = Modifier.width(ANCHO_CABLE))
            plantilla.condiciones.forEach {
                Text(it, color = RevScopeColors.TextSecondary, style = RevScopeType.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        plantilla.filas.forEach { fila ->
            HorizontalDivider(color = RevScopeColors.SurfaceHigh)
            FilaTabla(fila, plantilla.unidad, onValor)
        }
        HorizontalDivider(color = RevScopeColors.SurfaceHigh)
        ValoresEsperados(plantilla)
    }
}

@Composable
private fun FilaTabla(fila: FilaCableUi, unidad: String, onValor: (FuncionCable, String, String) -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(DisposicionMultimetro.SEPARACION_DP.dp)) {
        EtiquetaCable(fila, Modifier.width(ANCHO_CABLE).padding(top = 8.dp))
        fila.celdas.forEach { celda ->
            Box(Modifier.weight(1f)) {
                if (celda.aplica) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CampoValor(fila, celda, unidad, onValor)
                        IndicadorCelda(celda, corto = true)
                    }
                } else {
                    Text("—", color = RevScopeColors.TextSecondary, style = RevScopeType.body, modifier = Modifier.padding(top = 16.dp).align(Alignment.TopCenter))
                }
            }
        }
    }
}

// En la tabla no cabe la banda de cada celda: se cita debajo, una vez por cable si es la misma en todas.
@Composable
private fun ValoresEsperados(plantilla: PlantillaUi) {
    Text("Valores esperados", color = RevScopeColors.TextSecondary, style = RevScopeType.label)
    plantilla.filas.forEach { fila ->
        val celdas = fila.celdas.filter { it.aplica && it.referencia != null }
        val unica = celdas.map { it.referencia }.distinct().singleOrNull()
        if (unica != null) {
            TextoEsperado("${fila.etiqueta}: $unica")
        } else {
            celdas.forEach { TextoEsperado("${fila.etiqueta} · ${it.etiquetaCondicion}: ${it.referencia}") }
        }
    }
}

@Composable
private fun TextoEsperado(texto: String) {
    Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall.conCifrasTabulares())
}

// ── Tarjetas: una por cable, para letra grande ──────────────────────────────

@Composable
private fun TarjetasCables(plantilla: PlantillaUi, onValor: (FuncionCable, String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        plantilla.filas.forEach { fila ->
            TarjetaTaller {
                EtiquetaCable(fila)
                fila.celdas.filter { it.aplica }.forEach { celda -> CeldaEnTarjeta(fila, celda, plantilla.unidad, onValor) }
            }
        }
    }
}

@Composable
private fun CeldaEnTarjeta(fila: FilaCableUi, celda: CeldaUi, unidad: String, onValor: (FuncionCable, String, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(celda.etiquetaCondicion, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        CampoValor(fila, celda, unidad, onValor, conUnidad = true)
        IndicadorCelda(celda, corto = false)
        celda.referencia?.let { TextoEsperado("Esperado: $it") }
    }
}

// ── Piezas comunes ──────────────────────────────────────────────────────────

@Composable
private fun EtiquetaCable(fila: FilaCableUi, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(fila.etiqueta, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        Text(fila.color ?: "Sin color", color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoValor(
    fila: FilaCableUi,
    celda: CeldaUi,
    unidad: String,
    onValor: (FuncionCable, String, String) -> Unit,
    conUnidad: Boolean = false,
) {
    val descripcion = "${fila.etiqueta}${fila.color?.let { " ($it)" }.orEmpty()}, ${celda.etiquetaCondicion}, en $unidad"
    val interaccion = remember { MutableInteractionSource() }
    val colores = coloresCampo()
    // Relleno de 8 dp y no los 16 del campo estándar: en la tabla de un teléfono de 360 dp cada columna mide ~64 dp.
    BasicTextField(
        value = celda.texto,
        onValueChange = { onValor(fila.funcion, celda.condicion, it) },
        singleLine = true,
        textStyle = RevScopeType.body.conCifrasTabulares().copy(color = RevScopeColors.TextPrimary),
        cursorBrush = SolidColor(RevScopeColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        interactionSource = interaccion,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = descripcion },
    ) { campo ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = celda.texto,
            innerTextField = campo,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interaccion,
            isError = celda.invalido,
            suffix = if (conUnidad) ({ Text(unidad, style = RevScopeType.body) }) else null,
            colors = colores,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            container = {
                OutlinedTextFieldDefaults.Container(enabled = true, isError = celda.invalido, interactionSource = interaccion, colors = colores)
            },
        )
    }
}

@Composable
private fun coloresCampo() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RevScopeColors.TextPrimary,
    unfocusedTextColor = RevScopeColors.TextPrimary,
    focusedBorderColor = RevScopeColors.Accent,
    unfocusedBorderColor = RevScopeColors.TextMuted,
    errorBorderColor = RevScopeColors.Danger,
    cursorColor = RevScopeColors.Accent,
    focusedSuffixColor = RevScopeColors.TextSecondary,
    unfocusedSuffixColor = RevScopeColors.TextSecondary,
)

private data class Aspecto(val icono: ImageVector, val color: Color, val corto: String, val largo: String)

private fun aspecto(celda: CeldaUi): Aspecto? = when {
    celda.invalido -> Aspecto(Icons.Filled.Error, RevScopeColors.Danger, "No es un número", "No es un número: usa coma o punto")
    celda.estado == null -> null
    else -> aspectoDe(celda.estado)
}

private fun aspectoDe(estado: EstadoCelda): Aspecto = when (estado) {
    EstadoCelda.DENTRO -> Aspecto(Icons.Filled.CheckCircle, RevScopeColors.Success, "Dentro", "Dentro de lo esperado")
    EstadoCelda.BAJO -> Aspecto(Icons.Filled.ArrowDownward, RevScopeColors.Danger, "Bajo", "Por debajo de lo esperado")
    EstadoCelda.ALTO -> Aspecto(Icons.Filled.ArrowUpward, RevScopeColors.Danger, "Alto", "Por encima de lo esperado")
    EstadoCelda.SIN_REFERENCIA -> Aspecto(Icons.Filled.RemoveCircle, RevScopeColors.TextSecondary, "Sin ref.", "Sin valor esperado")
}

@Composable
private fun IndicadorCelda(celda: CeldaUi, corto: Boolean) {
    val a = aspecto(celda) ?: return
    val lado = with(LocalDensity.current) { 14.sp.toDp() }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
        Icon(a.icono, contentDescription = null, tint = a.color, modifier = Modifier.padding(top = 1.dp).size(lado))
        Text(if (corto) a.corto else a.largo, color = a.color, style = RevScopeType.bodySmall)
    }
}
