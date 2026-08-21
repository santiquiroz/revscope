package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFF0A0A0F)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun VehiculoActivoCard(vm: SettingsViewModel, onNavigateToVehicleProfiles: () -> Unit) {
    val activeVehicleProfile by vm.activeVehicleProfile.collectAsState()
    val askVehicleOnStart by vm.askVehicleOnStart.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Vehículo")
        NavRow(
            "Vehículo activo: ${activeVehicleProfile?.name ?: "Ninguno"}",
            onNavigateToVehicleProfiles,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Preguntar vehículo al inicio",
                color = TextPrimaryColor,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = askVehicleOnStart,
                onCheckedChange = vm::updateAskVehicleOnStart,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgColor,
                    checkedTrackColor = AccentColor,
                ),
            )
        }
    }
}

@Composable
internal fun HerramientasCard(vm: SettingsViewModel, onNavigateToVehicleProfiles: () -> Unit) {
    val keepScreenOn by vm.keepScreenOn.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Herramientas")
        NavRow("Perfiles de vehículo", onNavigateToVehicleProfiles)
        ToggleRow(
            "Mantener pantalla encendida",
            keepScreenOn,
            vm::updateKeepScreenOn,
            subtitle = "Durante la conducción con el dashboard abierto. Apagarlo ahorra batería en soporte de carro.",
        )
    }
}

@Composable
internal fun CombustibleCard(vm: SettingsViewModel) {
    val fuelPriceCorriente by vm.fuelPriceCorriente.collectAsState()
    val fuelPriceExtra by vm.fuelPriceExtra.collectAsState()
    val fuelPriceDiesel by vm.fuelPriceDiesel.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Combustible")
        OutlinedTextField(
            value = fuelPriceCorriente,
            onValueChange = vm::updateFuelPriceCorriente,
            label = { Text("Precio galón corriente (COP)", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fuelPriceExtra,
            onValueChange = vm::updateFuelPriceExtra,
            label = { Text("Precio galón extra (COP)", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fuelPriceDiesel,
            onValueChange = vm::updateFuelPriceDiesel,
            label = { Text("Precio galón diésel / ACPM (COP)", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Se usa el precio del tipo de combustible del vehículo activo para estimar el costo de " +
                "cada viaje. No hay fuente oficial en línea vigente en datos.gov.co para precios de " +
                "gasolina/ACPM por municipio (ver detalle en el código) — ajusta manualmente según tu estación.",
            color = TextMutedColor,
            fontSize = 11.sp,
        )
        Button(
            onClick = vm::saveFuelPrices,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text("Guardar precios", color = BgColor) }
    }
}
