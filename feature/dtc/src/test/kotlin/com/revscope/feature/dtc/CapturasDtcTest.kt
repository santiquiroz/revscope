package com.revscope.feature.dtc

import com.revscope.core.designsystem.NivelEstado
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.taller.dtc.BaseConocimientoDtc
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasDtcTest {

    private val acciones = AccionesDtc(
        onVolver = {},
        onLeer = {},
        onBorrar = {},
        onOpenAiValue = {},
        onPrueba = {},
        onMultimetro = {},
    )

    // La guía real del asset: la captura muestra el texto que verá la persona, no un texto de relleno.
    private val guias = BaseConocimientoDtc { File(RUTA_GUIA_REAL).readText() }

    private fun codigos(vararg codes: DtcCode) = agruparPorCodigo(codes.toList(), guias::guia)

    private val freezeFrame = FreezeFrameUi(
        causante = "P0122",
        items = listOf(
            FreezeFrameItem("Temperatura del refrigerante", "32 °C"),
            FreezeFrameItem("Posición absoluta del acelerador", "2,4 %"),
            FreezeFrameItem("RPM del motor", "1454 rpm"),
        ),
    )

    private val milEncendida = MilUi(encendida = true, texto = "Testigo de falla encendido · la ECU reporta 1 código confirmado")

    private val p0122ConGuia = DtcPantallaUi(
        estado = DtcUiState.HasCodes(codigos(DtcCode("P0122", DtcMode.Active), DtcCode("P0122", DtcMode.Pending))),
        detalle = DetalleDtc(
            mil = milEncendida,
            freezeFrame = freezeFrame,
            sesion = SesionDtcUi.Abierta("Se apaga al soltar el acelerador", setOf("P0122#1", "P0122#2")),
            lecturaEnSesion = true,
            guiasAbiertas = setOf("P0122"),
        ),
        vehiculo = "Benelli TNT 150i",
    )

    private val enMovimientoSinSesion = DtcPantallaUi(
        estado = DtcUiState.HasCodes(
            codigos(DtcCode("P0122", DtcMode.Active), DtcCode("P1234", DtcMode.Active)).mapIndexed { i, codigo ->
                if (i == 0) {
                    codigo.copy(
                        explicacion = ExplicacionIa.Lista(
                            "El TPS entrega menos voltaje del esperado. Revisa conector, referencia de 5 V y masa " +
                                "antes de cambiar el sensor.",
                            faltaConfigurar = false,
                        ),
                    )
                } else {
                    codigo
                }
            },
        ),
        detalle = DetalleDtc(
            mil = milEncendida.copy(texto = "Testigo de falla encendido · la ECU reporta 2 códigos confirmados"),
            bloqueoBorrado = "El vehículo va a 35 km/h. Detente antes de borrar los códigos.",
        ),
        vehiculo = "Benelli TNT 150i",
    )

    @Test
    fun statusContent() = MatrizCaptura.componente("DtcStatusContent") {
        StatusContent("Sin códigos activos, pendientes ni permanentes", NivelEstado.OK)
    }

    @Test
    fun centroP0122ConGuiaDesplegada() = MatrizCaptura.pantalla("DtcCenterContent_P0122", altoMinimoDp = 4200) {
        DtcCenterContent(p0122ConGuia, acciones)
    }

    // Con la guía plegada el freeze frame y la sesión entran enteros también con letra al 200 %.
    @Test
    fun centroP0122ConFreezeFrame() = MatrizCaptura.pantalla("DtcCenterContent_P0122_freezeFrame", altoMinimoDp = 2600) {
        DtcCenterContent(p0122ConGuia.copy(detalle = p0122ConGuia.detalle.copy(guiasAbiertas = emptySet())), acciones)
    }

    @Test
    fun centroEnMovimientoSinSesion() = MatrizCaptura.pantalla("DtcCenterContent_enMovimiento", altoMinimoDp = 1500) {
        DtcCenterContent(enMovimientoSinSesion, acciones)
    }

    @Test
    fun centroSinCodigos() = MatrizCaptura.pantalla("DtcCenterContent_sinCodigos", altoMinimoDp = 900) {
        DtcCenterContent(
            DtcPantallaUi(
                estado = DtcUiState.HasCodes(emptyList()),
                detalle = DetalleDtc(mil = MilUi(encendida = false, texto = "Testigo de falla apagado · la ECU reporta 0 códigos confirmados")),
            ),
            acciones,
        )
    }

    @Test
    fun centroSinLectura() = MatrizCaptura.pantalla("DtcCenterContent_sinLectura") {
        DtcCenterContent(DtcPantallaUi(DtcUiState.Idle), acciones)
    }

    @Test
    fun centroError() = MatrizCaptura.pantalla("DtcCenterContent_error") {
        DtcCenterContent(DtcPantallaUi(DtcUiState.Error("Conecta el adaptador primero")), acciones)
    }

    @Test
    fun centroBorradoRechazadoPorLaEcu() = MatrizCaptura.pantalla("DtcCenterContent_rechazo7F", altoMinimoDp = 900) {
        DtcCenterContent(
            DtcPantallaUi(DtcUiState.Borrado(ResultadoBorradoUi(listOf("P0122"), listOf("P0122"), rechazadoPorEcu = true))),
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
    fun confirmarBorrarDtcDialogConSesion() = MatrizCaptura.dialogo("ConfirmarBorrarDtcDialog_conSesion") {
        ConfirmarBorrarDtcDialog(
            ConfirmacionBorradoDtc(listOf("P0122"), rechazo = null, freezeFrameEnSesion = true),
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

    private companion object {
        // Gradle corre los tests con el directorio del módulo como directorio de trabajo.
        const val RUTA_GUIA_REAL = "../../core/obd/src/main/assets/taller/dtc_guia_es.json"
    }
}
