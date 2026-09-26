package com.revscope.feature.workshop

import com.revscope.core.obd.legal.DocumentStatusCalculator.DocStatus
import com.revscope.core.obd.legal.DocumentStatusCalculator.DocType
import com.revscope.core.obd.legal.DocumentStatusCalculator.Nivel
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
}
