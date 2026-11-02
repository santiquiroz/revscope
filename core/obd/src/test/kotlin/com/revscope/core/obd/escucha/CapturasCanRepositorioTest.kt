package com.revscope.core.obd.escucha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CapturasCanRepositorioTest {

    @get:Rule
    val temporal = TemporaryFolder()

    private fun repo(tope: Int = 200) = CapturasCanRepositorio(temporal.root, tope)

    private fun captura(id: String, inicio: Long, filtro: String? = null) = CapturaCan(
        id = id,
        etiqueta = "base",
        inicioEpochMs = inicio,
        duracionMs = 1_000L,
        protocolo = "6",
        bits29 = false,
        filtro = filtro,
        silencioso = true,
        desbordes = 1,
        errores = 2,
        huecos = listOf(Hueco(100L, 200L)),
        tramas = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x03, 0x41, 0x0D, 0x00)),
            TramaCan(120L, 0x7E9, false, emptyList()),
        ),
    )

    @Test
    fun `guardar y leer devuelven la captura intacta`() {
        val original = captura("5000-base", 5_000L, "7E8")
        repo().guardar(original)

        assertEquals(original, repo().leer("5000-base"))
    }

    @Test
    fun `json anterior sin huecos se lee como lista vacia`() {
        val json = CapturasCanRepositorio.aJson(captura("5000-base", 5_000L))
            .apply { remove("huecos") }.toString()

        assertTrue(CapturasCanRepositorio.desdeJson(json)!!.huecos.isEmpty())
    }

    @Test
    fun `listar ordena por mas nueva y sin tramas`() {
        val repositorio = repo()
        repositorio.guardar(captura("1000-vieja", 1_000L))
        repositorio.guardar(captura("2000-nueva", 2_000L))

        val lista = repositorio.listar()

        assertEquals(listOf("2000-nueva", "1000-vieja"), lista.map { it.id })
        assertTrue(lista.all { it.tramas.isEmpty() })
    }

    @Test
    fun `leer id inexistente o raro devuelve null`() {
        val repositorio = repo()
        repositorio.guardar(captura("5000-base", 5_000L))

        assertNull(repositorio.leer("9999-otra"))
        assertNull(repositorio.leer("../secreto"))
        assertNull(repositorio.leer("mala!"))
    }

    @Test
    fun `al pasar el tope borra las mas viejas`() {
        val repositorio = repo(tope = 3)
        repositorio.guardar(captura("1-a", 1_000L))
        repositorio.guardar(captura("2-a", 2_000L))
        repositorio.guardar(captura("3-a", 3_000L))
        repositorio.guardar(captura("4-a", 4_000L))

        assertEquals(listOf("4-a", "3-a", "2-a"), repositorio.listar().map { it.id })
        assertFalse(File(temporal.root, "1-a.log").exists())
        assertFalse(File(temporal.root, "1-a.json").exists())
    }
}
