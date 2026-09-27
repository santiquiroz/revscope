package com.revscope.feature.workshop.taller.modelo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.revscope.core.designsystem.CapacidadesEcuCard
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.uitesting.MatrizCaptura
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasModeloTest {

    @Test
    fun conocimientoBenelliConFuentes() = MatrizCaptura.pantalla("ConocimientoModeloContent_benelli", altoMinimoDp = 4680) {
        ConocimientoModeloContent(
            ConocimientoModeloUi(vehiculo = "Benelli TNT 150i", modelo = ModeloBenelli.conocimiento, cargando = false),
        )
    }

    @Test
    fun referenciasConTodosLosOrigenes() = MatrizCaptura.pantalla("ReferenciasContent_benelli", altoMinimoDp = 1560) {
        ReferenciasContent(
            ReferenciasUi(
                vehiculo = "Benelli TNT 150i",
                modelo = ModeloBenelli.conocimiento.nombre,
                bandas = ModeloBenelli.bandas,
                cargando = false,
            ),
        )
    }

    @Test
    fun capacidadesKLine() = MatrizCaptura.pantalla("CapacidadesEcuCard_kline", altoMinimoDp = 900) {
        Box(Modifier.fillMaxSize().background(RevScopeColors.Background).padding(16.dp)) {
            CapacidadesEcuCard(
                protocolo = "ISO 14230-4 KWP (K-line)",
                pids = listOf("04", "05", "0B", "0C", "0D", "0F", "11", "1F", "33"),
                tasaEsperada = "Baja · una petición a la vez",
                explicacion = "La Delphi MT05 anuncia pocos PIDs por K-line. Que un sensor exista en la moto no significa que la ECU lo publique por OBD2.",
            )
        }
    }
}
