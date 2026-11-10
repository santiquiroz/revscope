package com.revscope.feature.workshop

import com.revscope.core.data.db.entities.FuelUpEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.trip.IntervaloTanqueo
import com.revscope.core.obd.trip.MotivoIgnorado
import com.revscope.core.obd.trip.ResultadoCalibracion
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasTanqueosTest {

    @Test
    fun tanqueosContent() = MatrizCaptura.pantalla("TanqueosContent", altoMinimoDp = 1400) {
        val usados = (0..2).map { IntervaloTanqueo(fechaBase + it * dia * 2, fechaBase + (it * 2 + 1) * dia, 9.3, 10.0, false) }
        val ignorado = IntervaloTanqueo(fechaBase + dia * 6, fechaBase + dia * 7, 12.0, 16.0, false)
        val resultado = ResultadoCalibracion(usados, listOf(ignorado to MotivoIgnorado.DESVIO), 1.0, 0.93, 0, 10.0 / 9.3 - 1)
        TanqueosContent(
            estado = TanqueosEstado(
                perfil = VehicleProfileEntity(id = 1, name = "Mazda CX-30", type = "CAR", vin = null, enabledPids = "", gearRatios = null, createdAt = fechaBase),
                tanqueos = listOf(
                    FuelUpEntity(1, 1, fechaBase, 38.0, true),
                    FuelUpEntity(2, 1, fechaBase + dia, 8.0, false),
                    FuelUpEntity(3, 1, fechaBase + dia * 3, 40.0, true),
                    FuelUpEntity(4, 1, fechaBase + dia * 5, 39.0, true),
                    FuelUpEntity(5, 1, fechaBase + dia * 7, 41.0, true),
                ),
                intervalos = usados.map { it to null } + (ignorado to MotivoIgnorado.DESVIO),
                calibracion = resultado,
                enGalones = false,
            ),
            onAgregar = { _, _ -> true },
            onBorrar = {},
            onAplicarFactor = {},
            onVolver = {},
            onElegirVehiculo = {},
        )
    }

    private companion object {
        const val fechaBase = 1_790_000_000_000L
        const val dia = 86_400_000L
    }
}
