package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.sesion.Veredicto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantallaPruebasMotorTest {

    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)

    private fun paso(e: EstadoPrueba.EnPaso, serie: SerieVivo) =
        (pantalla.fase(EntradaPantalla(e, serieVivo = serie, bandas = PruebasMotorBenelli.bandas)) as FasePantalla.Paso).paso

    private fun resultado(t: EstadoPrueba.Terminada) = (pantalla.fase(EntradaPantalla(t)) as FasePantalla.Resultado).resultado

    @Test
    fun `el paso del mínimo muestra las RPM en vivo con la banda típica del mínimo sombreada`() {
        val p = paso(PruebasMotorBenelli.enMinimo(), PruebasMotorBenelli.serieMinimo())

        assertEquals(1 to 4, p.indice to p.total)
        assertEquals("RPM · PID 0C", p.vivo!!.etiqueta.plano())
        assertEquals("1463 rpm", p.vivo!!.principal.plano())
        assertNull(p.vivo!!.secundario)
        assertEquals("Últimos 10 s, en rpm", p.tituloGrafica)
        assertEquals(listOf("Mínimo típico"), p.grafica.bandas.map { it.etiqueta })
        assertEquals(listOf("Mínimo: 1200–1700 rpm · Típico (editable)"), p.leyendaGrafica.map { it.plano() })
        assertTrue(p.grafica.descripcion, p.grafica.descripcion.plano().startsWith("RPM (PID 0C) en los últimos 10 s: de 1452 rpm a 1572 rpm, bajando"))
        assertNull(p.accionPrincipal)
    }

    @Test
    fun `el calentamiento muestra la temperatura del motor y la acción Terminar`() {
        val p = paso(PruebasMotorBenelli.enCalentamiento(), PruebasMotorBenelli.serieCalentamiento())

        assertEquals("41 °C", p.vivo!!.principal.plano())
        assertEquals("Últimos 10 s, en °C", p.tituloGrafica)
        assertEquals(TextosPrueba.ACCION_TERMINAR, p.accionPrincipal)
        assertTrue(p.grafica.bandas.isEmpty())
        // 40-41 °C no se estira a toda la altura: la escala abarca al menos 10 °C.
        assertEquals(10.0, p.grafica.rangoY!!.endInclusive - p.grafica.rangoY!!.start, 1e-9)
    }

    @Test
    fun `la preparación del arranque marca el motor tibio como aviso y no pide la referencia de voltaje`() {
        val fase = pantalla.fase(
            EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.ARRANQUE_FRIO, PruebasMotorBenelli.precondicionesTibio),
        ) as FasePantalla.Preparacion

        assertTrue(fase.listas)
        assertFalse(fase.usaVref)
        assertEquals(1, fase.precondiciones.count { it.aviso })
        assertEquals("Arranque: graba hasta detectar el evento o hasta que toques «Terminar» (máx. 15 s)", fase.pasos[1].plano())
    }

    @Test
    fun `el mínimo sí usa la referencia porque compara el TPS de ralentí en voltios`() {
        val fase = pantalla.fase(EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.MINIMO_RETORNO, emptyList())) as FasePantalla.Preparacion

        assertTrue(fase.usaVref)
    }

    @Test
    fun `el resultado del mínimo de la Benelli trae medidas, comprobaciones con banda y la serie de RPM`() {
        val r = resultado(PruebasMotorBenelli.minimoTerminado())

        assertEquals(Veredicto.FALLA, r.veredicto)
        assertEquals("Se apagó 2 de 3 veces al soltar el acelerador", r.titulo.plano())
        assertTrue(r.medidas.toString(), "Deriva: -61,8 rpm/s (de 2010 a 1454 rpm)" in r.medidas.map { it.plano() })
        assertTrue(r.comprobaciones.any { !it.cumple && it.texto.plano() == "TPS en ralentí: 0,15 V · banda 0,3–1,0 V, Típico (editable)" })
        assertTrue(r.comprobaciones.any { !it.cumple && it.texto.plano() == "Deriva del mínimo (sin signo): 61,8 rpm/s · banda ≤ 10 rpm/s, Típico (editable)" })
        val g = r.graficas.single()
        assertEquals("RPM en toda la prueba", g.titulo)
        assertEquals(4, g.modelo.tramos.size)
        assertEquals("1 Mínimo · 2 Retorno 1 · 3 Retorno 2 · 4 Retorno 3", g.leyenda.first().plano())
        assertTrue(r.pesas.isEmpty())
        assertNull(r.codigoGuia)
    }

    @Test
    fun `el resultado del arranque tibio trae la serie de RPM y la de temperatura`() {
        val r = resultado(PruebasMotorBenelli.arranqueTerminado())

        assertTrue(r.titulo, r.titulo.plano().startsWith("Arranque tibio: arrancó al 2.º intento"))
        assertEquals(Veredicto.ATENCION, r.veredicto)
        assertEquals(listOf("RPM en toda la prueba", "Temperatura del motor en toda la prueba, en °C"), r.graficas.map { it.titulo })
        assertTrue(r.medidas.toString(), "En contacto: motor 32 °C, aire 27 °C · tibio" in r.medidas.map { it.plano() })
        assertNull(r.codigoGuia)
    }
}
