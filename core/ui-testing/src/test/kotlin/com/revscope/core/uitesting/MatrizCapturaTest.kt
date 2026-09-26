package com.revscope.core.uitesting

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrizCapturaTest {

    @Test
    fun `la matriz cubre dos telefonos por tres escalas de letra`() {
        val sufijos = variantesCaptura().map { it.sufijo }

        assertEquals(
            listOf("w360_fs100", "w360_fs130", "w360_fs200", "w412_fs100", "w412_fs130", "w412_fs200"),
            sufijos,
        )
    }

    @Test
    fun `la ruta queda en src test screenshots con componente y variante`() {
        val variante = VarianteCaptura(DispositivoCaptura.GRANDE, 1.3f)

        assertEquals("src/test/screenshots/Barra_w412_fs130.png", rutaCaptura("Barra", variante))
    }

    @Test
    fun `el alto minimo alarga la ventana sin tocar ancho ni densidad`() {
        assertEquals("w360dp-h640dp-xhdpi", DispositivoCaptura.PEQUENO.calificadores())
        assertEquals("w412dp-h1800dp-xxhdpi", DispositivoCaptura.GRANDE.calificadores(altoMinimoDp = 1800))
        assertEquals("w412dp-h915dp-xxhdpi", DispositivoCaptura.GRANDE.calificadores(altoMinimoDp = 500))
    }
}
