package com.revscope.feature.dtc

import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.protocol.DtcServicio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BorradoDtcUiTest {

    private val limpio = ResultadoBorradoUi(antes = listOf("P0122"), despues = emptyList(), rechazadoPorEcu = false)

    @Test
    fun `sin relectura valida no afirma que no quedan codigos`() {
        assertEquals(
            "Se envió el borrado, pero no se pudo releer la ECU para confirmarlo. Vuelve a leer los códigos.",
            textoResultadoBorrado(limpio.copy(despuesLeido = false)),
        )
    }

    @Test
    fun `si el borrado no se pudo enviar lo dice`() {
        assertEquals(
            "No se pudo enviar el borrado: el adaptador no respondió. Vuelve a leer los códigos antes de intentarlo otra vez.",
            textoResultadoBorrado(limpio.copy(enviado = false)),
        )
    }

    @Test
    fun `una lectura previa fallida no se presenta como sin codigos`() {
        assertEquals(
            "Códigos borrados. Antes: no se pudieron leer. Después: sin códigos activos.",
            textoResultadoBorrado(limpio.copy(antes = emptyList(), antesLeido = false)),
        )
    }

    @Test
    fun `el resultado toma la validez de las dos lecturas y del envio`() {
        val fallida = DtcScan(
            activos = emptyList(),
            pendientes = emptyList(),
            permanentes = emptyList(),
            milEncendida = null,
            conteoSegunEcu = null,
            freezeFrame = null,
            crudo = emptyMap(),
            errores = listOf("03: sin respuesta del adaptador"),
            enlacePerdido = true,
            serviciosFallidos = setOf(DtcServicio.ACTIVOS),
        )

        val ui = resultadoBorradoUi(BorradoDtc(null, rechazadoPorCondiciones = false, antes = fallida, despues = fallida))

        assertFalse(ui.enviado)
        assertFalse(ui.antesLeido)
        assertFalse(ui.despuesLeido)
    }
}
