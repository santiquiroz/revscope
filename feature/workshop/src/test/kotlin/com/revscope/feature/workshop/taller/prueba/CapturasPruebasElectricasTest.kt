package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.pruebas.CalibracionVoltaje
import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.DesfaseVoltaje
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasPruebasElectricasTest {

    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)
    private val calibrado = DesfaseVoltaje.medido(multimetroV = 12.58, adaptadorV = 12.4)

    private fun ui(entrada: EntradaPantalla, desfase: DesfaseVoltaje = DesfaseVoltaje.SIN_CALIBRAR) =
        PruebaGuiadaUi(fase = pantalla.fase(entrada), vehiculo = "Benelli TNT 150i", desfase = desfase, voltajeAdaptador = 12.4)

    @Test
    fun preparacionBateria() = MatrizCaptura.pantalla("PruebaGuiadaContent_bateria_preparacion", altoMinimoDp = 1900) {
        PruebaGuiadaContent(
            ui(EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.BATERIA_CARGA, PruebasElectricasBenelli.precondicionesBateria), calibrado),
            AccionesPrueba(),
        )
    }

    // El cuerpo del diálogo, fuera de su ventana: con el TextField enfocado la captura del diálogo entero no termina
    // en Robolectric al 200 %. Aquí se ve si el texto, el campo con el desfase calculado y «Quitar» caben sin cortes.
    @Test
    fun cuerpoCalibrar() = MatrizCaptura.componente("CuerpoDesfase_calibrando") {
        CuerpoDesfase(
            texto = "12,58",
            adaptadorV = 12.4,
            nuevo = CalibracionVoltaje.desde(12.58, 12.4),
            onTexto = {},
            onQuitar = {},
        )
    }

    @Test
    fun bateriaArrancando() = MatrizCaptura.pantalla("PruebaGuiadaContent_bateria_arranque", altoMinimoDp = 1100) {
        PruebaGuiadaContent(
            ui(EntradaPantalla(PruebasElectricasBenelli.enArranque(), serieVivo = PruebasElectricasBenelli.serieArranque(), bandas = PruebasElectricasBenelli.bandas)),
            AccionesPrueba(),
        )
    }

    @Test
    fun resultadoBateriaBenelli() = MatrizCaptura.pantalla("PruebaGuiadaContent_bateria_resultado_benelli", altoMinimoDp = 3600) {
        PruebaGuiadaContent(ui(EntradaPantalla(PruebasElectricasBenelli.bateriaTerminada())), AccionesPrueba(onVerGuia = {}))
    }

    @Test
    fun resultadoBateriaReinicio() = MatrizCaptura.pantalla("PruebaGuiadaContent_bateria_resultado_reinicio", altoMinimoDp = 3200) {
        PruebaGuiadaContent(ui(EntradaPantalla(PruebasElectricasBenelli.bateriaConReinicio())), AccionesPrueba(onVerGuia = {}))
    }

    @Test
    fun preparacionMapSinBaro() = MatrizCaptura.pantalla("PruebaGuiadaContent_map_preparacion_sin_pid33", altoMinimoDp = 1700) {
        PruebaGuiadaContent(
            ui(EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.MAP_BARO, PruebasElectricasBenelli.precondicionesMapSinBaro)),
            AccionesPrueba(),
        )
    }

    @Test
    fun resultadoMapEstimada() = MatrizCaptura.pantalla("PruebaGuiadaContent_map_resultado_estimada", altoMinimoDp = 3200) {
        PruebaGuiadaContent(ui(EntradaPantalla(PruebasElectricasBenelli.mapTerminado())), AccionesPrueba(onVerGuia = {}))
    }
}
