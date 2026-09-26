package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
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
class CapturasPruebasMotorTest {

    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)

    private fun ui(entrada: EntradaPantalla) = PruebaGuiadaUi(fase = pantalla.fase(entrada), vehiculo = "Benelli TNT 150i")

    @Test
    fun minimoGrabando() = MatrizCaptura.pantalla("PruebaGuiadaContent_minimo_paso1", altoMinimoDp = 1100) {
        PruebaGuiadaContent(
            ui(EntradaPantalla(PruebasMotorBenelli.enMinimo(), serieVivo = PruebasMotorBenelli.serieMinimo(), bandas = PruebasMotorBenelli.bandas)),
            AccionesPrueba(),
        )
    }

    @Test
    fun resultadoMinimoBenelli() = MatrizCaptura.pantalla("PruebaGuiadaContent_minimo_resultado_benelli", altoMinimoDp = 4200) {
        PruebaGuiadaContent(ui(EntradaPantalla(PruebasMotorBenelli.minimoTerminado())), AccionesPrueba(onVerGuia = {}))
    }

    @Test
    fun preparacionArranqueTibio() = MatrizCaptura.pantalla("PruebaGuiadaContent_arranque_preparacion_tibio", altoMinimoDp = 1900) {
        PruebaGuiadaContent(
            ui(EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.ARRANQUE_FRIO, PruebasMotorBenelli.precondicionesTibio)),
            AccionesPrueba(),
        )
    }

    @Test
    fun arranqueCalentando() = MatrizCaptura.pantalla("PruebaGuiadaContent_arranque_calentamiento", altoMinimoDp = 1100) {
        PruebaGuiadaContent(
            ui(EntradaPantalla(PruebasMotorBenelli.enCalentamiento(), serieVivo = PruebasMotorBenelli.serieCalentamiento())),
            AccionesPrueba(),
        )
    }

    @Test
    fun resultadoArranqueTibio() = MatrizCaptura.pantalla("PruebaGuiadaContent_arranque_resultado_tibio", altoMinimoDp = 4200) {
        PruebaGuiadaContent(ui(EntradaPantalla(PruebasMotorBenelli.arranqueTerminado())), AccionesPrueba(onVerGuia = {}))
    }
}
