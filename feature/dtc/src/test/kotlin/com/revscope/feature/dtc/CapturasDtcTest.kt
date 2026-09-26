package com.revscope.feature.dtc

import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasDtcTest {

    private val acciones = AccionesDtc(onVolver = {}, onLeer = {}, onBorrar = {}, onOpenAiValue = {})

    private val conCodigos = DtcPantallaUi(
        estado = DtcUiState.HasCodes(
            listOf(
                DtcCodeUi(
                    DtcCode("P0122", DtcMode.Active),
                    explanation = "Sensor de posición del acelerador (TPS) con señal baja. Revisa el conector, " +
                        "la alimentación de 5 V y la masa antes de cambiar el sensor.",
                ),
                DtcCodeUi(DtcCode("P0122", DtcMode.Pending), explanation = null),
            ),
        ),
        freezeFrame = listOf(
            FreezeFrameItem("DTC que lo guardó", "P0122"),
            FreezeFrameItem("Temperatura del refrigerante", "32 °C"),
            FreezeFrameItem("Posición absoluta del acelerador", "2,4 %"),
        ),
        estadoMil = "Testigo de falla (MIL) encendido · la ECU reporta 1 código(s) confirmados",
    )

    @Test
    fun statusContent() = MatrizCaptura.componente("DtcStatusContent") {
        StatusContent("Sin códigos activos, pendientes ni permanentes", NivelEstado.OK)
    }

    @Test
    fun dtcContentConCodigos() = MatrizCaptura.pantalla("DtcContent", altoMinimoDp = 1100) {
        DtcContent(conCodigos, acciones)
    }

    @Test
    fun dtcContentSinLectura() = MatrizCaptura.pantalla("DtcContent_sinLectura") {
        DtcContent(DtcPantallaUi(DtcUiState.Idle), acciones)
    }

    @Test
    fun dtcContentError() = MatrizCaptura.pantalla("DtcContent_error") {
        DtcContent(DtcPantallaUi(DtcUiState.Error("Conecta el adaptador primero")), acciones)
    }

    @Test
    fun dtcContentBorradoConFallaPresente() = MatrizCaptura.pantalla("DtcContent_borrado") {
        DtcContent(
            DtcPantallaUi(DtcUiState.Borrado(ResultadoBorradoUi(listOf("P0122"), listOf("P0122"), rechazadoPorEcu = false))),
            acciones,
        )
    }

    @Test
    fun confirmarBorrarDtcDialog() = MatrizCaptura.dialogo("ConfirmarBorrarDtcDialog") {
        ConfirmarBorrarDtcDialog(
            ConfirmacionBorradoDtc(listOf("P0122"), RechazoBorradoDtc.SinVelocidadReciente),
            onConfirmar = {},
            onCancelar = {},
        )
    }

    @Test
    fun confirmarBorrarDtcDialogEnMovimiento() = MatrizCaptura.dialogo("ConfirmarBorrarDtcDialog_enMovimiento") {
        ConfirmarBorrarDtcDialog(
            ConfirmacionBorradoDtc(listOf("P0300", "P0171"), RechazoBorradoDtc.EnMovimiento(35)),
            onConfirmar = {},
            onCancelar = {},
        )
    }
}
