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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel

private val BgColor = Color(0xFF0A0A0F)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
internal fun VehiculoActivoCard(vm: SettingsViewModel, onNavigateToVehicleProfiles: () -> Unit) {
    val activeVehicleProfile by vm.activeVehicleProfile.collectAsState()
    val askVehicleOnStart by vm.askVehicleOnStart.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_vehicle_section_title))
        NavRow(
            stringResource(
                R.string.settings_vehicle_active,
                activeVehicleProfile?.name ?: stringResource(R.string.settings_vehicle_none),
            ),
            onNavigateToVehicleProfiles,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_vehicle_ask_on_start),
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
internal fun HerramientasCard(
    vm: SettingsViewModel,
    onNavigateToVehicleProfiles: () -> Unit,
    viajeVm: ViajeObdSettingsViewModel = hiltViewModel(),
) {
    val keepScreenOn by vm.keepScreenOn.collectAsState()
    val autoTripOnMove by viajeVm.autoTripOnMove.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_tools_section_title))
        NavRow(stringResource(R.string.settings_vehicle_profiles), onNavigateToVehicleProfiles)
        ToggleRow(
            stringResource(R.string.settings_keep_screen_on),
            keepScreenOn,
            vm::updateKeepScreenOn,
            subtitle = stringResource(R.string.settings_keep_screen_on_description),
        )
        ToggleRow(
            stringResource(R.string.settings_auto_trip_on_move),
            autoTripOnMove,
            viajeVm::updateAutoTripOnMove,
            subtitle = stringResource(R.string.settings_auto_trip_on_move_description),
        )
    }
}

@Composable
internal fun CombustibleCard(vm: SettingsViewModel) {
    val fuelPriceCorriente by vm.fuelPriceCorriente.collectAsState()
    val fuelPriceExtra by vm.fuelPriceExtra.collectAsState()
    val fuelPriceDiesel by vm.fuelPriceDiesel.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_fuel_section_title))
        OutlinedTextField(
            value = fuelPriceCorriente,
            onValueChange = vm::updateFuelPriceCorriente,
            label = { Text(stringResource(R.string.settings_fuel_regular_price), fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fuelPriceExtra,
            onValueChange = vm::updateFuelPriceExtra,
            label = { Text(stringResource(R.string.settings_fuel_premium_price), fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fuelPriceDiesel,
            onValueChange = vm::updateFuelPriceDiesel,
            label = { Text(stringResource(R.string.settings_fuel_diesel_price), fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = settingsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.settings_fuel_price_description),
            color = TextMutedColor,
            fontSize = 11.sp,
        )
        Button(
            onClick = vm::saveFuelPrices,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text(stringResource(R.string.settings_fuel_save_prices), color = BgColor) }
    }
}
