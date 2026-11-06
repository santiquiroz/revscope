package com.revscope.feature.dtc

import org.junit.Assert.assertEquals
import org.junit.Test

class CentroDtcEstadoTest {

    private val resolver = com.revscope.core.uitesting.XmlStringResolver.es(
        com.revscope.feature.dtc.R::class.java,
        com.revscope.core.obd.R::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res"),
    )

    @Test
    fun `un codigo del fabricante sin guia se explica una sola vez sin inventar descripcion`() {
        assertEquals(
            "Código del fabricante: consulta el manual del modelo. RevScope no inventa su descripción.",
            textoSinGuia("P1234", resolver),
        )
    }

    @Test
    fun `un codigo generico sin guia da su estructura SAE y dice donde buscar la descripcion`() {
        assertEquals(
            "Código genérico definido por SAE · tren motriz (motor y transmisión) · subsistema: transmisión. " +
                "No está en la guía local de RevScope: busca la descripción exacta en SAE J2012 " +
                "o en el manual de servicio del modelo.",
            textoSinGuia("P0700", resolver),
        )
    }

    @Test
    fun `un texto que no es un codigo se reconoce como formato desconocido`() {
        assertEquals("Código con un formato que RevScope no reconoce.", textoSinGuia("XYZ", resolver))
    }
}
