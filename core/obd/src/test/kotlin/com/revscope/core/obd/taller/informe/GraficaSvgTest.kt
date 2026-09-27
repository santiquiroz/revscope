package com.revscope.core.obd.taller.informe

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraficaSvgTest {

    @Test
    fun `reduce a cuatrocientos puntos y conserva minimo y maximo`() {
        val puntos = (0..1_999).map { i ->
            PuntoGrafica(i.toDouble(), when (i) {
                421 -> -30.0
                1_733 -> 90.0
                else -> 20.0 + (i % 17)
            })
        }

        val reducidos = GraficaSvg.reducir(puntos)

        assertTrue(reducidos.size <= 400)
        assertEquals(-30.0, reducidos.minOf { it.y }, 0.0)
        assertEquals(90.0, reducidos.maxOf { it.y }, 0.0)
    }

    @Test
    fun `genera svg valido para lineas y pesas con etiquetas escapadas`() {
        val linea = GraficaSvg.lineas(
            listOf(
                SerieGrafica("TPS <ECU>", listOf(PuntoGrafica(0.0, 0.2), PuntoGrafica(1.0, 4.5))),
                SerieGrafica("Referencia", listOf(PuntoGrafica(0.0, 0.3), PuntoGrafica(1.0, 4.2))),
            ),
            tramos = listOf(TramoGrafica(0.0, 0.5, "Cerrado")),
        )
        val pesas = GraficaSvg.pesas(
            listOf(PesaGrafica("Señal & masa", 0.22, 0.4, 0.8, "V", "Típico")),
        )

        parsear(linea)
        parsear(pesas)
        assertTrue(linea.contains("TPS &lt;ECU&gt;"))
        assertTrue(linea.contains("stroke-dasharray:10 6"))
        assertTrue(linea.contains("class=\"tramo tramo0\""))
        assertTrue(linea.contains(">Cerrado</text>"))
        assertTrue(pesas.contains("Señal &amp; masa"))
    }

    private fun parsear(svg: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        factory.newDocumentBuilder().parse(svg.byteInputStream())
    }
}
