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

class PantallaPruebasElectricasTest {

    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)

    private fun paso(e: EstadoPrueba.EnPaso, serie: SerieVivo) =
        (pantalla.fase(EntradaPantalla(e, serieVivo = serie, bandas = PruebasElectricasBenelli.bandas)) as FasePantalla.Paso).paso

    private fun resultado(t: EstadoPrueba.Terminada) = (pantalla.fase(EntradaPantalla(t)) as FasePantalla.Resultado).resultado

    @Test
    fun `la lista ofrece las cinco pruebas, todas disponibles`() {
        val fase = pantalla.fase(EntradaPantalla(EstadoPrueba.Inactiva)) as FasePantalla.Elegir

        assertEquals(TipoPrueba.entries.toList(), fase.opciones.map { it.tipo })
        assertTrue(fase.opciones.all { it.disponible })
    }

    @Test
    fun `la preparación de la batería pide el desfase, no la referencia de 5 V, y avisa de la farola`() {
        val fase = pantalla.fase(
            EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.BATERIA_CARGA, PruebasElectricasBenelli.precondicionesBateria),
        ) as FasePantalla.Preparacion

        assertTrue(fase.listas)
        assertTrue(fase.usaDesfase)
        assertFalse(fase.usaVref)
        assertTrue(fase.precondiciones.single { it.aviso }.texto.contains("farola"))
        assertEquals(
            listOf(
                "Contacto: se graba solo 10 s",
                "Arranque: se graba solo 8 s",
                "Mínimo: se graba solo 15 s",
                "Rpm altas: sostener 10 s",
            ),
            fase.pasos.map { it.plano() },
        )
    }

    @Test
    fun `en el arranque de la batería muestra el voltaje de AT RV en vivo y la banda del valle en la leyenda`() {
        val p = paso(PruebasElectricasBenelli.enArranque(), PruebasElectricasBenelli.serieArranque())

        assertEquals(2 to 4, p.indice to p.total)
        assertEquals("Voltaje de la batería · AT RV en el conector", p.vivo!!.etiqueta)
        assertEquals("13,4 V", p.vivo!!.principal.plano())
        assertEquals("Últimos 10 s, en V", p.tituloGrafica)
        assertTrue("una banda de un solo lado no se sombrea", p.grafica.bandas.isEmpty())
        assertEquals(listOf("Valle al arrancar: ≥ 9,6 V · Típico (editable)"), p.leyendaGrafica.map { it.plano() })
        assertTrue(p.grafica.descripcion, p.grafica.descripcion.plano().startsWith("Voltaje de la batería (AT RV en el conector) en los últimos 10 s: de 10,6 V a 13,4 V"))
    }

    @Test
    fun `en el mínimo de la batería la banda típica de carga se sombrea`() {
        val p = paso(PruebasElectricasBenelli.enMinimo(), PruebasElectricasBenelli.serieMinimo())

        assertEquals(listOf("Carga típica"), p.grafica.bandas.map { it.etiqueta })
        assertEquals(listOf("Carga: 13,5–14,5 V · Típico (editable)"), p.leyendaGrafica.map { it.plano() })
    }

    @Test
    fun `el resultado de la batería de la Benelli trae medidas, comprobaciones y la serie con la banda de carga`() {
        val r = resultado(PruebasElectricasBenelli.bateriaTerminada())

        assertEquals(Veredicto.OK, r.veredicto)
        assertEquals("Contacto: 12,2 V (de 12,2 a 12,3 V)", r.medidas.first().plano())
        assertTrue(r.medidas.toString(), r.medidas.any { it.plano().startsWith("Rpm altas: 14,2 V") })
        assertTrue(r.comprobaciones.any { it.texto.plano() == "Carga a rpm altas: 14,20 V · banda 13,5–14,5 V, Típico (editable)" && it.cumple })
        val serie = r.graficas.single()
        assertEquals("Voltaje de la batería en toda la prueba, en V", serie.titulo)
        assertEquals(listOf("Carga típica"), serie.modelo.bandas.map { it.etiqueta })
        assertEquals("1 Contacto · 2 Arranque · 3 Mínimo · 4 Rpm altas", serie.leyenda.first().plano())
        assertNull(r.codigoGuia)
    }

    @Test
    fun `si el adaptador se reinicia al arrancar el resultado lo dice como hallazgo y no como error`() {
        val r = resultado(PruebasElectricasBenelli.bateriaConReinicio())

        assertEquals(Veredicto.ATENCION, r.veredicto)
        assertEquals("El adaptador se reinició al arrancar", r.titulo)
        assertTrue(r.hallazgos.any { it.contains("adaptador se reinició") })
        assertTrue(r.comprobaciones.any { it.texto == "El adaptador siguió respondiendo al arrancar" && !it.cumple })
    }

    @Test
    fun `el MAP de 70 kPa contra la estimada de 1 500 m sale con la barométrica y su margen en la serie`() {
        val r = resultado(PruebasElectricasBenelli.mapTerminado())

        assertEquals(Veredicto.FALLA, r.veredicto)
        assertEquals("P0106", r.codigoGuia)
        val serie = r.graficas.single()
        assertEquals("MAP en toda la prueba, en kPa", serie.titulo)
        val banda = serie.modelo.bandas.single()
        assertEquals("Barométrica ±6 kPa", banda.etiqueta.plano())
        assertEquals(84.6 - 6.0, banda.desde, 0.05)
        assertEquals(84.6 + 6.0, banda.hasta, 0.05)
        assertTrue(serie.leyenda.toString(), serie.leyenda.any { it.plano().contains("Presión estimada por la altitud GPS") })
        assertTrue(r.comprobaciones.single().texto.plano().startsWith("Diferencia entre el MAP y la barométrica (más ±3 kPa de la fuente): 14,1 kPa"))
        assertFalse(r.comprobaciones.single().cumple)
    }

    @Test
    fun `en el paso del MAP se ve la presión en kPa`() {
        val p = paso(PruebasElectricasBenelli.enMap(), PruebasElectricasBenelli.serieMap())

        assertEquals("MAP · PID 0B", p.vivo!!.etiqueta)
        assertEquals("Últimos 10 s, en kPa", p.tituloGrafica)
        assertTrue(p.leyendaGrafica.isEmpty())
    }
}
