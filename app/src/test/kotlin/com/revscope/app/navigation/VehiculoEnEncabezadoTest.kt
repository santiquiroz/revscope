package com.revscope.app.navigation

import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.descripcionSelectorVehiculo
import com.revscope.core.obd.connection.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiculoEnEncabezadoTest {

    private fun perfil(nombre: String, tipo: String) = VehicleProfileEntity(
        name = nombre,
        type = tipo,
        vin = null,
        enabledPids = "[]",
        gearRatios = null,
        createdAt = 0,
    )

    @Test
    fun `una moto conectada muestra su nombre y el adaptador en la descripcion`() {
        val vehiculo = vehiculoEnEncabezado(ConnectionState.Connected("Android-Vlink"), perfil("Benelli TNT 150i", "MOTORCYCLE"))

        assertTrue(vehiculo.esMoto)
        assertEquals(RevScopeColors.Success, vehiculo.colorEstado)
        assertEquals(
            "Vehículo: Benelli TNT 150i. Adaptador: Android-Vlink. Toca para cambiar de vehículo",
            descripcionSelectorVehiculo(vehiculo),
        )
    }

    @Test
    fun `sin perfil activo el selector lo dice en vez de quedar vacio`() {
        val vehiculo = vehiculoEnEncabezado(ConnectionState.Disconnected, null)

        assertFalse(vehiculo.esMoto)
        assertEquals(
            "Vehículo: sin vehículo. Adaptador: Sin conexión. Toca para cambiar de vehículo",
            descripcionSelectorVehiculo(vehiculo),
        )
    }
}
