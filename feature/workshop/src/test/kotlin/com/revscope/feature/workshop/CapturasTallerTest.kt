package com.revscope.feature.workshop

import com.revscope.core.obd.legal.DocumentStatusCalculator.DocStatus
import com.revscope.core.obd.legal.DocumentStatusCalculator.DocType
import com.revscope.core.obd.legal.DocumentStatusCalculator.Nivel
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasTallerTest {

    private val estados = listOf(
        DocStatus(DocType.SOAT, Nivel.OK, "SOAT", "Vence el 14 mar 2027 · faltan 169 días"),
        DocStatus(DocType.RTM, Nivel.ATENCION, "Tecnomecánica", "Vence en 12 días"),
        DocStatus(DocType.PICO_Y_PLACA, Nivel.OK, "Pico y placa", "Hoy puedes circular"),
        DocStatus(DocType.TODO_RIESGO, Nivel.SIN_CONFIGURAR, "Todo riesgo", "Sin configurar"),
        DocStatus(DocType.LICENCIA, Nivel.VENCIDO, "Licencia de conducción", "Venció hace 3 días"),
    )

    @Test
    fun alDiaContent() = MatrizCaptura.pantalla("AlDiaContent", altoMinimoDp = 1600) {
        AlDiaContent(
            vehiculo = VehiculoAlDia(nombre = "Benelli TNT 150i", placa = "NZO28H"),
            estados = estados,
            licenciaVenceEn = null,
            mantenimientoNivel = Nivel.ATENCION,
            mantenimientoDetalle = "Cambio de aceite en 300 km",
            zoneBrief = null,
            acciones = AccionesAlDia(),
        )
    }

    @Test
    fun speedComparisonContent() = MatrizCaptura.pantalla("SpeedComparisonContent", altoMinimoDp = 1100) {
        SpeedComparisonContent(
            conectado = true,
            velocidadObd = 104.0,
            velocidadGps = 98.4,
            promedioPorcentaje = 5.6,
            onReiniciar = {},
            onVolver = {},
        )
    }

    private val diagnosticos = listOf(
        DiagnosticRules.Diagnosis(
            DiagnosticRules.Nivel.FALLA,
            "Códigos de falla",
            "P0122: sensor de posición del acelerador con señal baja",
            "Revisa el conector del TPS, la alimentación de 5 V y la masa.",
        ),
        DiagnosticRules.Diagnosis(
            DiagnosticRules.Nivel.ATENCION,
            "Batería",
            "Voltaje con contacto puesto: 12,0 V",
            "Algo bajo con el motor apagado; revisa la carga con el motor encendido.",
        ),
        DiagnosticRules.Diagnosis(DiagnosticRules.Nivel.OK, "Mezcla", "Ajuste largo +2,3 %", "Dentro de lo normal."),
    )

    @Test
    fun healthCheckContent() = MatrizCaptura.pantalla("HealthCheckContent", altoMinimoDp = 1100) {
        HealthCheckContent(
            HealthCheckViewModel.UiState.Done(diagnosticos, listOf("P0122"), timestamp = 1_790_000_000_000),
            AccionesChequeo(),
        )
    }

    @Test
    fun healthCheckContentError() = MatrizCaptura.pantalla("HealthCheckContent_error") {
        HealthCheckContent(HealthCheckViewModel.UiState.Error("Conecta el adaptador primero"), AccionesChequeo())
    }
}
