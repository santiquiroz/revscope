package com.revscope.feature.session

import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasViajesTest {

    @Test
    fun sessionItem() = MatrizCaptura.componente("SessionItem") {
        SessionItem(
            session = SessionEntity(
                vehicleProfileId = 1,
                startedAt = 1_790_000_000_000,
                endedAt = 1_790_000_000_000 + 47 * 60_000 + 12_000,
                adapterName = "Android-Vlink",
                maxRpm = 9948,
                maxSpeed = 112,
                distanceKm = 38.4f,
            ),
            isCompareCandidate = false,
            onClick = {},
            onCompare = {},
            onDelete = {},
        )
    }

    @Test
    fun confirmarBorrarViajeDialog() = MatrizCaptura.dialogo("ConfirmarBorrarViajeDialog") {
        ConfirmarBorrarViajeDialog(
            sesion = SessionEntity(
                vehicleProfileId = 1,
                startedAt = 1_790_000_000_000,
                endedAt = 1_790_000_000_000 + 30 * 60_000,
                adapterName = "Android-Vlink",
                maxRpm = 9_000,
                maxSpeed = 80,
                distanceKm = 12.3f,
            ),
            onConfirmar = {},
            onCancelar = {},
        )
    }

    @Test
    fun historialVacio() = MatrizCaptura.pantalla("SessionHistoryContent_vacio") {
        SessionHistoryContent(
            historial = HistorialUi(emptyList(), emptyList(), filtro = null, candidatoComparar = null),
            acciones = AccionesHistorial({}, {}, {}, {}, {}),
        )
    }
}
