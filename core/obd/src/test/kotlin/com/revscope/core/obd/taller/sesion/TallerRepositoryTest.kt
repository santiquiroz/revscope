package com.revscope.core.obd.taller.sesion

import android.database.sqlite.SQLiteConstraintException
import com.revscope.core.data.db.AppDatabase
import com.revscope.core.data.db.entities.VehicleKnowledgeEntity
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.baseDeDatosEnMemoria
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.repositorioTaller
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TallerRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: TallerRepository

    private val sesionBenelli = SesionTaller(
        vehiculoId = 7,
        claveModelo = "benelli-tnt150i-2022",
        inicio = 1_758_848_000_000,
        titulo = "Señal del TPS fuera de rango",
        sintomas = setOf(Sintoma.NO_SOSTIENE_MINIMO_FRIO, Sintoma.SE_APAGA_AL_SOLTAR, Sintoma.SE_AHOGA_AL_ACELERAR),
        sintomasTexto = "El TPS se cambió hace unos meses",
        odometroKm = 12_345.5,
        chequeoBaseId = 3,
        pasosMarcados = setOf("P0122#1", "P0122#2"),
    )

    @Before
    fun setUp() {
        db = baseDeDatosEnMemoria()
        repositorio = db.repositorioTaller()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `una sesión abierta se guarda y se lee igual`() = runTest {
        val id = repositorio.abrirSesion(sesionBenelli)

        assertEquals(sesionBenelli.copy(id = id), repositorio.sesion(id))
        assertEquals(sesionBenelli.copy(id = id), repositorio.sesionAbierta(7))
        assertEquals(id, repositorio.observarSesionAbierta(7).first()?.id)
    }

    @Test
    fun `abrir una sesión cierra la que seguía abierta en ese vehículo y no toca otros`() = runTest {
        val anterior = repositorio.abrirSesion(sesionBenelli)
        val otroVehiculo = repositorio.abrirSesion(sesionBenelli.copy(vehiculoId = 8))

        val nueva = repositorio.abrirSesion(sesionBenelli.copy(inicio = sesionBenelli.inicio + 60_000, titulo = "Segunda"))

        assertEquals(sesionBenelli.inicio + 60_000, repositorio.sesion(anterior)?.cierre)
        assertEquals(nueva, repositorio.sesionAbierta(7)?.id)
        assertTrue(repositorio.sesion(otroVehiculo)!!.abierta)
        assertEquals(listOf(nueva, anterior), repositorio.observarSesiones(7).first().map { it.id })
    }

    @Test
    fun `cerrar deja la sesión sin abrir y un segundo cierre no cambia la hora`() = runTest {
        val id = repositorio.abrirSesion(sesionBenelli)

        assertTrue(repositorio.cerrarSesion(id, 1_758_850_000_000))
        assertFalse(repositorio.cerrarSesion(id, 1_758_860_000_000))

        assertEquals(1_758_850_000_000, repositorio.sesion(id)?.cierre)
        assertNull(repositorio.sesionAbierta(7))
    }

    @Test
    fun `actualizar guarda interpretación y pasos marcados`() = runTest {
        val id = repositorio.abrirSesion(sesionBenelli)
        val editada = repositorio.sesion(id)!!.copy(
            interpretacion = "Señal baja en todo el recorrido",
            pasosMarcados = setOf("P0122#1", "P0122#2", "P0122#3"),
        )

        repositorio.actualizarSesion(editada)

        assertEquals(editada, repositorio.sesion(id))
    }

    @Test
    fun `los eventos salen en orden cronológico con su payload`() = runTest {
        val sesion = repositorio.abrirSesion(sesionBenelli)
        val tarde = evento(sesion, instante = 3_000, tipo = TipoEvento.MEDICION_MULTIMETRO, veredicto = Veredicto.FALLA)
        val temprano = evento(sesion, instante = 1_000, tipo = TipoEvento.DTC_LECTURA, payload = """{"codigos":["P0122"]}""")
        repositorio.agregarEvento(tarde)
        repositorio.agregarEvento(temprano)

        val eventos = repositorio.eventos(sesion)

        assertEquals(listOf(TipoEvento.DTC_LECTURA, TipoEvento.MEDICION_MULTIMETRO), eventos.map { it.tipo })
        assertEquals("""{"codigos":["P0122"]}""", eventos.first().payloadJson)
        assertEquals(Veredicto.FALLA, eventos.last().veredicto)
        assertEquals(eventos, repositorio.observarEventos(sesion).first())
    }

    @Test
    fun `borrar la sesión borra sus eventos en cascada y deja los de otras sesiones`() = runTest {
        val borrada = repositorio.abrirSesion(sesionBenelli)
        val conservada = repositorio.abrirSesion(sesionBenelli.copy(vehiculoId = 8))
        repositorio.agregarEvento(evento(borrada, instante = 1_000))
        repositorio.agregarEvento(evento(borrada, instante = 2_000))
        repositorio.agregarEvento(evento(conservada, instante = 1_500))

        assertTrue(repositorio.eliminarSesion(borrada))

        assertNull(repositorio.sesion(borrada))
        assertEquals(emptyList<EventoTaller>(), repositorio.eventos(borrada))
        assertEquals(1, repositorio.eventos(conservada).size)
        assertFalse(repositorio.eliminarSesion(borrada))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `un evento de una sesión inexistente se rechaza`() = runTest {
        repositorio.agregarEvento(evento(sesionId = 999, instante = 1_000))
    }

    @Test
    fun `sin modelo asociado las bandas resueltas son las típicas`() = runTest {
        val bandas = repositorio.bandasResueltas(null, VehicleType.MOTORCYCLE)

        assertEquals(OrigenBanda.TIPICO, bandas.getValue(ClavesBanda.TPS_CERRADO_V).origen)
    }

    @Test
    fun `la banda del usuario se resuelve con su origen y restablecer vuelve a la típica`() = runTest {
        val clave = "benelli-tnt150i-2022"
        repositorio.guardarBandaUsuario(
            clave,
            BandaReferencia(ClavesBanda.TPS_CERRADO_V, 0.4, 0.9, "V", OrigenBanda.FUENTE, fuente = "se ignora"),
        )
        insertarModeloVacio(clave)

        val editada = repositorio.bandasResueltas(clave, VehicleType.MOTORCYCLE).getValue(ClavesBanda.TPS_CERRADO_V)
        assertEquals(OrigenBanda.USUARIO, editada.origen)
        assertEquals("Editado por ti", editada.etiquetaOrigen)

        assertTrue(repositorio.restablecerBanda(clave, ClavesBanda.TPS_CERRADO_V))
        val restablecida = repositorio.bandasResueltas(clave, VehicleType.MOTORCYCLE).getValue(ClavesBanda.TPS_CERRADO_V)
        assertEquals(OrigenBanda.TIPICO, restablecida.origen)
    }

    @Test
    fun `guardar dos veces la misma banda del usuario la reemplaza`() = runTest {
        val clave = "benelli-tnt150i-2022"
        insertarModeloVacio(clave)
        val banda = BandaReferencia(ClavesBanda.TPS_FONDO_V, 4.0, 4.6, "V", OrigenBanda.USUARIO)

        repositorio.guardarBandaUsuario(clave, banda)
        repositorio.guardarBandaUsuario(clave, banda.copy(max = 4.7))

        assertEquals(listOf(banda.copy(max = 4.7)), repositorio.conocimiento(clave)!!.bandas)
    }

    private suspend fun insertarModeloVacio(clave: String) {
        db.vehicleKnowledgeDao().upsertKnowledge(
            VehicleKnowledgeEntity(
                key = clave,
                displayName = "Benelli TNT 150i (2022)",
                vehicleType = VehicleType.MOTORCYCLE.name,
            ),
        )
    }

    private fun evento(
        sesionId: Long,
        instante: Long,
        tipo: TipoEvento = TipoEvento.NOTA,
        veredicto: Veredicto = Veredicto.INFO,
        payload: String = "{}",
    ) = EventoTaller(
        sesionId = sesionId,
        instante = instante,
        tipo = tipo,
        titulo = tipo.name,
        veredicto = veredicto,
        payloadJson = payload,
    )
}
