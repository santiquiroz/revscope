package com.revscope.core.obd.taller.dtc

import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseConocimientoDtcTest {

    private val base = GuiaDePrueba.baseReal()

    @Test
    fun `el json real carga sin problemas con la cabecera y la etiqueta de orientación general`() {
        assertEquals(emptyList<String>(), base.problemas)
        assertEquals(1, base.catalogo.version)
        assertTrue(base.catalogo.fuenteDescripciones.contains("SAE J2012"))
        assertEquals("Orientación general, no es el procedimiento del fabricante", base.catalogo.aviso)
    }

    @Test
    fun `trae unos 45 códigos con las familias del diseño y el TPS completo`() {
        val codigos = base.catalogo.guias.map { it.codigo }.toSet()

        assertTrue("hay ${codigos.size}", codigos.size in 40..50)
        val familias = listOf(
            "P0105", "P0106", "P0107", "P0108", "P0110", "P0112", "P0113", "P0115", "P0116", "P0117", "P0118",
            "P0120", "P0121", "P0122", "P0123", "P0124", "P0130", "P0131", "P0132", "P0133", "P0134", "P0135",
            "P0171", "P0172", "P0201", "P0230", "P0300", "P0301", "P0335", "P0336", "P0351", "P0500", "P0501",
            "P0505", "P0506", "P0507", "P0560", "P0562", "P0563", "P0601",
        )
        assertEquals(emptyList<String>(), familias.filterNot { it in codigos })
    }

    @Test
    fun `cada código tiene título, al menos 2 causas y al menos 3 verificaciones`() {
        base.catalogo.guias.forEach { guia ->
            assertTrue("${guia.codigo} sin título", guia.titulo.isNotBlank())
            assertTrue("${guia.codigo} con ${guia.causas.size} causas", guia.causas.size >= 2)
            assertTrue("${guia.codigo} con ${guia.verificaciones.size} verificaciones", guia.verificaciones.size >= 3)
        }
    }

    @Test
    fun `las acciones del json referencian pruebas, plantillas de multímetro o el borrado válidos`() {
        val claves = base.catalogo.guias.flatMap { g -> g.verificaciones.mapNotNull { it.accion?.clave } }.toSet()
        val validas = TipoPrueba.entries.map { "PRUEBA:${it.name}" } +
            SensorMultimetro.entries.map { "MULTIMETRO:${it.name}" } + "DTC:BORRAR"

        assertEquals(emptyList<String>(), claves.filterNot { it in validas })
        assertTrue(claves.containsAll(listOf("PRUEBA:TPS_BARRIDO", "MULTIMETRO:TPS", "DTC:BORRAR")))
    }

    @Test
    fun `los relacionados apuntan a códigos con formato SAE distintos del propio`() {
        base.catalogo.guias.forEach { guia ->
            guia.relacionados.forEach { rel ->
                assertEquals("${guia.codigo} → $rel", rel, DecodificadorDtc.normalizar(rel))
                assertTrue(rel != guia.codigo)
            }
        }
    }

    @Test
    fun `P0122 trae las verificaciones del caso Benelli en orden`() {
        val pasos = base.guia("P0122")!!.verificaciones.map { it.paso.lowercase() }
        val delCaso = listOf("original", "revisar el conector", "orden de pines", "medir de nuevo", "reaprender el mínimo", "en frío")

        val posiciones = delCaso.map { clave -> pasos.indexOfFirst { it.contains(clave) } }

        assertTrue("faltan pasos: $posiciones en $pasos", posiciones.none { it < 0 })
        assertEquals(posiciones.sorted(), posiciones)
    }

    @Test
    fun `P0122 lanza el barrido, el multímetro del TPS, el borrado y el arranque en frío`() {
        val guia = base.guia("P0122")!!
        val acciones = guia.verificaciones.mapNotNull { it.accion }

        assertTrue(guia.titulo.contains("señal baja"))
        assertEquals(
            listOf(
                AccionGuia.Prueba(TipoPrueba.TPS_BARRIDO),
                AccionGuia.Multimetro(SensorMultimetro.TPS),
                AccionGuia.Prueba(TipoPrueba.TPS_BARRIDO),
                AccionGuia.BorrarCodigos,
                AccionGuia.Prueba(TipoPrueba.ARRANQUE_FRIO),
            ),
            acciones,
        )
        assertTrue(guia.notasMoto.any { it.contains("MT05") })
        assertEquals(listOf("P0120", "P0121", "P0123", "P0124"), guia.relacionados)
    }

    @Test
    fun `los valores de referencia en el texto se marcan como típicos`() {
        val conValores = base.catalogo.guias.flatMap { g -> g.verificaciones.map { it.detalle } + g.notasMoto }
            .filter { Regex("""≈\s?\d""").containsMatchIn(it) }

        assertTrue(conValores.isNotEmpty())
        conValores.forEach { assertTrue("sin «típico»: $it", it.contains("típico")) }
    }

    @Test
    fun `busca por código normalizando mayúsculas y espacios`() {
        assertNotNull(base.guia(" p0122 "))
        assertNull(base.guia("P1234"))
        assertNull(base.guia("no es un código"))
    }

    @Test
    fun `la carga es perezosa y lee el json una sola vez`() {
        var lecturas = 0
        val perezosa = BaseConocimientoDtc {
            lecturas++
            GuiaDePrueba.textoReal()
        }

        assertEquals(0, lecturas)
        perezosa.guia("P0122")
        perezosa.guia("P0171")
        assertEquals(1, lecturas)
    }

    @Test
    fun `un json dañado deja la base vacía con el problema y sin romper`() {
        val rota = BaseConocimientoDtc { "{ no es json" }

        assertNull(rota.guia("P0122"))
        assertTrue(rota.catalogo.guias.isEmpty())
        assertTrue(rota.problemas.single().contains(BaseConocimientoDtc.ASSET_GUIA))
    }

    @Test
    fun `descarta entradas inválidas y conserva las válidas con el motivo`() {
        val json = GuiaDePrueba.conEntradas(
            GuiaDePrueba.entrada("P0122"),
            GuiaDePrueba.entrada("P0123", causas = 1),
            GuiaDePrueba.entrada("P0124", verificaciones = 2),
            GuiaDePrueba.entrada("P0120", accion = "PRUEBA:NO_EXISTE"),
            GuiaDePrueba.entrada("P0121", relacionados = "[\"X9\"]"),
        )

        val conInvalidas = BaseConocimientoDtc { json }

        assertEquals(listOf("P0122"), conInvalidas.catalogo.guias.map { it.codigo })
        val problemas = conInvalidas.problemas.joinToString("\n")
        assertTrue(problemas, problemas.contains("P0123: menos de 2 causas"))
        assertTrue(problemas, problemas.contains("P0124: menos de 3 verificaciones"))
        assertTrue(problemas, problemas.contains("PRUEBA:NO_EXISTE"))
        assertTrue(problemas, problemas.contains("P0121: relacionados inválidos"))
    }

    @Test
    fun `un código repetido se descarta en todas sus entradas`() {
        val json = GuiaDePrueba.conEntradas(GuiaDePrueba.entrada("P0122"), GuiaDePrueba.entrada("P0122"))

        val repetida = BaseConocimientoDtc { json }

        assertNull(repetida.guia("P0122"))
        assertEquals(listOf("P0122: código repetido"), repetida.problemas)
    }
}
