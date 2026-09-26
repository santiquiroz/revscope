package com.revscope.core.obd.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SamplingPresetTest {

    private val bases = listOf(100L, 500L, 2_000L, 1_000L)

    private fun intervalos(preset: SamplingPreset) = bases.map(preset::intervaloPara)

    @Test
    fun `preset estandar reproduce 100 500 2000 1000`() {
        assertEquals(bases, intervalos(SamplingPreset.ESTANDAR_2S))
    }

    @Test
    fun `presets 1s 500ms y 250ms aplican min entre base y preset`() {
        assertEquals(listOf(100L, 500L, 1_000L, 1_000L), intervalos(SamplingPreset.UN_SEGUNDO))
        assertEquals(listOf(100L, 500L, 500L, 500L), intervalos(SamplingPreset.MEDIO_SEGUNDO))
        assertEquals(listOf(100L, 250L, 250L, 250L), intervalos(SamplingPreset.CUARTO_SEGUNDO))
    }

    @Test
    fun `maximo no espera entre ciclos`() {
        assertEquals(listOf(0L, 0L, 0L, 0L), intervalos(SamplingPreset.MAXIMO))
    }

    @Test
    fun `claves desconocidas vuelven al estandar y la clave mcp se valida`() {
        assertEquals(SamplingPreset.ESTANDAR_2S, SamplingPreset.desdeClave(null))
        assertEquals(SamplingPreset.ESTANDAR_2S, SamplingPreset.desdeClave("RARO"))
        assertEquals(SamplingPreset.CUARTO_SEGUNDO, SamplingPreset.desdeClave("CUARTO_SEGUNDO"))
        assertEquals(SamplingPreset.MAXIMO, SamplingPreset.desdeClaveMcp("maximo"))
        assertNull(SamplingPreset.desdeClaveMcp("10ms"))
    }
}
