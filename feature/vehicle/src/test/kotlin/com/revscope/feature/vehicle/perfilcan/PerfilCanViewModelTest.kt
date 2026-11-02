package com.revscope.feature.vehicle.perfilcan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.perfilcan.ClavesSenal
import com.revscope.core.obd.perfilcan.EstadoSenal
import com.revscope.core.obd.perfilcan.FuenteSenal
import com.revscope.core.obd.perfilcan.ModoFuente
import com.revscope.core.obd.perfilcan.OrdenBytes
import com.revscope.core.obd.perfilcan.PerfilCan
import com.revscope.core.obd.perfilcan.PerfilesCanRepositorio
import com.revscope.core.obd.perfilcan.SenalCan
import com.revscope.core.obd.perfilcan.TipoSenal
import com.revscope.core.obd.perfilcan.VehiculoPerfil
import com.revscope.core.obd.perfilcan.fuentesPorSenalDe
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.pid.CapacidadesEcu
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilCanViewModelTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val despachador = UnconfinedTestDispatcher()
    private val scopeAjustes = CoroutineScope(despachador + SupervisorJob())
    private val conexion = MutableStateFlow<ConnectionState>(ConnectionState.Connected("OBDII"))
    private var guardado = vehiculo()
    private val dao = mockk<VehicleProfileDao>(relaxed = true)
    private val repositorio = mockk<PerfilesCanRepositorio>(relaxed = true)
    private val sesion = mockk<ObdSessionManager>(relaxed = true)
    private lateinit var ajustes: DataStore<Preferences>

    @Before
    fun setUp() {
        Dispatchers.setMain(despachador)
        ajustes = PreferenceDataStoreFactory.create(scope = scopeAjustes) { carpeta.newFile("ajustes.preferences_pb") }
        coEvery { dao.getById(ID_VEHICULO) } answers { guardado }
        val actualizado = slot<VehicleProfileEntity>()
        coEvery { dao.update(capture(actualizado)) } answers { guardado = actualizado.captured }
        every { repositorio.cambios } returns MutableStateFlow(0)
        every { repositorio.disponibles() } returns listOf(PERFIL)
        every { repositorio.leer(PERFIL.id) } returns PERFIL
        every { repositorio.adoptar(PERFIL.id) } returns PERFIL
        every { sesion.connectionState } returns conexion
        every { sesion.capacidadesEcu } returns MutableStateFlow(CapacidadesEcu.desde(null, emptySet()))
        every { sesion.puedeProbarBus() } returns true
        every { sesion.probarSenal(any()) } returns flowOf(null, "3")
    }

    @After
    fun tearDown() {
        scopeAjustes.cancel()
        Dispatchers.resetMain()
    }

    private fun vm() = PerfilCanViewModel(dao, repositorio, sesion, ajustes, despachador).also { it.cargar(ID_VEHICULO) }

    @Test
    fun `elegir perfil adopta y guarda perfilCanId`() {
        val vm = vm()

        vm.elegirPerfil(PERFIL.id)

        verify { repositorio.adoptar(PERFIL.id) }
        assertEquals(PERFIL.id, guardado.perfilCanId)
        assertEquals(ModoFuente.COMBINADO.name, guardado.fuenteDatos)
        verify { sesion.notifyProfileUpdated(guardado) }
        assertEquals(PERFIL.id, vm.datos.value?.perfilId)
    }

    @Test
    fun `cambiar modo persiste`() {
        guardado = vehiculo(perfilCanId = PERFIL.id, fuenteDatos = ModoFuente.COMBINADO.name)
        val vm = vm()

        vm.elegirModo(ModoFuente.SOLO_BUS)

        assertEquals(ModoFuente.SOLO_BUS.name, guardado.fuenteDatos)
        assertEquals(ModoFuente.SOLO_BUS, vm.datos.value?.modo)
    }

    @Test
    fun `quitar perfil vuelve a estandar`() {
        guardado = vehiculo(perfilCanId = PERFIL.id, fuenteDatos = ModoFuente.SOLO_BUS.name)
        val vm = vm()

        vm.elegirPerfil(null)

        assertNull(guardado.perfilCanId)
        assertEquals(ModoFuente.ESTANDAR.name, guardado.fuenteDatos)
    }

    @Test
    fun `0D en GPS escribe SPEED_SOURCE_GPS`() = runTest(despachador) {
        guardado = vehiculo(perfilCanId = PERFIL.id, fuenteDatos = ModoFuente.COMBINADO.name)
        val vm = vm()

        vm.elegirFuente(ClavesSenal.VELOCIDAD, FuenteSenal.GPS)

        assertTrue(ajustes.data.first { it[PreferencesKeys.SPEED_SOURCE_GPS] == true }[PreferencesKeys.SPEED_SOURCE_GPS]!!)
        assertEquals(FuenteSenal.GPS, fuentesPorSenalDe(guardado.fuentesPorSenal)[ClavesSenal.VELOCIDAD])

        vm.elegirFuente(ClavesSenal.VELOCIDAD, FuenteSenal.AUTO)

        assertFalse(ajustes.data.first { it[PreferencesKeys.SPEED_SOURCE_GPS] == false }[PreferencesKeys.SPEED_SOURCE_GPS]!!)
        assertNull(fuentesPorSenalDe(guardado.fuentesPorSenal)[ClavesSenal.VELOCIDAD])
    }

    @Test
    fun `solo velocidad ofrece GPS y solo marcha ofrece calculada`() {
        assertEquals(
            listOf(FuenteSenal.AUTO, FuenteSenal.PID, FuenteSenal.BUS, FuenteSenal.GPS),
            TextosBusCan.opciones(ClavesSenal.VELOCIDAD),
        )
        assertEquals(listOf(FuenteSenal.AUTO, FuenteSenal.BUS, FuenteSenal.CALCULADA), TextosBusCan.opciones(ClavesSenal.GEAR))
        assertEquals(listOf(FuenteSenal.AUTO, FuenteSenal.BUS), TextosBusCan.opciones(ClavesSenal.PATA_LATERAL))
    }

    @Test
    fun `confirmar en vivo marca verificada`() {
        guardado = vehiculo(perfilCanId = PERFIL.id, fuenteDatos = ModoFuente.COMBINADO.name)
        val vm = vm()

        vm.abrirPrueba(ID_VEHICULO, ClavesSenal.GEAR)
        assertEquals("3", vm.prueba.value?.valor)
        assertEquals("ID 500 · byte 4 · sin escala", vm.prueba.value?.detalle)
        vm.responderPrueba(coincide = true)

        verify { repositorio.marcarEstado(PERFIL.id, ClavesSenal.GEAR, EstadoSenal.VERIFICADA) }
        assertEquals(true, vm.prueba.value?.coincide)
    }

    @Test
    fun `no coincide deja la señal sin verificar`() {
        guardado = vehiculo(perfilCanId = PERFIL.id, fuenteDatos = ModoFuente.COMBINADO.name)
        val vm = vm()

        vm.abrirPrueba(ID_VEHICULO, ClavesSenal.GEAR)
        vm.responderPrueba(coincide = false)

        verify { repositorio.marcarEstado(PERFIL.id, ClavesSenal.GEAR, EstadoSenal.SIN_VERIFICAR) }
    }

    @Test
    fun `sin conexion la prueba queda deshabilitada`() {
        guardado = vehiculo(perfilCanId = PERFIL.id, fuenteDatos = ModoFuente.COMBINADO.name)
        conexion.value = ConnectionState.Disconnected
        every { sesion.puedeProbarBus() } returns false
        val vm = vm()

        vm.abrirPrueba(ID_VEHICULO, ClavesSenal.GEAR)
        vm.responderPrueba(coincide = true)

        val prueba = vm.prueba.value!!
        assertFalse(prueba.disponible)
        assertEquals(TextosBusCan.SIN_CONEXION, prueba.motivoSinPrueba)
        assertEquals(TextosBusCan.SIN_CONEXION, vm.datos.value?.motivoSinPrueba)
        verify(exactly = 0) { sesion.probarSenal(any()) }
        verify(exactly = 0) { repositorio.marcarEstado(any(), any(), any()) }
    }

    private companion object {
        const val ID_VEHICULO = 7L

        val MARCHA = SenalCan(
            clave = ClavesSenal.GEAR,
            nombre = "Marcha",
            id = "500",
            bitInicio = 32,
            largo = 8,
            orden = OrdenBytes.BIG,
            firmado = false,
            factor = 1.0,
            offset = 0.0,
            unidad = "",
            tipo = TipoSenal.ENUM,
            valores = mapOf(0L to "N", 1L to "1", 2L to "2", 3L to "3"),
            invalidos = setOf(7L),
            periodoMs = 100,
            estado = EstadoSenal.SIN_VERIFICAR,
            origen = "incluido",
        )

        val PERFIL = PerfilCan(
            version = 1,
            id = "tvs.apache_rtr_160_4v.2024",
            vehiculo = VehiculoPerfil("TVS", "Apache RTR 160 4V", "2024", "MOTORCYCLE"),
            calibracionesEcu = emptyList(),
            protocolo = "6",
            licencia = "CC-BY-SA-4.0",
            autor = "prueba",
            atribucion = "prueba",
            senales = listOf(MARCHA),
        )

        fun vehiculo(perfilCanId: String? = null, fuenteDatos: String = ModoFuente.ESTANDAR.name) = VehicleProfileEntity(
            id = ID_VEHICULO,
            name = "Apache",
            type = "MOTORCYCLE",
            vin = null,
            enabledPids = "[]",
            gearRatios = null,
            createdAt = 0L,
            perfilCanId = perfilCanId,
            fuenteDatos = fuenteDatos,
        )
    }
}
