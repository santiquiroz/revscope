package com.revscope.core.obd.taller.modelo

import com.revscope.core.data.db.AppDatabase
import com.revscope.core.obd.taller.baseDeDatosEnMemoria
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.repositorioTaller
import com.revscope.core.obd.taller.sesion.TallerRepositoryRoom
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SembradorConocimientoTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: TallerRepositoryRoom
    private lateinit var sembrador: SembradorConocimiento

    @Before
    fun setUp() {
        db = baseDeDatosEnMemoria()
        repositorio = db.repositorioTaller()
        sembrador = SembradorConocimiento(repositorio)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `siembra la Benelli del catálogo real con repuestos, cableado y notas`() = runTest {
        val acciones = sembrador.sembrar(CatalogoDePrueba.textoReal())

        val benelli = repositorio.conocimiento(CatalogoDePrueba.CLAVE_BENELLI)!!
        assertEquals(AccionSemilla.INSERTAR, acciones[CatalogoDePrueba.CLAVE_BENELLI])
        assertEquals(7, benelli.repuestos.size)
        assertEquals("TPS", benelli.cableado.single().sensor)
        assertTrue(benelli.notas.any { it.tipo == TipoNota.EJEMPLO })
        assertFalse(benelli.editadoPorUsuario)
    }

    @Test
    fun `sembrar dos veces es idempotente`() = runTest {
        sembrador.sembrar(CatalogoDePrueba.textoReal())
        val antes = repositorio.conocimiento(CatalogoDePrueba.CLAVE_BENELLI)

        val acciones = sembrador.sembrar(CatalogoDePrueba.textoReal())

        assertEquals(AccionSemilla.OMITIR_AL_DIA, acciones[CatalogoDePrueba.CLAVE_BENELLI])
        assertEquals(antes, repositorio.conocimiento(CatalogoDePrueba.CLAVE_BENELLI))
        assertEquals(7, db.vehicleKnowledgeDao().getParts(CatalogoDePrueba.CLAVE_BENELLI).size)
    }

    @Test
    fun `una seedVersion mayor actualiza el modelo y reemplaza sus repuestos`() = runTest {
        sembrador.sembrar(CatalogoDePrueba.conVersion(1, nombre = "Versión 1", referenciaTps = "111"))

        val acciones = sembrador.sembrar(CatalogoDePrueba.conVersion(2, nombre = "Versión 2", referenciaTps = "222"))

        val modelo = repositorio.conocimiento("moto-prueba")!!
        assertEquals(AccionSemilla.ACTUALIZAR, acciones["moto-prueba"])
        assertEquals("Versión 2", modelo.nombre)
        assertEquals(listOf("222"), modelo.repuestos.map { it.referencia })
        assertEquals(EstadoSemilla(2, editadoPorUsuario = false), repositorio.estadoSemilla("moto-prueba"))
    }

    @Test
    fun `una seedVersion menor o igual no toca el modelo`() = runTest {
        sembrador.sembrar(CatalogoDePrueba.conVersion(2, nombre = "Versión 2"))

        val acciones = sembrador.sembrar(CatalogoDePrueba.conVersion(1, nombre = "Versión 1"))

        assertEquals(AccionSemilla.OMITIR_AL_DIA, acciones["moto-prueba"])
        assertEquals("Versión 2", repositorio.conocimiento("moto-prueba")!!.nombre)
    }

    @Test
    fun `nunca pisa un modelo editado por el usuario aunque la semilla sea más nueva`() = runTest {
        sembrador.sembrar(CatalogoDePrueba.conVersion(1, nombre = "Versión 1", referenciaTps = "111"))
        marcarEditadoPorUsuario("moto-prueba", nombre = "Mi moto")

        val acciones = sembrador.sembrar(CatalogoDePrueba.conVersion(5, nombre = "Versión 5", referenciaTps = "555"))

        val modelo = repositorio.conocimiento("moto-prueba")!!
        assertEquals(AccionSemilla.OMITIR_EDITADO, acciones["moto-prueba"])
        assertEquals("Mi moto", modelo.nombre)
        assertEquals(listOf("111"), modelo.repuestos.map { it.referencia })
        assertTrue(modelo.editadoPorUsuario)
    }

    @Test
    fun `al actualizar conserva la banda editada por el usuario`() = runTest {
        sembrador.sembrar(CatalogoDePrueba.conVersion(1))
        val propia = BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.4, 0.9, "V", OrigenBanda.USUARIO)
        repositorio.guardarBandaUsuario("moto-prueba", propia)

        sembrador.sembrar(CatalogoDePrueba.conVersion(2))

        assertEquals(listOf(propia), repositorio.conocimiento("moto-prueba")!!.bandas)
    }

    @Test
    fun `decidir la acción depende de la existencia, la edición y la versión`() {
        assertEquals(AccionSemilla.INSERTAR, decidirAccionSemilla(null, 1))
        assertEquals(AccionSemilla.OMITIR_EDITADO, decidirAccionSemilla(EstadoSemilla(1, true), 9))
        assertEquals(AccionSemilla.OMITIR_AL_DIA, decidirAccionSemilla(EstadoSemilla(3, false), 3))
        assertEquals(AccionSemilla.ACTUALIZAR, decidirAccionSemilla(EstadoSemilla(2, false), 3))
    }

    private suspend fun marcarEditadoPorUsuario(clave: String, nombre: String) {
        val dao = db.vehicleKnowledgeDao()
        dao.upsertKnowledge(dao.getKnowledge(clave)!!.copy(displayName = nombre, userEdited = true))
    }
}
