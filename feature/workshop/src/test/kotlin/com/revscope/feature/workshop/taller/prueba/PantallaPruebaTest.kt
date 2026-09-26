package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.sesion.Veredicto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantallaPruebaTest {

    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)

    private fun paso(indice: Int, fase: FasePaso, restanteMs: Long?) =
        (pantalla.fase(EntradaPantalla(BarridoBenelli.enPaso(indice, fase, restanteMs), serieVivo = BarridoBenelli.serieMedio(), bandas = BarridoBenelli.bandas)) as FasePantalla.Paso).paso

    @Test
    fun `sin prueba elegida se listan todas y solo las que tienen analizador se pueden abrir`() {
        val fase = pantalla.fase(EntradaPantalla(EstadoPrueba.Inactiva)) as FasePantalla.Elegir

        assertEquals(TipoPrueba.entries, fase.opciones.map { it.tipo })
        assertEquals(
            listOf(TipoPrueba.TPS_BARRIDO, TipoPrueba.MINIMO_RETORNO, TipoPrueba.ARRANQUE_FRIO),
            fase.opciones.filter { it.disponible }.map { it.tipo },
        )
    }

    @Test
    fun `la preparacion muestra los pasos y cada precondicion con lo que la detecto y que hacer si falla`() {
        val fase = pantalla.fase(
            EntradaPantalla(EstadoPrueba.Inactiva, TipoPrueba.TPS_BARRIDO, BarridoBenelli.precondicionesUnaFallando),
        ) as FasePantalla.Preparacion

        assertFalse(fase.listas)
        assertEquals("Cerrado: sostener 5 s", fase.pasos.first())
        assertEquals("Barrido lento: se graba solo 8 s", fase.pasos.last())
        val falla = fase.precondiciones.single { !it.cumple }
        assertEquals("Motor encendido (1454 rpm)", falla.texto)
        assertTrue(falla.queHacer!!.startsWith("Apaga el motor"))
    }

    @Test
    fun `la verificacion del controlador se muestra si no hay una evaluacion viva`() {
        val estado = EstadoPrueba.Verificando(TipoPrueba.TPS_BARRIDO, BarridoBenelli.precondicionesUnaFallando)

        val fase = pantalla.fase(EntradaPantalla(estado)) as FasePantalla.Preparacion

        assertEquals(4, fase.precondiciones.size)
    }

    @Test
    fun `al posicionar la accion es listo y el anuncio dice el paso`() {
        val p = paso(1, FasePaso.POSICIONANDO, null)

        assertEquals(2 to 5, p.indice to p.total)
        assertEquals(TextosPrueba.ACCION_LISTO, p.accionPrincipal)
        assertNull(p.restanteS)
        assertEquals("Paso 2 de 5: Medio. Posiciona el acelerador y toca Listo", p.anuncio)
        assertTrue(p.confirmarAlCancelar)
    }

    @Test
    fun `sosteniendo con 3 s el anuncio es la cuenta y no hay accion principal`() {
        val p = paso(1, FasePaso.SOSTENIENDO, 2_400)

        assertEquals(3, p.restanteS)
        assertEquals(0.48f, p.fraccionRestante!!, 1e-4f)
        assertEquals("3", p.anuncio)
        assertNull(p.accionPrincipal)
    }

    @Test
    fun `el anuncio no cambia mientras la cuenta va por encima de 3 s`() {
        val anuncios = listOf(5_000L, 4_900L, 4_100L, 3_050L).map { paso(1, FasePaso.SOSTENIENDO, it).anuncio }.distinct()

        assertEquals(listOf("Paso 2 de 5: Medio. Sosteniendo"), anuncios)
    }

    @Test
    fun `el valor en vivo va en porcentaje y voltios y la minigrafica cubre 10 s en voltios`() {
        val p = paso(1, FasePaso.SOSTENIENDO, 2_400)

        assertEquals("9,4 %", p.vivo!!.principal)
        assertEquals("0,47 V", p.vivo!!.secundario)
        assertEquals("Últimos 10 s, en voltios", p.tituloGrafica)
        assertEquals(-10.0..0.0, p.grafica.rangoX)
        assertEquals(0.0..5.0, p.grafica.rangoY)
        assertTrue(p.grafica.series.single().puntos.all { it.y < 0.5 })
        assertTrue(p.grafica.descripcion, p.grafica.descripcion.startsWith("PID 11 en los últimos 10 s: de 0,12 V a 0,47 V, subiendo"))
    }

    @Test
    fun `el paso en cerrado sombrea la banda de cerrado con su origen y el medio ninguna`() {
        val cerrado = paso(0, FasePaso.SOSTENIENDO, 4_000)
        val medio = paso(1, FasePaso.SOSTENIENDO, 4_000)

        assertEquals(listOf("Cerrado típico"), cerrado.grafica.bandas.map { it.etiqueta })
        assertEquals(listOf("Cerrado: 0,3–1,0 V (≈6–20 %) · Típico (editable)"), cerrado.leyendaGrafica)
        assertTrue(medio.grafica.bandas.isEmpty())
    }

    @Test
    fun `el primer paso sin grabar se cancela sin preguntar`() {
        assertFalse(paso(0, FasePaso.POSICIONANDO, null).confirmarAlCancelar)
        assertTrue(paso(0, FasePaso.SOSTENIENDO, 3_000).confirmarAlCancelar)
    }

    @Test
    fun `el resultado del caso Benelli da senal baja con pesas contra la banda y la guia del P0122`() {
        val r = (pantalla.fase(EntradaPantalla(BarridoBenelli.terminada())) as FasePantalla.Resultado).resultado

        assertEquals(Veredicto.FALLA, r.veredicto)
        assertEquals("Señal baja en todo el recorrido, pareja y estable", r.titulo)
        assertEquals("P0122", r.codigoGuia)
        assertTrue(r.guardado)
        assertEquals(listOf("Cerrado", "Medio", "A fondo", "Cerrado otra vez"), r.pesas.map { it.titulo })
        val cerrado = r.pesas.first()
        assertEquals(0.12, cerrado.mediaV, 0.005)
        assertEquals(Veredicto.FALLA, cerrado.nivel)
        assertEquals("0,3–1,0 V, Típico (editable)", cerrado.banda!!.texto)
        assertNull(r.pesas[1].banda)
        assertEquals(Veredicto.INFO, r.pesas[1].nivel)
        assertTrue(r.pesas[1].descripcion.endsWith("sin banda fija: debe quedar entre cerrado y fondo"))
        assertTrue(r.comprobaciones.any { !it.cumple && it.texto.startsWith("A fondo: 0,89 V · banda 3,8–4,8 V, Típico (editable)") })
        assertEquals("Referencia 5,00 V · Típico (editable)", r.referencia)
    }

    @Test
    fun `la serie del resultado sombrea los 5 pasos y las bandas y los nombra en la leyenda`() {
        val r = (pantalla.fase(EntradaPantalla(BarridoBenelli.terminada())) as FasePantalla.Resultado).resultado

        val grafica = r.graficas.single()
        val serie = grafica.modelo
        assertEquals("Toda la prueba, en voltios", grafica.titulo)
        assertEquals(listOf("1", "2", "3", "4", "5"), serie.tramos.map { it.etiqueta })
        assertEquals(2, serie.bandas.size)
        assertEquals("1 Cerrado · 2 Medio · 3 A fondo · 4 Cerrado otra vez · 5 Barrido lento", grafica.leyenda.first())
        assertTrue(serie.descripcion, serie.descripcion.startsWith("TPS en toda la prueba: de 0,12 V a 0,90 V, 5 pasos sombreados."))
    }

    @Test
    fun `sin sesion abierta el resultado no esta guardado`() {
        val r = (pantalla.fase(EntradaPantalla(BarridoBenelli.terminada(eventoId = null))) as FasePantalla.Resultado).resultado

        assertFalse(r.guardado)
    }
}
