package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.taller.dtc.AccionGuia
import com.revscope.core.obd.taller.dtc.GuiaDePrueba
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SugeridorPruebasTest {

    private val sugeridor = SugeridorPruebas.desde(GuiaDePrueba.baseReal())

    private val barridoTps = AccionGuia.Prueba(TipoPrueba.TPS_BARRIDO)
    private val multimetroTps = AccionGuia.Multimetro(SensorMultimetro.TPS)
    private val minimoRetorno = AccionGuia.Prueba(TipoPrueba.MINIMO_RETORNO)

    @Test
    fun `P0122 sugiere primero el barrido del TPS y el multímetro en el TPS`() {
        val sugeridas = sugeridor.sugerir(emptySet(), listOf("P0122"))

        assertEquals(listOf(barridoTps, multimetroTps), sugeridas.take(2).map { it.accion })
        assertEquals("por P0122", sugeridas.first().motivo)
    }

    @Test
    fun `se apaga al soltar el acelerador sugiere la prueba de mínimo y retorno`() {
        val sugeridas = sugeridor.sugerir(setOf(Sintoma.SE_APAGA_AL_SOLTAR), emptyList())

        assertEquals(minimoRetorno, sugeridas.first().accion)
        assertEquals("por «Se apaga al soltar el acelerador»", sugeridas.first().motivo)
    }

    @Test
    fun `no sostiene el mínimo en frío sugiere el arranque en frío y el mínimo y retorno`() {
        val sugeridas = sugeridor.sugerir(setOf(Sintoma.NO_SOSTIENE_MINIMO_FRIO), emptyList())

        assertEquals(listOf(AccionGuia.Prueba(TipoPrueba.ARRANQUE_FRIO), minimoRetorno), sugeridas.map { it.accion })
    }

    @Test
    fun `P0119 sugiere el arranque en frío, que marca los saltos de la temperatura`() {
        val sugeridas = sugeridor.sugerir(emptySet(), listOf("P0119"))

        assertTrue(sugeridas.any { it.accion == AccionGuia.Prueba(TipoPrueba.ARRANQUE_FRIO) && it.motivo == "por P0119" })
    }

    @Test
    fun `borrar códigos nunca aparece como prueba sugerida`() {
        val sugeridas = sugeridor.sugerir(emptySet(), listOf("P0122", "P0562"))

        assertTrue(sugeridas.none { it.accion == AccionGuia.BorrarCodigos })
    }

    @Test
    fun `el caso Benelli junta motivos del código y de los síntomas sin repetir pruebas`() {
        val sintomas = setOf(Sintoma.NO_SOSTIENE_MINIMO_FRIO, Sintoma.SE_APAGA_AL_SOLTAR, Sintoma.SE_AHOGA_AL_ACELERAR)

        val sugeridas = sugeridor.sugerir(sintomas, listOf("P0122"))

        assertEquals(
            listOf(barridoTps, multimetroTps, AccionGuia.Prueba(TipoPrueba.ARRANQUE_FRIO), minimoRetorno),
            sugeridas.map { it.accion },
        )
        assertEquals("por P0122 y «Se ahoga al acelerar»", sugeridas.first().motivo)
    }

    @Test
    fun `un código sin guía local no sugiere nada por sí solo`() {
        assertTrue(sugeridor.sugerir(emptySet(), listOf("P1A00")).isEmpty())
    }

    @Test
    fun `batería que se descarga sugiere batería y carga`() {
        val sugeridas = sugeridor.sugerir(setOf(Sintoma.BATERIA_DESCARGA), emptyList())

        assertEquals(AccionGuia.Prueba(TipoPrueba.BATERIA_CARGA), sugeridas.first().accion)
    }

    @Test
    fun `el máximo recorta la lista ordenada`() {
        val sintomas = setOf(Sintoma.NO_SOSTIENE_MINIMO_FRIO, Sintoma.SE_APAGA_AL_SOLTAR, Sintoma.SE_AHOGA_AL_ACELERAR)

        assertEquals(3, sugeridor.sugerir(sintomas, listOf("P0122"), maximo = 3).size)
    }

    @Test
    fun `sin síntomas ni códigos no hay sugerencias`() {
        assertTrue(SugeridorPruebas { emptyList() }.sugerir(emptySet(), emptyList()).isEmpty())
    }
}
