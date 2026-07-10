package com.revscope.core.obd.trip

import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.obd.legal.DocumentStatusCalculator.Nivel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaintenanceCalculatorTest {

    private fun item(
        nombre: String = "Aceite",
        intervaloKm: Double = 3_000.0,
        ultimoServicioKm: Double = 0.0,
    ) = MaintenanceItemEntity(
        vehicleProfileId = 1L,
        nombre = nombre,
        intervaloKm = intervaloKm,
        ultimoServicioKm = ultimoServicioKm,
    )

    @Test
    fun `odometro actual suma el base y las sesiones`() {
        assertEquals(1_250.0, MaintenanceCalculator.odometroActual(1_000.0, 250.0), 0.0001)
    }

    @Test
    fun `km restantes en 420 cuando faltan 420 km para el intervalo`() {
        val estados = MaintenanceCalculator.calculate(2_580.0, listOf(item(intervaloKm = 3_000.0)))
        assertEquals(420.0, estados.single().kmRestantes, 0.0001)
    }

    @Test
    fun `nivel vencido cuando kilometros restantes es cero o negativo`() {
        val estados = MaintenanceCalculator.calculate(3_000.0, listOf(item(intervaloKm = 3_000.0)))
        assertEquals(Nivel.VENCIDO, estados.single().nivel)
    }

    @Test
    fun `nivel vencido cuando ya se paso del intervalo`() {
        val estados = MaintenanceCalculator.calculate(3_500.0, listOf(item(intervaloKm = 3_000.0)))
        assertEquals(Nivel.VENCIDO, estados.single().nivel)
    }

    @Test
    fun `nivel atencion cuando faltan 10 por ciento o menos del intervalo`() {
        val estados = MaintenanceCalculator.calculate(2_750.0, listOf(item(intervaloKm = 3_000.0)))
        assertEquals(Nivel.ATENCION, estados.single().nivel)
    }

    @Test
    fun `nivel ok cuando faltan mas del 10 por ciento del intervalo`() {
        val estados = MaintenanceCalculator.calculate(2_580.0, listOf(item(intervaloKm = 3_000.0)))
        assertEquals(Nivel.OK, estados.single().nivel)
    }

    @Test
    fun `peor nivel es el mas severo entre varios items`() {
        val estados = MaintenanceCalculator.calculate(
            odometroActual = 3_100.0,
            items = listOf(
                item(nombre = "Aceite", intervaloKm = 3_000.0), // vencido
                item(nombre = "Llantas", intervaloKm = 10_000.0), // ok
            ),
        )
        assertEquals(Nivel.VENCIDO, MaintenanceCalculator.peorNivel(estados))
    }

    @Test
    fun `peor nivel es sin_configurar cuando no hay items`() {
        assertEquals(Nivel.SIN_CONFIGURAR, MaintenanceCalculator.peorNivel(emptyList()))
    }

    @Test
    fun `peor nivel detecta el mas severo aunque no tenga menos km restantes`() {
        // F: OK con 40 km restantes (40% del intervalo). E: ATENCION con 50 km restantes (0,05% del intervalo).
        val estados = MaintenanceCalculator.calculate(
            odometroActual = 0.0,
            items = listOf(
                item(nombre = "F", intervaloKm = 100.0, ultimoServicioKm = -60.0),
                item(nombre = "E", intervaloKm = 100_000.0, ultimoServicioKm = -99_950.0),
            ),
        )
        assertEquals(Nivel.ATENCION, MaintenanceCalculator.peorNivel(estados))
    }

    @Test
    fun `proximo es el item con menos kilometros restantes`() {
        val estados = MaintenanceCalculator.calculate(
            odometroActual = 2_580.0,
            items = listOf(
                item(nombre = "Aceite", intervaloKm = 3_000.0), // 420 km restantes
                item(nombre = "Llantas", intervaloKm = 10_000.0), // 7420 km restantes
            ),
        )
        assertEquals("Aceite", MaintenanceCalculator.proximo(estados)!!.item.nombre)
    }

    @Test
    fun `proximo es null cuando no hay items`() {
        assertNull(MaintenanceCalculator.proximo(emptyList()))
    }

    @Test
    fun `detalle texto describe el proximo item en kilometros`() {
        val estados = MaintenanceCalculator.calculate(2_580.0, listOf(item(intervaloKm = 3_000.0)))
        assertEquals("Aceite en 420 km", MaintenanceCalculator.detalleTexto(estados))
    }

    @Test
    fun `detalle texto marca vencido cuando el proximo ya paso`() {
        val estados = MaintenanceCalculator.calculate(3_000.0, listOf(item(intervaloKm = 3_000.0)))
        assertEquals("Aceite vencido", MaintenanceCalculator.detalleTexto(estados))
    }

    @Test
    fun `detalle texto es por configurar sin items`() {
        assertEquals("Por configurar", MaintenanceCalculator.detalleTexto(emptyList()))
    }
}
