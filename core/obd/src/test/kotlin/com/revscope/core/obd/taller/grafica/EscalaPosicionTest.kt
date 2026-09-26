package com.revscope.core.obd.taller.grafica

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalaPosicionTest {

    private val tipicas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())

    @Test
    fun `los pids de posicion son los de mariposa y pedal, sin importar mayusculas`() {
        listOf("11", "45", "47", "49", "4A", "4b", "4C", "5A").forEach { assertTrue(it, PidsPosicion.es(it)) }
        listOf("0C", "0D", "05", "42").forEach { assertFalse(it, PidsPosicion.es(it)) }
    }

    @Test
    fun `en voltios convierte con la referencia y en porcentaje deja el valor de la ECU`() {
        val vref = ReferenciaVoltaje.TIPICA

        assertEquals(0.46, UnidadPosicion.VOLTIOS.desdePorcentaje(9.2, vref), 1e-9)
        assertEquals(9.2, UnidadPosicion.PORCENTAJE.desdePorcentaje(9.2, vref), 1e-9)
        assertEquals(0.4508, UnidadPosicion.VOLTIOS.desdePorcentaje(9.2, ReferenciaVoltaje.editada(4.9)), 1e-9)
    }

    @Test
    fun `las bandas del tps en voltios son cerrado y fondo con su origen`() {
        val bandas = BandasPosicion.tps(tipicas, UnidadPosicion.VOLTIOS, ReferenciaVoltaje.TIPICA)

        assertEquals(listOf(ClavesBanda.TPS_CERRADO_V, ClavesBanda.TPS_FONDO_V), bandas.map { it.clave })
        val cerrado = bandas.first()
        assertEquals(0.3, cerrado.min, 1e-9)
        assertEquals(1.0, cerrado.max, 1e-9)
        assertEquals("Cerrado típico", cerrado.etiquetaCorta)
        assertEquals("Cerrado: 0,3–1,0 V (≈6–20 %) · Típico (editable)", cerrado.descripcion)
    }

    @Test
    fun `en porcentaje las bandas se pasan con la referencia usada`() {
        val bandas = BandasPosicion.tps(tipicas, UnidadPosicion.PORCENTAJE, ReferenciaVoltaje.editada(4.8))

        assertEquals(6.25, bandas[0].min, 1e-9)
        assertEquals(20.833, bandas[0].max, 1e-3)
        assertEquals(79.167, bandas[1].min, 1e-3)
        assertEquals(100.0, bandas[1].max, 1e-9)
    }

    @Test
    fun `una banda de fuente o editada lo dice y una abierta no se sombrea`() {
        val bandas = mapOf(
            ClavesBanda.TPS_CERRADO_V to BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.5, 0.8, "V", OrigenBanda.FUENTE, "Catálogo de partes Auteco"),
            ClavesBanda.TPS_FONDO_V to BandaReferencia(ClavesBanda.TPS_FONDO_V, 4.0, null, "V", OrigenBanda.USUARIO),
        )

        val escala = BandasPosicion.tps(bandas, UnidadPosicion.VOLTIOS, ReferenciaVoltaje.TIPICA)

        assertEquals(1, escala.size)
        assertEquals("Cerrado según fuente", escala.single().etiquetaCorta)
        assertTrue(escala.single().descripcion.endsWith("Fuente: Catálogo de partes Auteco"))
    }

    @Test
    fun `el valor en vivo se muestra en porcentaje y en voltios con coma decimal`() {
        assertEquals("9,2 %", FormatoPosicion.porcentaje(9.2))
        assertEquals("0,46 V", FormatoPosicion.voltios(0.46))
        assertEquals("9,2 % · 0,46 V", FormatoPosicion.ambos(9.2, ReferenciaVoltaje.TIPICA))
    }
}
