package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
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
class CapturasPruebaGuiadaTest {

    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)

    private fun ui(entrada: EntradaPantalla) = PruebaGuiadaUi(fase = pantalla.fase(entrada), vehiculo = "Benelli TNT 150i")

    @Test
    fun precondicionesConUnaFallando() = MatrizCaptura.pantalla("PruebaGuiadaContent_precondiciones", altoMinimoDp = 2100) {
        PruebaGuiadaContent(
            ui(EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.TPS_BARRIDO, BarridoBenelli.precondicionesUnaFallando)),
            AccionesPrueba(),
        )
    }

    @Test
    fun paso2de5SosteniendoConCuenta3() = MatrizCaptura.pantalla("PruebaGuiadaContent_paso2_cuenta3", altoMinimoDp = 1100) {
        PruebaGuiadaContent(
            ui(
                EntradaPantalla(
                    BarridoBenelli.enPaso(1, FasePaso.SOSTENIENDO, 2_400),
                    serieVivo = BarridoBenelli.serieMedio(),
                    bandas = BarridoBenelli.bandas,
                ),
            ),
            AccionesPrueba(),
        )
    }

    @Test
    fun resultadoCasoBenelli() = MatrizCaptura.pantalla("PruebaGuiadaContent_resultado_benelli", altoMinimoDp = 4800) {
        PruebaGuiadaContent(ui(EntradaPantalla(BarridoBenelli.terminada())), AccionesPrueba(onVerGuia = {}))
    }
}
