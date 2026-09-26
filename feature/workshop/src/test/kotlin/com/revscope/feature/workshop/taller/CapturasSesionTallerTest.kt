package com.revscope.feature.workshop.taller

import com.revscope.core.designsystem.SelectorVehiculo
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.VehiculoEnEncabezado
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasSesionTallerTest {

    private val selector = VehiculoEnEncabezado("Benelli TNT 150i", esMoto = true, estadoEnlace = "conectado", colorEstado = RevScopeColors.Success)

    private fun hub(abierta: Boolean, conexion: ConnectionState) =
        ResumenHub.de(CasoBenelli.datosHub(abierta), conexion, verTodas = false, zona = BOGOTA)

    @Test
    fun hubConSesionConectado() = MatrizCaptura.pantalla("TallerHubContent_sesion_conectado", altoMinimoDp = 4400) {
        TallerHubContent(hub(abierta = true, CasoBenelli.conectado), AccionesHub()) { SelectorVehiculo(selector, onClick = {}) }
    }

    @Test
    fun hubSinSesionSinAdaptador() = MatrizCaptura.pantalla("TallerHubContent_sinSesion_sinAdaptador", altoMinimoDp = 4400) {
        TallerHubContent(hub(abierta = false, ConnectionState.Disconnected), AccionesHub()) {
            SelectorVehiculo(selector.copy(estadoEnlace = "desconectado", colorEstado = RevScopeColors.TextSecondary), onClick = {})
        }
    }

    @Test
    fun nuevaSesionConSeisSintomas() = MatrizCaptura.pantalla("NuevaSesionContent", altoMinimoDp = 2600) {
        NuevaSesionContent(
            NuevaSesionEstado(
                cargando = false,
                vehiculo = "Benelli TNT 150i",
                sintomas = setOf(
                    Sintoma.ARRANQUE_DIFICIL_FRIO, Sintoma.NO_SOSTIENE_MINIMO_FRIO, Sintoma.MINIMO_INESTABLE,
                    Sintoma.SE_APAGA_AL_SOLTAR, Sintoma.SE_AHOGA_AL_ACELERAR, Sintoma.MIL_ENCENDIDA,
                ),
                sintomasTexto = "El TPS se cambió hace unos meses por uno genérico",
                odometro = "18420",
                odometroDeEcu = true,
                bases = listOf(
                    OpcionChequeoBase(3, "11 jul 2026, 18:00", "Sin códigos"),
                    OpcionChequeoBase(2, "2 jun 2026, 09:15", "3 hallazgos"),
                ),
                baseElegida = 3,
            ),
            AccionesNuevaSesion(),
        )
    }

    @Test
    fun sesionCasoBenelli() = MatrizCaptura.pantalla("SesionTallerContent_benelli", altoMinimoDp = 7000) {
        SesionTallerContent(CasoBenelli.estadoSesion(), AccionesSesion())
    }

    @Test
    fun sesionCerradaSinEventos() = MatrizCaptura.pantalla("SesionTallerContent_cerradaVacia") {
        SesionTallerContent(
            CasoBenelli.estadoSesion().copy(
                abierta = false,
                estadoTexto = "Cerrada el 25 sep 2026, 21:10",
                eventos = emptyList(),
                sugeridas = emptyList(),
                comparacion = TarjetaComparacion.SinChequeoAhora("11 jul 2026"),
                agregar = DisponibilidadAgregar.SesionCerrada,
            ),
            AccionesSesion(),
        )
    }

    @Test
    fun hojaAgregarSinAdaptador() = MatrizCaptura.componente("HojaAgregarContent_sinAdaptador", altoMinimoDp = 1200) {
        HojaAgregarContent(OpcionesAgregar.de(AccionesSesion()), conectado = false)
    }
}
