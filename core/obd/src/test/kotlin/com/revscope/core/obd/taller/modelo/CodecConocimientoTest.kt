package com.revscope.core.obd.taller.modelo

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.referencia.OrigenBanda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecConocimientoTest {

    private val benelli: ConocimientoModelo by lazy {
        CodecConocimiento.leerCatalogo(CatalogoDePrueba.textoReal())
            .single { it.conocimiento.clave == CatalogoDePrueba.CLAVE_BENELLI }.conocimiento
    }

    @Test
    fun `el catálogo real trae la Benelli TNT 150i 2022 como moto`() {
        assertEquals("Benelli TNT 150i (2022)", benelli.nombre)
        assertEquals(VehicleType.MOTORCYCLE, benelli.tipo)
        assertTrue(benelli.ecu!!.contains("96C00P100D01"))
        assertTrue(benelli.ecu!!.contains("MT05"))
        assertTrue(benelli.fuenteEcu!!.contains("Catálogo de partes Auteco TNT 150i, figura 34"))
        assertTrue(benelli.notasProtocolo.contains("K-line"))
    }

    @Test
    fun `los repuestos del catálogo Auteco citan figura e ítem`() {
        val oem = benelli.repuestos.filter { it.tipo == TipoRepuesto.OEM }.associate { it.referencia to it.fuente }

        assertEquals(
            mapOf(
                "96C00P100D01" to "Catálogo de partes Auteco TNT 150i, figura 34, ítem 14",
                "280756030001" to "Catálogo de partes Auteco TNT 150i, figura 34, ítem 35",
                "289004320030" to "Catálogo de partes Auteco TNT 150i, figura 34, ítem 36",
                "280024320000" to "Catálogo de partes Auteco TNT 150i, figura 34, ítem 37",
                "280013320000" to "Catálogo de partes Auteco TNT 150i, figura 34, ítem 26",
            ),
            oem,
        )
    }

    @Test
    fun `los TPS de Keeway quedan como no equivalentes con aviso y fuente`() {
        val keeway = benelli.repuestos.filter { it.tipo == TipoRepuesto.NO_EQUIVALENTE }

        assertEquals(setOf("280023130000", "280023300000"), keeway.map { it.referencia }.toSet())
        keeway.forEach { repuesto ->
            assertEquals("TPS", repuesto.rol)
            assertTrue(repuesto.nota.contains("sin ser equivalentes al original"))
            assertTrue(repuesto.fuente.contains("informe del caso"))
        }
    }

    @Test
    fun `el cableado del TPS tiene los tres colores del caso y su fuente`() {
        val tps = benelli.cableado.single { it.sensor == "TPS" }

        assertEquals(
            listOf(
                CableModelo(FuncionCable.REF_5V, "Rojo"),
                CableModelo(FuncionCable.MASA, "Negro"),
                CableModelo(FuncionCable.SENAL, "Verde-amarillo"),
            ),
            tps.cables,
        )
        assertTrue(tps.fuente.contains("Medición del dueño"))
    }

    @Test
    fun `cada repuesto, cableado y nota del catálogo cita fuente`() {
        CodecConocimiento.leerCatalogo(CatalogoDePrueba.textoReal()).forEach { semilla ->
            val modelo = semilla.conocimiento
            modelo.repuestos.forEach { assertTrue(it.referencia, it.fuente.isNotBlank()) }
            modelo.cableado.forEach { assertTrue(it.sensor, it.fuente.isNotBlank()) }
            modelo.notas.forEach { assertTrue(it.texto, it.fuente.isNotBlank()) }
            modelo.bandas.forEach { assertEquals(OrigenBanda.FUENTE, it.origen) }
        }
    }

    @Test
    fun `el ejemplo MT05 va como ejemplo no oficial y no reemplaza las bandas típicas`() {
        val ejemplo = benelli.notas.single { it.tipo == TipoNota.EJEMPLO }

        assertTrue(ejemplo.texto.contains("0,7 V"))
        assertTrue(ejemplo.texto.contains("4,5 V"))
        assertTrue(ejemplo.texto.contains("no el valor oficial de Benelli"))
        assertTrue(ejemplo.fuente.contains("HUD ECU Hacker"))
        assertTrue(benelli.bandas.isEmpty())
    }

    @Test
    fun `los voltajes oficiales del TPS quedan como pendientes`() {
        val pendiente = benelli.notas.single { it.tipo == TipoNota.PENDIENTE }

        assertTrue(pendiente.texto.contains("Voltajes oficiales del TPS"))
        assertTrue(pendiente.fuente.contains("no consultado"))
    }

    @Test
    fun `notas y cableado sobreviven la ida y vuelta por la columna JSON`() {
        val notas = CodecConocimiento.notasDesdeJson(CodecConocimiento.notasAJson(benelli.notas))
        val cableado = CodecConocimiento.cableadoDesdeJson(CodecConocimiento.cableadoAJson(benelli.cableado))

        assertEquals(benelli.notas, notas)
        assertEquals(benelli.cableado, cableado)
    }

    @Test
    fun `columnas vacías se leen como listas vacías`() {
        assertEquals(emptyList<NotaModelo>(), CodecConocimiento.notasDesdeJson(""))
        assertEquals(emptyList<CableadoSensor>(), CodecConocimiento.cableadoDesdeJson("[]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un repuesto sin fuente rechaza el catálogo`() {
        CodecConocimiento.leerCatalogo(CatalogoDePrueba.conVersion(1).replace("\"Catálogo de prueba\"", "\"\""))
    }

    @Test
    fun `una banda de la semilla queda con origen fuente`() {
        val banda = CodecConocimiento.leerCatalogo(CatalogoDePrueba.conVersion(1)).single().conocimiento.bandas.single()

        assertEquals(OrigenBanda.FUENTE, banda.origen)
        assertEquals("Fuente: Manual de prueba", banda.etiquetaOrigen)
    }
}
