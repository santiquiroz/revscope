package com.revscope.feature.workshop.taller

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
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.vector.ImageVector

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
    val titulo: String,
    val descripcion: String,
    val requiereAdaptador: Boolean,
    val onAbrir: () -> Unit,
)

data class GrupoHerramientas(val titulo: String, val herramientas: List<HerramientaTaller>)

internal object HerramientasTaller {

    private const val ANCHO_MINIMO_DOS_COLUMNAS_DP = 240f

    // Con letra grande una rejilla de dos columnas parte las palabras: se pasa a una sola.
    fun columnas(anchoDisponibleDp: Float, escalaLetra: Float): Int =
        if (anchoDisponibleDp / escalaLetra >= ANCHO_MINIMO_DOS_COLUMNAS_DP) 2 else 1

    fun rapidas(a: AccionesHub): List<HerramientaTaller> = listOfNotNull(
        HerramientaTaller(
            Icons.Filled.BugReport,
            "Códigos",
            "Leer, entender y borrar códigos de falla",
            true,
            a.onCodigos,
        ),
        a.onPruebasGuiadas?.let {
            HerramientaTaller(
                Icons.Filled.Science,
                "Pruebas guiadas",
                "Barrido del TPS, mínimo, batería y más",
                true,
                it,
            )
        },
        a.onMultimetro?.let {
            HerramientaTaller(
                Icons.Filled.ElectricalServices,
                "Multímetro",
                "Anota lo que mides en cada cable",
                false,
                it,
            )
        },
        HerramientaTaller(
            Icons.Filled.Timeline,
            "Sensores y captura",
            "Curvas en vivo y captura rápida",
            true,
            a.onSensores,
        ),
        HerramientaTaller(
            Icons.Filled.MonitorHeart,
            "Chequeo de salud",
            "Códigos, monitores, mezcla y batería",
            false,
            a.onChequeo,
        ),
    )

    fun mas(a: AccionesHub): List<GrupoHerramientas> = listOf(
        GrupoHerramientas(
            "Estado",
            listOf(
                HerramientaTaller(
                    Icons.Filled.Verified,
                    "Vehículo al día",
                    "SOAT, tecnomecánica, pico y placa, multas y todo riesgo",
                    false,
                    a.onAlDia,
                ),
            ),
        ),
        GrupoHerramientas(
            "Diagnóstico",
            listOf(
                HerramientaTaller(
                    Icons.Filled.Science,
                    "Mezcla y combustión",
                    "Ajustes, sonda O2, lambda y MAF interpretados en vivo",
                    true,
                    a.onMezcla,
                ),
                HerramientaTaller(
                    Icons.Filled.Search,
                    "Escáner avanzado (Mode 22)",
                    "Descubrir PIDs propios del fabricante",
                    true,
                    a.onEscaner,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.ShowChart,
                    "Onda de la sonda O2",
                    "Gráfica en vivo del voltaje del sensor de oxígeno",
                    true,
                    a.onOndaO2,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.FactCheck,
                    "Resultados a bordo (Mode 06)",
                    "Pruebas de monitoreo internas del fabricante",
                    true,
                    a.onMode06,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.Chat,
                    "Mecánico IA",
                    "Pregúntale al mecánico: ya conoce el estado real de tu vehículo",
                    false,
                    a.onMecanicoIa,
                ),
            ),
        ),
        GrupoHerramientas(
            "Vehículo",
            listOf(
                HerramientaTaller(
                    Icons.Filled.MenuBook,
                    "Conocimiento del modelo",
                    "ECU, repuestos, cableado y referencias con su fuente",
                    false,
                    a.onConocimientoModelo,
                ),
                HerramientaTaller(
                    Icons.Filled.Settings,
                    "Analizador de marchas",
                    "Calibrar la relación RPM/velocidad por marcha",
                    true,
                    a.onMarchas,
                ),
                HerramientaTaller(
                    Icons.Filled.DirectionsCar,
                    "Perfiles de vehículo",
                    "Vehículos guardados, línea roja y VIN",
                    false,
                    a.onPerfiles,
                ),
                HerramientaTaller(
                    Icons.Filled.Build,
                    "Mantenimiento",
                    "Aceite, llantas, batería y otros ítems por kilometraje",
                    false,
                    a.onMantenimiento,
                ),
                HerramientaTaller(
                    Icons.Filled.Speed,
                    "Verificación de kilometraje",
                    "Odómetro real de la ECU e histórico contra manipulación",
                    false,
                    a.onOdometro,
                ),
                HerramientaTaller(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    "Comparar velocímetros",
                    "Velocidad OBD contra GPS en vivo",
                    true,
                    a.onVelocimetros,
                ),
            ),
        ),
    )
}
