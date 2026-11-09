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
import androidx.annotation.StringRes
import com.revscope.core.common.i18n.Region
import com.revscope.core.common.i18n.moneyFractionDigits
import com.revscope.core.common.i18n.pricePerLiterInput
import java.util.Currency

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

    CombustibleContenido(
        FuelPriceFormat(vm.fuelRegion, vm.fuelCurrency),
        FuelPriceFields(fuelPriceCorriente, fuelPriceExtra, fuelPriceDiesel),
        FuelPriceActions(vm::updateFuelPriceCorriente, vm::updateFuelPriceExtra, vm::updateFuelPriceDiesel, vm::saveFuelPrices),
    )
}

internal data class FuelPriceFormat(val region: Region, val currency: Currency)

internal data class FuelPriceFields(val corriente: String, val extra: String, val diesel: String)

internal class FuelPriceActions(
    val onCorriente: (String) -> Unit,
    val onExtra: (String) -> Unit,
    val onDiesel: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
internal fun CombustibleContenido(format: FuelPriceFormat, fields: FuelPriceFields, actions: FuelPriceActions) {
    val perLiter = pricePerLiterInput(format.region)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.settings_fuel_section_title))
        val regular = if (perLiter) R.string.settings_fuel_regular_price_liter else R.string.settings_fuel_regular_price
        val premium = if (perLiter) R.string.settings_fuel_premium_price_liter else R.string.settings_fuel_premium_price
        val diesel = if (perLiter) R.string.settings_fuel_diesel_price_liter else R.string.settings_fuel_diesel_price
        FuelPriceField(fields.corriente, actions.onCorriente, regular, format.currency)
        FuelPriceField(fields.extra, actions.onExtra, premium, format.currency)
        FuelPriceField(fields.diesel, actions.onDiesel, diesel, format.currency)
        Text(
            stringResource(fuelPriceExplanation(format.region)),
            color = TextMutedColor,
            fontSize = 11.sp,
        )
        Button(
            onClick = actions.onSave,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        ) { Text(stringResource(R.string.settings_fuel_save_prices), color = BgColor) }
    }
}

@Composable
private fun FuelPriceField(value: String, onValueChange: (String) -> Unit, @StringRes label: Int, currency: Currency) {
    val keyboard = if (moneyFractionDigits(currency) > 0) KeyboardType.Decimal else KeyboardType.Number
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label, currency.currencyCode), fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = settingsFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun fuelPriceExplanation(region: Region): Int =
    if (region == Region.CO) R.string.settings_fuel_price_description else R.string.settings_fuel_price_hint
