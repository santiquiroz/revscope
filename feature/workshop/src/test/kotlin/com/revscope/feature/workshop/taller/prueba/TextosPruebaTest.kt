package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.pruebas.AnalizadorBateria
import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ModoPaso
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextosPruebaTest {

    private val en = XmlStringResolver.en(
        com.revscope.core.obd.R::class.java,
        com.revscope.feature.workshop.R::class.java,
        com.revscope.core.designsystem.R::class.java,
        resDirs = listOf("src/main/res", "../../core/obd/src/main/res", "../../core/designsystem/src/main/res"),
    )

    @Test
    fun `la descripcion del minimo sale en ingles`() {
        assertEquals(
            "With the engine running in neutral, record 45 s of idle without touching the throttle, then rev to about " +
                "3,000 rpm three times and release sharply: it measures whether the idle holds, how far it drops on " +
                "release and whether the engine stalls.",
            TextosPrueba.descripcion(TipoPrueba.MINIMO_RETORNO, en),
        )
    }

    @Test
    fun `el paso de accion de la preparacion sale en ingles con el tope en segundos`() {
        val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles, en)
        val fase = pantalla.fase(
            EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.ARRANQUE_FRIO, PruebasMotorBenelli.precondicionesTibio),
        ) as FasePantalla.Preparacion

        val paso = fase.pasos[1].plano()
        assertTrue(paso, paso.endsWith(": records until the event is detected or you tap Finish (max 15 s)"))
    }

    @Test
    fun `subtexto y accion principal en ingles y en espanol`() {
        val es = resolverPrueba()
        val accion = ModoPaso.Accion(15_000)

        assertEquals("Ends on its own when detected; otherwise, tap Finish.", TextosPrueba.subtexto(FasePaso.GRABANDO, accion, en))
        assertEquals("Termina solo al detectarlo; si no, toca «Terminar».", TextosPrueba.subtexto(FasePaso.GRABANDO, accion, es))
        assertEquals("Finish", TextosPrueba.accionPrincipal(FasePaso.GRABANDO, accion, en))
        assertEquals("Holding", TextosPrueba.estadoFase(FasePaso.SOSTENIENDO, en))
    }

    @Test
    fun `los hz usan el separador decimal del idioma`() {
        assertEquals("12.5 Hz", TextosPrueba.hz(12.5, en))
        assertEquals("12,5 Hz", TextosPrueba.hz(12.5, resolverPrueba()))
    }

    @Test
    fun `el voltaje en vivo nombra su fuente en ingles`() {
        val vivo = MapeoMagnitud.valorVivo(AnalizadorBateria.PID_VOLTAJE, 12.64, en)

        assertEquals("Battery voltage · AT RV at the connector", vivo.etiqueta)
        assertEquals("12.6 V", vivo.principal.plano())
    }
}
