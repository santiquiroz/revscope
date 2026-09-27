package com.revscope.feature.workshop.taller.multimetro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.designsystem.BarraConVolver
import com.revscope.core.designsystem.ChipSeleccion
import com.revscope.core.designsystem.ErrorState
import com.revscope.core.designsystem.NivelBadge
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.ResultadoEcu
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.feature.workshop.taller.prueba.CuerpoDesplazable
import com.revscope.feature.workshop.taller.TarjetaTaller
import com.revscope.feature.workshop.taller.nivel
import com.revscope.feature.workshop.taller.prueba.PieConAccion
import com.revscope.feature.workshop.taller.prueba.Seccion
import com.revscope.feature.workshop.taller.texto

data class AccionesMultimetro(
    val onVolver: () -> Unit = {},
    val onSensor: (SensorMultimetro) -> Unit = {},
    val onValor: (FuncionCable, String, String) -> Unit = { _, _, _ -> },
    val onPedirColor: (FuncionCable) -> Unit = {},
    val onCambiarColor: (String) -> Unit = {},
    val onAceptarColor: () -> Unit = {},
    val onCerrarDialogo: () -> Unit = {},
    val onGuardarColores: () -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onReintentar: () -> Unit = {},
)

@Composable
fun MultimetroContent(estado: MultimetroUi, acciones: AccionesMultimetro) {
    Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
        BarraConVolver(titulo = "Multímetro", subtitulo = estado.vehiculo, onVolver = acciones.onVolver)
        val plantilla = estado.plantilla
        when {
            estado.error != null -> Box(Modifier.weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                ErrorState(estado.error, acciones.onReintentar)
            }
            plantilla == null -> Cargando()
            else -> Cuerpo(estado, plantilla, acciones)
        }
    }
    estado.dialogoColor?.let { DialogoColor(it, acciones) }
}

@Composable
private fun ColumnScope.Cargando() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
    ) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
        Text("Cargando la plantilla…", color = RevScopeColors.TextPrimary, style = RevScopeType.body)
    }
}

@Composable
private fun ColumnScope.Cuerpo(estado: MultimetroUi, plantilla: PlantillaUi, acciones: AccionesMultimetro) {
    CuerpoDesplazable {
        SelectorSensor(estado.sensor, estado.sensores, acciones.onSensor)
        TarjetaDondeMedir(plantilla)
        Seccion("Medidas en ${plantilla.unidad}")
        MedicionesPlantilla(plantilla, acciones.onValor)
        Seccion("Veredicto")
        estado.veredicto?.let { TarjetaVeredicto(it) } ?: SinMedidas()
        SeccionColores(estado.colores, acciones)
    }
    PieConAccion(estado.guardar.texto, acciones.onGuardar, habilitado = estado.guardar.habilitado, aviso = estado.guardar.detalle)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorSensor(actual: SensorMultimetro, sensores: List<SensorMultimetro>, onSensor: (SensorMultimetro) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sensores.forEach { s ->
            ChipSeleccion(
                nombreCorto(s),
                seleccionado = s == actual,
                onClick = { onSensor(s) },
                modifier = Modifier.semantics { contentDescription = s.titulo },
            )
        }
    }
}

internal fun nombreCorto(s: SensorMultimetro): String = when (s) {
    SensorMultimetro.TPS -> "TPS"
    SensorMultimetro.MAP -> "MAP"
    SensorMultimetro.ECT -> "Temp. motor"
    SensorMultimetro.IAT -> "Temp. aire"
    SensorMultimetro.INYECTOR -> "Inyector"
    SensorMultimetro.BATERIA -> "Batería"
}

@Composable
private fun TarjetaDondeMedir(plantilla: PlantillaUi) {
    TarjetaTaller {
        Text(plantilla.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        FilaIcono(Icons.Filled.ElectricalServices, RevScopeColors.Accent, plantilla.dondeMedir)
        FilaIcono(Icons.Filled.Info, RevScopeColors.TextSecondary, plantilla.origenColores, secundario = true)
    }
}

@Composable
private fun SinMedidas() {
    TarjetaTaller {
        Text(
            "Escribe lo que marca el multímetro en cada cable: el veredicto de cada celda y el de todo el circuito " +
                "aparecen al escribir.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
    }
}

@Composable
private fun TarjetaVeredicto(v: VeredictoUi) {
    TarjetaTaller(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        NivelBadge(v.veredicto.nivel(), texto = v.veredicto.texto())
        Text(v.titulo, color = RevScopeColors.TextPrimary, style = RevScopeType.title)
        v.interpretacion?.let { Text(it, color = RevScopeColors.TextPrimary, style = RevScopeType.body) }
        v.ecu?.let { ComparacionConEcu(it) }
    }
}

@Composable
private fun ComparacionConEcu(c: ComparacionEcuUi) {
    val color = if (c.resultado == ResultadoEcu.COINCIDE) RevScopeColors.Success else RevScopeColors.Warning
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text("Contra lo que recibe la ECU", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
        FilaIcono(Icons.AutoMirrored.Filled.CompareArrows, color, c.texto)
        Text(c.origen, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}

@Composable
private fun SeccionColores(colores: ColoresUi, acciones: AccionesMultimetro) {
    if (colores.cables.isEmpty()) return
    Seccion("Colores de los cables")
    TarjetaTaller {
        colores.cables.forEach { FilaColor(it, acciones.onPedirColor) }
        if (colores.editados) PieColores(colores, acciones.onGuardarColores)
    }
}

@Composable
private fun FilaColor(c: ColorCableUi, onEditar: (FuncionCable) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.etiqueta, color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            Text(c.color ?: "Sin color", color = RevScopeColors.TextSecondary, style = RevScopeType.body)
        }
        TextButton(
            onClick = { onEditar(c.funcion) },
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Editar el color de ${c.etiqueta}" },
        ) {
            Text("Editar", color = RevScopeColors.Accent, style = RevScopeType.label)
        }
    }
}

@Composable
private fun PieColores(colores: ColoresUi, onGuardar: () -> Unit) {
    if (colores.modelo == null) {
        Text(
            "Para guardar estos colores como plantilla, elige el modelo de referencia del vehículo en Perfiles. Mientras " +
                "tanto se usan en esta medición.",
            color = RevScopeColors.TextSecondary,
            style = RevScopeType.body,
        )
        return
    }
    OutlinedButton(onClick = onGuardar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(
            "Guardar colores como plantilla de ${colores.modelo}",
            color = RevScopeColors.Accent,
            style = RevScopeType.label,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DialogoColor(d: DialogoColorUi, acciones: AccionesMultimetro) {
    AlertDialog(
        onDismissRequest = acciones.onCerrarDialogo,
        containerColor = RevScopeColors.SurfaceHigh,
        title = { Text("Color de ${d.etiqueta}", color = RevScopeColors.TextPrimary, style = RevScopeType.title) },
        text = {
            OutlinedTextField(
                value = d.texto,
                onValueChange = acciones.onCambiarColor,
                label = { Text("Color", style = RevScopeType.body) },
                supportingText = { Text("Por ejemplo «Verde-amarillo». Vacío = sin color.", style = RevScopeType.bodySmall) },
                singleLine = true,
                textStyle = RevScopeType.body,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RevScopeColors.TextPrimary,
                    unfocusedTextColor = RevScopeColors.TextPrimary,
                    focusedBorderColor = RevScopeColors.Accent,
                    unfocusedBorderColor = RevScopeColors.TextMuted,
                    focusedLabelColor = RevScopeColors.Accent,
                    unfocusedLabelColor = RevScopeColors.TextSecondary,
                    cursorColor = RevScopeColors.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = acciones.onAceptarColor, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Aceptar", color = RevScopeColors.Accent, style = RevScopeType.label)
            }
        },
        dismissButton = {
            TextButton(onClick = acciones.onCerrarDialogo, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancelar", color = RevScopeColors.TextPrimary, style = RevScopeType.label)
            }
        },
    )
}

@Composable
private fun FilaIcono(icono: ImageVector, color: Color, texto: String, secundario: Boolean = false) {
    val lado = with(LocalDensity.current) { 18.sp.toDp() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(lado))
        Text(
            texto,
            color = if (secundario) RevScopeColors.TextSecondary else RevScopeColors.TextPrimary,
            style = RevScopeType.body,
            modifier = Modifier.weight(1f),
        )
    }
}
