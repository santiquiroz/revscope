package com.revscope.core.obd.catalogo

import com.revscope.core.obd.R
import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidadorCatalogoTest {
    private val res = XmlStringResolver.es(R::class.java)

    private fun validar(entrada: EntradaCatalogo) = ValidadorCatalogo.validar(entrada, res)

    @Test
    fun `acepta una lectura valida`() {
        assertNull(validar(entrada()))
    }

    @Test
    fun `rechaza id marca descripcion tipo y riesgo vacios o invalidos`() {
        assertNotNull(validar(entrada(id = " ")))
        assertNotNull(validar(entrada(marca = "")))
        assertNotNull(validar(entrada(descripcion = "")))
        assertNotNull(validar(entrada(tipo = "otro")))
        assertNotNull(validar(entrada(riesgo = "critico")))
    }

    @Test
    fun `rechaza header pasos comandos bloqueados y escrituras declaradas como lectura`() {
        assertNotNull(validar(entrada(header = "7e0")))
        assertNotNull(validar(entrada(pasos = emptyList())))
        assertNotNull(validar(entrada(pasos = List(11) { "22 21 21" })))
        assertNotNull(validar(entrada(pasos = listOf("ZZ"))))
        assertNotNull(validar(entrada(pasos = listOf("36 01"))))
        assertNotNull(validar(entrada(pasos = listOf("2E F1 90"))))
    }

    @Test
    fun `rechaza formulas invalidas`() {
        assertNotNull(validar(entrada(formula = "no es valida")))
    }

    @Test
    fun `el motivo sale en espanol y en ingles segun el resolver`() {
        assertEquals("el id no puede estar vacío", validar(entrada(id = " ")))
        assertEquals("debe haber entre 1 y 10 pasos", validar(entrada(pasos = emptyList())))
        val en = XmlStringResolver.en(R::class.java)
        assertEquals("the id can't be empty", ValidadorCatalogo.validar(entrada(id = " "), en))
        assertEquals("invalid risk: must be bajo, medio or alto", ValidadorCatalogo.validar(entrada(riesgo = "x"), en))
    }

    private fun entrada(
        id: String = "test.id",
        marca: String = "Mazda",
        descripcion: String = "Velocidad",
        tipo: String = "lectura",
        riesgo: String = "bajo",
        header: String? = "7E0",
        pasos: List<String> = listOf("22 21 21"),
        formula: String? = "B",
    ) = EntradaCatalogo(
        id, marca, listOf("CX-30"), null, "PCM", header, tipo, pasos, formula, "km/h",
        descripcion, false, riesgo, null, null, false, null,
    )
}
