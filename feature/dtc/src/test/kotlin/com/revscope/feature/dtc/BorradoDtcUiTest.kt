package com.revscope.feature.dtc

import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.protocol.DtcServicio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BorradoDtcUiTest {

    private val limpio = ResultadoBorradoUi(antes = listOf("P0122"), despues = emptyList(), rechazadoPorEcu = false)
    private val resolverEs = com.revscope.core.uitesting.XmlStringResolver.es(
        com.revscope.feature.dtc.R::class.java,
        resDirs = listOf("src/main/res"),
    )
    private val resolverEn = com.revscope.core.uitesting.XmlStringResolver.en(
        com.revscope.feature.dtc.R::class.java,
        resDirs = listOf("src/main/res"),
    )

    @Test
    fun `sin relectura valida no afirma que no quedan codigos`() {
        assertEquals(
            "Se envió el borrado, pero no se pudo releer la ECU para confirmarlo. Vuelve a leer los códigos.",
            textoResultadoBorrado(limpio.copy(despuesLeido = false), resolverEs),
        )
    }

    @Test
    fun `si el borrado no se pudo enviar lo dice`() {
        assertEquals(
            "No se pudo enviar el borrado: el adaptador no respondió. Vuelve a leer los códigos antes de intentarlo otra vez.",
            textoResultadoBorrado(limpio.copy(enviado = false), resolverEs),
        )
    }

    @Test
    fun `una lectura previa fallida no se presenta como sin codigos`() {
        assertEquals(
            "Códigos borrados. Antes: no se pudieron leer. Después: sin códigos activos.",
            textoResultadoBorrado(limpio.copy(antes = emptyList(), antesLeido = false), resolverEs),
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

    @Test
    fun `el resumen de borrado se localiza al ingles`() {
        assertEquals(
            "Codes cleared. Before: P0122. After: no active codes.",
            textoResultadoBorrado(limpio, resolverEn),
        )
    }
}
