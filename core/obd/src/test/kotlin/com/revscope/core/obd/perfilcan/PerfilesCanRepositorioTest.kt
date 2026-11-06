package com.revscope.core.obd.perfilcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PerfilesCanRepositorioTest {

    private val resolver = com.revscope.core.uitesting.XmlStringResolver.es(
        com.revscope.core.obd.R::class.java,
        resDirs = listOf("src/main/res", "../common/src/main/res"),
    )

    @get:Rule
    val tmp = TemporaryFolder()

    private val idApache = "tvs.apache_rtr_160_4v.2024"

    private val jsonApache: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("perfiles_can/$idApache.json")) {
            "no está el perfil incluido de la Apache"
        }.bufferedReader().use { it.readText() }

    private fun carpeta(): File = File(tmp.root, "perfiles_can")

    private fun repositorio(incluidos: List<String> = listOf(jsonApache)) =
        PerfilesCanRepositorio(carpeta(), { incluidos })

    @Test
    fun `el incluido de la Apache parsea y tiene 11 señales sin verificar`() {
        val perfil = CodecPerfilCan.leer(jsonApache).getOrThrow()

        assertEquals(idApache, perfil.id)
        assertEquals(11, perfil.senales.size)
        assertTrue(perfil.senales.all { it.estado == EstadoSenal.SIN_VERIFICAR })
        assertEquals(perfil.senales.size, perfil.senales.map { it.clave }.toSet().size)
        assertEquals(listOf("U507EBS6B1A1a706"), perfil.calibracionesEcu)
        assertEquals("CC-BY-SA-4.0", perfil.licencia)
        assertEquals("santiquiroz", perfil.autor)
    }

    @Test
    fun `el incluido de la Apache decodifica las tramas reales`() {
        val perfil = CodecPerfilCan.leer(jsonApache).getOrThrow()
        fun senal(clave: String) = perfil.senales.single { it.clave == clave }
        fun hex(t: String) = t.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        assertEquals("3", DecodificadorSenal.texto(senal(ClavesSenal.GEAR), hex("0000440003004B04"), resolver))
        assertEquals(3576.0, DecodificadorSenal.valor(senal(ClavesSenal.RPM), hex("0C0DF8000000D200"))!!, 0.0)
        assertEquals(1.0, DecodificadorSenal.valor(senal(ClavesSenal.PATA_LATERAL), hex("0000450000404604"))!!, 0.0)
        assertEquals(1.0, DecodificadorSenal.valor(senal(ClavesSenal.KILL_SWITCH), hex("0000450000004604"))!!, 0.0)
        assertEquals(52.0, DecodificadorSenal.valor(senal(ClavesSenal.TEMP_MOTOR), hex("00005C000000540C"))!!, 0.0)
    }

    @Test
    fun `disponibles lista el incluido cuando no hay propios`() {
        assertEquals(listOf(idApache), repositorio().disponibles().map { it.id })
    }

    @Test
    fun `adoptar copia una sola vez`() {
        val repo = repositorio()

        val copia = repo.adoptar(idApache)
        assertTrue(File(carpeta(), "$idApache.json").exists())
        assertTrue(copia.senales.all { it.estado == EstadoSenal.SIN_VERIFICAR })

        repo.marcarEstado(idApache, ClavesSenal.GEAR, EstadoSenal.VERIFICADA)
        val otraVez = repo.adoptar(idApache)

        assertEquals(EstadoSenal.VERIFICADA, otraVez.senales.single { it.clave == ClavesSenal.GEAR }.estado)
        assertEquals(1, repo.disponibles().size)
    }

    @Test
    fun `adoptar deja sin verificar las señales de un incluido verificado`() {
        val verificado = CodecPerfilCan.leer(jsonApache).getOrThrow().let { p ->
            p.copy(senales = p.senales.map { it.copy(estado = EstadoSenal.VERIFICADA) })
        }
        val repo = repositorio(listOf(CodecPerfilCan.escribir(verificado)))

        assertTrue(repo.adoptar(idApache).senales.all { it.estado == EstadoSenal.SIN_VERIFICAR })
    }

    @Test
    fun `marcarEstado persiste y sube cambios`() {
        val repo = repositorio()
        val antes = repo.cambios.value

        repo.marcarEstado(idApache, ClavesSenal.PATA_LATERAL, EstadoSenal.VERIFICADA)

        assertTrue(repo.cambios.value > antes)
        val releido = repositorio().leer(idApache)!!
        assertEquals(EstadoSenal.VERIFICADA, releido.senales.single { it.clave == ClavesSenal.PATA_LATERAL }.estado)
        assertEquals(EstadoSenal.SIN_VERIFICAR, releido.senales.single { it.clave == ClavesSenal.GEAR }.estado)
    }

    @Test
    fun `marcarEstado con clave ausente falla`() {
        try {
            repositorio().marcarEstado(idApache, "NO_EXISTE", EstadoSenal.VERIFICADA)
            fail("esperaba IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("NO_EXISTE"))
        }
    }

    @Test
    fun `un json corrupto en la carpeta se ignora sin romper disponibles`() {
        carpeta().mkdirs()
        File(carpeta(), "roto.json").writeText("{ esto no es json")

        assertEquals(listOf(idApache), repositorio().disponibles().map { it.id })
    }

    @Test
    fun `un incluido corrupto se ignora`() {
        val repo = repositorio(listOf("{ roto", jsonApache))

        assertEquals(listOf(idApache), repo.disponibles().map { it.id })
    }

    @Test
    fun `id con barras o puntos dobles se rechaza`() {
        val repo = repositorio()

        for (id in listOf("../$idApache", "a/b", "a\\b", "..", "a..b")) {
            assertNull(repo.leer(id))
            try {
                repo.adoptar(id)
                fail("esperaba IllegalArgumentException para $id")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("id"))
            }
        }
        assertFalse(carpeta().exists() && carpeta().list()!!.isNotEmpty())
        assertFalse(tmp.root.list()!!.any { it.endsWith(".json") })
    }
}
