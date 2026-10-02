package com.revscope.core.obd.catalogo

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidadorCatalogoTest {
    @Test
    fun `acepta una lectura valida`() {
        assertNull(ValidadorCatalogo.validar(entrada()))
    }

    @Test
    fun `rechaza id marca descripcion tipo y riesgo vacios o invalidos`() {
        assertNotNull(ValidadorCatalogo.validar(entrada(id = " ")))
        assertNotNull(ValidadorCatalogo.validar(entrada(marca = "")))
        assertNotNull(ValidadorCatalogo.validar(entrada(descripcion = "")))
        assertNotNull(ValidadorCatalogo.validar(entrada(tipo = "otro")))
        assertNotNull(ValidadorCatalogo.validar(entrada(riesgo = "critico")))
    }

    @Test
    fun `rechaza header pasos comandos bloqueados y escrituras declaradas como lectura`() {
        assertNotNull(ValidadorCatalogo.validar(entrada(header = "7e0")))
        assertNotNull(ValidadorCatalogo.validar(entrada(pasos = emptyList())))
        assertNotNull(ValidadorCatalogo.validar(entrada(pasos = List(11) { "22 21 21" })))
        assertNotNull(ValidadorCatalogo.validar(entrada(pasos = listOf("ZZ"))))
        assertNotNull(ValidadorCatalogo.validar(entrada(pasos = listOf("36 01"))))
        assertNotNull(ValidadorCatalogo.validar(entrada(pasos = listOf("2E F1 90"))))
    }

    @Test
    fun `rechaza formulas invalidas`() {
        assertNotNull(ValidadorCatalogo.validar(entrada(formula = "no es valida")))
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
