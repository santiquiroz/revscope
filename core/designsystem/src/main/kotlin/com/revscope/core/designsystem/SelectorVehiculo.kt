package com.revscope.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class VehiculoEnEncabezado(
    val nombre: String?,
    val esMoto: Boolean,
    val estadoEnlace: String,
    val colorEstado: Color,
)

fun descripcionSelectorVehiculo(vehiculo: VehiculoEnEncabezado): String =
    "Vehículo: ${vehiculo.nombre ?: "sin vehículo"}. Adaptador: ${vehiculo.estadoEnlace}. Toca para cambiar de vehículo"

/**
 * Selector del vehículo activo para el encabezado de cada pestaña. Un nombre muy largo envuelve a
 * dos líneas y luego se corta con «…»: el nombre completo y el estado del adaptador van en la
 * descripción accesible.
 */
@Composable
fun SelectorVehiculo(
    vehiculo: VehiculoEnEncabezado,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = RevScopeColors.SurfaceHigh,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = descripcionSelectorVehiculo(vehiculo) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(Modifier.size(8.dp).background(vehiculo.colorEstado, CircleShape))
            Icon(
                imageVector = if (vehiculo.esMoto) Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = RevScopeColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = vehiculo.nombre ?: "Sin vehículo",
                color = RevScopeColors.TextPrimary,
                style = RevScopeType.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {},
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = RevScopeColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Franja del encabezado, bajo la barra de título, donde cada pestaña monta el selector de vehículo.
 * Va en su propia fila porque en 360 dp, junto al título y las acciones, el nombre no cabía.
 */
@Composable
fun FranjaSelectorVehiculo(selector: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface)
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) { selector() }
}
