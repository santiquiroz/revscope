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
    fun `gps ida y vuelta con campos nulos y metadatos de origen`() {
        val gps = listOf(
            MuestraGps(0L, 12.5f, 3.0f, 90.0f),
            MuestraGps(1_000L, null, 4.5f, null),
            MuestraGps(2_000L, 0.0f, null, 359.5f),
        )
        val repositorio = repo()
        repositorio.guardar(captura("5000-ruta", 5_000L).copy(origen = CapturaCan.ORIGEN_APP), gps)

        assertEquals(gps, repositorio.leerGps("5000-ruta"))
        val meta = repositorio.listar().single()
        assertEquals(CapturaCan.ORIGEN_APP, meta.origen)
        assertEquals(3, meta.muestrasGps)
        assertTrue(File(temporal.root, "5000-ruta.gps.csv").readLines().first() == GpsCsv.CABECERA)
    }

    @Test
    fun `sin gps no escribe csv y leerGps devuelve vacio`() {
        val repositorio = repo()
        repositorio.guardar(captura("5000-base", 5_000L))

        assertFalse(File(temporal.root, "5000-base.gps.csv").exists())
        assertTrue(repositorio.leerGps("5000-base").isEmpty())
        assertTrue(repositorio.leerGps("../secreto").isEmpty())
    }

    @Test
    fun `json viejo sin origen ni muestrasGps se lee como mcp y cero`() {
        val json = CapturasCanRepositorio.aJson(captura("5000-base", 5_000L))
            .apply {
                remove("origen")
                remove("muestrasGps")
            }.toString()

        val leida = CapturasCanRepositorio.desdeJson(json)!!

        assertEquals(CapturaCan.ORIGEN_MCP, leida.origen)
        assertEquals(0, leida.muestrasGps)
    }

    @Test
    fun `existe solo con metadatos guardados`() {
        val repositorio = repo()
        repositorio.guardar(captura("5000-base", 5_000L))

        assertTrue(repositorio.existe("5000-base"))
        assertFalse(repositorio.existe("9999-otra"))
        assertFalse(repositorio.existe("../secreto"))
    }

    @Test
    fun `la poda borra tambien el csv de gps`() {
        val repositorio = repo(tope = 1)
        repositorio.guardar(captura("1-a", 1_000L), listOf(MuestraGps(0L, 1f, 1f, 1f)))
        repositorio.guardar(captura("2-a", 2_000L))

        assertFalse(File(temporal.root, "1-a.gps.csv").exists())
        assertFalse(File(temporal.root, "1-a.json").exists())
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
