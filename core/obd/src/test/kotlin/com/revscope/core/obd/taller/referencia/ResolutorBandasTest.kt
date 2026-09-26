package com.revscope.core.obd.taller.referencia

import com.revscope.core.data.db.entities.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutorBandasTest {

    private val deFuente = BandaReferencia(
        ClavesBanda.TPS_CERRADO_V, 0.5, 0.7, "V", OrigenBanda.FUENTE, fuente = "Manual de servicio TNT 150i",
    )
    private val delUsuario = BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.4, 0.8, "V", OrigenBanda.USUARIO)

    @Test
    fun `sin bandas del modelo devuelve la típica con su origen`() {
        val banda = ResolutorBandas.resolver(ClavesBanda.TPS_CERRADO_V, VehicleType.MOTORCYCLE, emptyList())

        assertEquals(BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.3, 1.0, "V", OrigenBanda.TIPICO), banda)
    }

    @Test
    fun `la banda con fuente gana a la típica`() {
        val banda = ResolutorBandas.resolver(ClavesBanda.TPS_CERRADO_V, VehicleType.MOTORCYCLE, listOf(deFuente))

        assertEquals(deFuente, banda)
        assertEquals("Fuente: Manual de servicio TNT 150i", banda?.etiquetaOrigen)
    }

    @Test
    fun `la banda del usuario gana a la de fuente y a la típica, en cualquier orden`() {
        val primero = ResolutorBandas.resolver(ClavesBanda.TPS_CERRADO_V, VehicleType.MOTORCYCLE, listOf(delUsuario, deFuente))
        val despues = ResolutorBandas.resolver(ClavesBanda.TPS_CERRADO_V, VehicleType.MOTORCYCLE, listOf(deFuente, delUsuario))

        assertEquals(OrigenBanda.USUARIO, primero?.origen)
        assertEquals(delUsuario, despues)
    }

    @Test
    fun `las bandas de otra clave no interfieren`() {
        val fondo = BandaReferencia(ClavesBanda.TPS_FONDO_V, 4.0, 4.6, "V", OrigenBanda.USUARIO)

        val banda = ResolutorBandas.resolver(ClavesBanda.TPS_CERRADO_V, VehicleType.MOTORCYCLE, listOf(fondo))

        assertEquals(OrigenBanda.TIPICO, banda?.origen)
    }

    @Test
    fun `el mínimo típico depende del tipo de vehículo`() {
        val moto = ResolutorBandas.resolver(ClavesBanda.MINIMO_RPM, VehicleType.MOTORCYCLE, emptyList())
        val carro = ResolutorBandas.resolver(ClavesBanda.MINIMO_RPM, VehicleType.CAR, emptyList())

        assertEquals(1_200.0 to 1_700.0, moto?.min to moto?.max)
        assertEquals(600.0 to 900.0, carro?.min to carro?.max)
    }

    @Test
    fun `una clave desconocida sin banda del modelo no tiene referencia`() {
        assertNull(ResolutorBandas.resolver("NO_EXISTE", VehicleType.CAR, emptyList()))
    }

    @Test
    fun `resolver todas combina típicas y del modelo y conserva el origen de cada una`() {
        val propia = BandaReferencia("TPS_CERRADO_PROPIO_V", 0.6, 0.8, "V", OrigenBanda.USUARIO)

        val todas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, listOf(delUsuario, propia))

        assertEquals(OrigenBanda.USUARIO, todas.getValue(ClavesBanda.TPS_CERRADO_V).origen)
        assertEquals(OrigenBanda.TIPICO, todas.getValue(ClavesBanda.TPS_FONDO_V).origen)
        assertEquals(propia, todas["TPS_CERRADO_PROPIO_V"])
        assertTrue(todas.keys.containsAll(BandasTipicas.para(VehicleType.MOTORCYCLE).keys))
    }

    @Test
    fun `todas las bandas típicas son de origen típico y sin fuente inventada`() {
        VehicleType.entries.forEach { tipo ->
            BandasTipicas.para(tipo).values.forEach { banda ->
                assertEquals(banda.clave, OrigenBanda.TIPICO, banda.origen)
                assertEquals(banda.clave, "", banda.fuente)
            }
        }
    }
}
