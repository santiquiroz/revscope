package com.revscope.core.obd.catalogo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CatalogoPropietarioTest {
    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `prioriza entrada de usuario sobre asset por id`() {
        val asset = entrada(descripcion = "original")
        val archivo = temporal.newFile()
        archivo.writeText(CodecCatalogo.escribir(null, listOf(entrada(descripcion = "dueño"))))
        val catalogo = CatalogoPropietario({ CodecCatalogo.escribir("aviso", listOf(asset)) }, archivo)

        assertEquals("dueño", catalogo.porId(asset.id)?.descripcion)
        assertEquals(1, catalogo.todas().size)
    }

    @Test
    fun `aplica filtros de marca modelo tipo y texto sin distinguir mayusculas`() {
        val catalogo = catalogo(temporal.newFile(), listOf(entrada()))

        val resultado = catalogo.buscar("maz", "cx-3", "lect", "torque")

        assertEquals(1, resultado.size)
        assertEquals("mazda.test", resultado.single().id)
    }

    @Test
    fun `guarda entrada valida y rechaza la invalida`() {
        val archivo = File(temporal.root, "usuario.json")
        val catalogo = catalogo(archivo, emptyList())

        assertNull(catalogo.guardarUsuario(entrada()))
        assertTrue(archivo.isFile)
        assertEquals("Torque", CatalogoPropietario({ null }, archivo).porId("mazda.test")?.descripcion)
        assertNotNull(catalogo.guardarUsuario(entrada(id = "", descripcion = "inválida")))
    }

    private fun catalogo(file: File, entries: List<EntradaCatalogo>) =
        CatalogoPropietario({ CodecCatalogo.escribir("aviso", entries) }, file)

    private fun entrada(
        id: String = "mazda.test",
        descripcion: String = "Torque",
    ) = EntradaCatalogo(
        id, "Mazda", listOf("CX-30"), null, "PCM", "7E0", "lectura",
        listOf("22 21 21"), "A", "Nm", descripcion, false, "bajo",
        null, null, false, null,
    )
}
