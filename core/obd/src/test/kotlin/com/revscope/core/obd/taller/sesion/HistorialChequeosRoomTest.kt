package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.AppDatabase
import com.revscope.core.data.db.entities.HealthReportEntity
import com.revscope.core.obd.taller.baseDeDatosEnMemoria
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistorialChequeosRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var historial: HistorialChequeosRoom

    @Before
    fun setUp() {
        db = baseDeDatosEnMemoria()
        historial = HistorialChequeosRoom(db.healthReportDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun guardar(vehiculo: Long, instante: Long): Long =
        db.healthReportDao().insert(HealthReportEntity(vehicleProfileId = vehiculo, timestamp = instante, resultsJson = "[]"))

    @Test
    fun `los recientes son del vehículo, anteriores al instante y del más nuevo al más viejo`() = runTest {
        val julio = guardar(vehiculo = 7, instante = 100)
        val agosto = guardar(vehiculo = 7, instante = 200)
        guardar(vehiculo = 8, instante = 250)
        val septiembre = guardar(vehiculo = 7, instante = 300)
        guardar(vehiculo = 7, instante = 400)

        val recientes = historial.recientesAntesDe(vehiculoId = 7, instante = 350, limite = 5)

        assertEquals(listOf(septiembre, agosto, julio), recientes.map { it.id })
    }

    @Test
    fun `el límite corta la lista`() = runTest {
        (1..6).forEach { guardar(vehiculo = 7, instante = it * 10L) }

        assertEquals(listOf(60L, 50L), historial.recientesAntesDe(7, 1_000, limite = 2).map { it.instante })
    }
}
