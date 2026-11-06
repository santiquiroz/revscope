package com.revscope.feature.workshop.taller

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import com.revscope.feature.workshop.R

data class AccionesHub(
    val onIniciarDiagnostico: () -> Unit = {},
    val onAbrirSesion: (Long) -> Unit = {},
    val onConectarAdaptador: () -> Unit = {},
    val onElegirVehiculo: () -> Unit = {},
    val onVerTodas: () -> Unit = {},
    val onCodigos: () -> Unit = {},
    val onSensores: () -> Unit = {},
    val onChequeo: () -> Unit = {},
    val onPruebasGuiadas: (() -> Unit)? = null,
    val onMultimetro: (() -> Unit)? = null,
    val onAlDia: () -> Unit = {},
    val onMezcla: () -> Unit = {},
    val onEscaner: () -> Unit = {},
    val onOndaO2: () -> Unit = {},
    val onMode06: () -> Unit = {},
    val onCapturaCanRuta: () -> Unit = {},
    val onMecanicoIa: () -> Unit = {},
    val onMarchas: () -> Unit = {},
    val onPerfiles: () -> Unit = {},
    val onMantenimiento: () -> Unit = {},
    val onOdometro: () -> Unit = {},
    val onVelocimetros: () -> Unit = {},
    val onConocimientoModelo: () -> Unit = {},
)

data class HerramientaTaller(
    val icono: ImageVector,
    @StringRes val titulo: Int,
    @StringRes val descripcion: Int,
    val requiereAdaptador: Boolean,
    val onAbrir: () -> Unit,
)

data class GrupoHerramientas(@StringRes val titulo: Int, val herramientas: List<HerramientaTaller>)

internal object HerramientasTaller {

    private const val ANCHO_MINIMO_DOS_COLUMNAS_DP = 240f

    // Con letra grande una rejilla de dos columnas parte las palabras: se pasa a una sola.
    fun columnas(anchoDisponibleDp: Float, escalaLetra: Float): Int =
        if (anchoDisponibleDp / escalaLetra >= ANCHO_MINIMO_DOS_COLUMNAS_DP) 2 else 1

    fun rapidas(a: AccionesHub): List<HerramientaTaller> = listOfNotNull(
        HerramientaTaller(
            Icons.Filled.BugReport,
            R.string.workshop_hub_tool_codes,
            R.string.workshop_hub_tool_codes_description,
            true,
            a.onCodigos,
        ),
        a.onPruebasGuiadas?.let {
            HerramientaTaller(
                Icons.Filled.Science,
                R.string.workshop_hub_tool_guided_tests,
                R.string.workshop_hub_tool_guided_tests_description,
                true,
                it,
            )
        },
        a.onMultimetro?.let {
            HerramientaTaller(
                Icons.Filled.ElectricalServices,
                R.string.workshop_hub_tool_multimeter,
                R.string.workshop_hub_tool_multimeter_description,
                false,
                it,
            )
        },
        HerramientaTaller(
            Icons.Filled.Timeline,
            R.string.workshop_hub_tool_sensors_capture,
            R.string.workshop_hub_tool_sensors_capture_description,
            true,
            a.onSensores,
        ),
        HerramientaTaller(
            Icons.Filled.MonitorHeart,
            R.string.workshop_hub_tool_health_check,
            R.string.workshop_hub_tool_health_check_description,
            false,
            a.onChequeo,
        ),
    )

    fun mas(a: AccionesHub): List<GrupoHerramientas> = listOf(
        GrupoHerramientas(
            R.string.workshop_hub_group_status,
            listOf(
                HerramientaTaller(
                    Icons.Filled.Verified,
                    R.string.workshop_hub_tool_vehicle_status,
                    R.string.workshop_hub_tool_vehicle_status_description,
                    false,
                    a.onAlDia,
                ),
            ),
        ),
        GrupoHerramientas(
            R.string.workshop_hub_group_diagnostics,
            listOf(
                HerramientaTaller(
                    Icons.Filled.Science,
                    R.string.workshop_hub_tool_fuel_mixture,
                    R.string.workshop_hub_tool_fuel_mixture_description,
                    true,
                    a.onMezcla,
                ),
                HerramientaTaller(
                    Icons.Filled.Search,
                    R.string.workshop_hub_tool_advanced_scanner,
                    R.string.workshop_hub_tool_advanced_scanner_description,
                    true,
                    a.onEscaner,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.ShowChart,
                    R.string.workshop_hub_tool_o2_waveform,
                    R.string.workshop_hub_tool_o2_waveform_description,
                    true,
                    a.onOndaO2,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.FactCheck,
                    R.string.workshop_hub_tool_onboard_test_results,
                    R.string.workshop_hub_tool_onboard_test_results_description,
                    true,
                    a.onMode06,
                ),
                HerramientaTaller(
                    Icons.Filled.Route,
                    R.string.workshop_hub_tool_route_can_capture,
                    R.string.workshop_hub_tool_route_can_capture_description,
                    true,
                    a.onCapturaCanRuta,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.Chat,
                    R.string.workshop_hub_tool_ai_mechanic,
                    R.string.workshop_hub_tool_ai_mechanic_description,
                    false,
                    a.onMecanicoIa,
                ),
            ),
        ),
        GrupoHerramientas(
            R.string.workshop_hub_group_vehicle,
            listOf(
                HerramientaTaller(
                    Icons.Filled.MenuBook,
                    R.string.workshop_hub_tool_model_information,
                    R.string.workshop_hub_tool_model_information_description,
                    false,
                    a.onConocimientoModelo,
                ),
                HerramientaTaller(
                    Icons.Filled.Settings,
                    R.string.workshop_hub_tool_gear_analyzer,
                    R.string.workshop_hub_tool_gear_analyzer_description,
                    true,
                    a.onMarchas,
                ),
                HerramientaTaller(
                    Icons.Filled.DirectionsCar,
                    R.string.workshop_hub_tool_vehicle_profiles,
                    R.string.workshop_hub_tool_vehicle_profiles_description,
                    false,
                    a.onPerfiles,
                ),
                HerramientaTaller(
                    Icons.Filled.Build,
                    R.string.workshop_hub_tool_maintenance,
                    R.string.workshop_hub_tool_maintenance_description,
                    false,
                    a.onMantenimiento,
                ),
                HerramientaTaller(
                    Icons.Filled.Speed,
                    R.string.workshop_hub_tool_odometer_check,
                    R.string.workshop_hub_tool_odometer_check_description,
                    false,
                    a.onOdometro,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    R.string.workshop_hub_tool_compare_speed,
                    R.string.workshop_hub_tool_compare_speed_description,
                    true,
                    a.onVelocimetros,
                ),
            ),
        ),
    )
}
