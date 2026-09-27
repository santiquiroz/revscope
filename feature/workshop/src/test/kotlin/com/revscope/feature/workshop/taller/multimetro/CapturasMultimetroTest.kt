package com.revscope.feature.workshop.taller.multimetro

import com.revscope.core.obd.taller.multimetro.CondicionesMultimetro
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasMultimetroTest {

    @Test
    fun tpsDelModeloConElCaso() = MatrizCaptura.pantalla("MultimetroContent_tps_benelli", altoMinimoDp = 5600) {
        MultimetroContent(MultimetroBenelli.ui(), AccionesMultimetro())
    }

    @Test
    fun tpsGenericaSinMedidas() = MatrizCaptura.pantalla("MultimetroContent_tps_generica_vacia", altoMinimoDp = 3600) {
        MultimetroContent(
            MultimetroBenelli.ui(textos = emptyMap(), contexto = MultimetroBenelli.contexto(conModelo = false, conSesion = false)),
            AccionesMultimetro(),
        )
    }

    @Test
    fun bateriaConValleBajo() = MatrizCaptura.pantalla("MultimetroContent_bateria", altoMinimoDp = 2600) {
        MultimetroContent(
            MultimetroBenelli.ui(
                textos = mapOf(
                    (FuncionCable.ALIMENTACION_12V to CondicionesMultimetro.CONTACTO) to "12,2",
                    (FuncionCable.ALIMENTACION_12V to CondicionesMultimetro.ARRANQUE) to "8,9",
                    (FuncionCable.ALIMENTACION_12V to CondicionesMultimetro.CARGA) to "14,2x",
                ),
                contexto = MultimetroBenelli.contexto(SensorMultimetro.BATERIA),
            ),
            AccionesMultimetro(),
        )
    }
}
