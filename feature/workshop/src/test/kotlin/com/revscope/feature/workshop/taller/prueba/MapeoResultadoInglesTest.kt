package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapeoResultadoInglesTest {

    private val ingles = XmlStringResolver.en(
        com.revscope.core.obd.R::class.java,
        com.revscope.feature.workshop.R::class.java,
        com.revscope.core.designsystem.R::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res", "../../core/designsystem/src/main/res"),
    )
    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles, ingles)

    private fun resultado() = (pantalla.fase(EntradaPantalla(BarridoBenelli.terminada())) as FasePantalla.Resultado).resultado

    @Test
    fun `la serie del barrido se describe en inglés con los pasos sombreados en plural`() {
        val grafica = resultado().graficas.single()

        assertEquals("Whole test, in volts", grafica.titulo)
        val descripcion = grafica.modelo.descripcion.plano()
        assertTrue(descripcion, descripcion.startsWith("TPS over the whole test: 0.12 V to 0.90 V, 5 shaded steps."))
    }

    @Test
    fun `la pesa sin banda fija y la referencia salen en inglés`() {
        val r = resultado()

        assertTrue(r.pesas[1].descripcion, r.pesas[1].descripcion.endsWith("no fixed range: it should fall between closed and wide open"))
        assertTrue(r.referencia, r.referencia.plano().startsWith("Reference 5.00 V · "))
    }

    @Test
    fun `los pasos sombreados usan el plural de cada idioma`() {
        assertEquals("1 shaded step", MapeoResultado.pasosSombreados(1, ingles))
        assertEquals("1 paso sombreado", MapeoResultado.pasosSombreados(1, resolverPrueba()))
        assertEquals("3 pasos sombreados", MapeoResultado.pasosSombreados(3, resolverPrueba()))
    }
}
