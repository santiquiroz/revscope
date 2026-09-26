package com.revscope.feature.dtc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revscope.core.designsystem.DESCRIPCION_VOLVER
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticaDtcTest {

    @get:Rule
    val compose = createComposeRule()

    private val connectionVm = mockk<ConnectionViewModel>()

    private fun scan(vararg codes: String) = DtcScan(
        activos = codes.map { DtcCode(it, DtcMode.Active) },
        pendientes = emptyList(),
        permanentes = emptyList(),
        milEncendida = codes.isNotEmpty(),
        conteoSegunEcu = codes.size,
        freezeFrame = null,
        crudo = emptyMap(),
        errores = emptyList(),
    )

    @Before
    fun setUp() {
        // Lectura «del futuro»: sigue siendo reciente cuando el ViewModel mira el reloj real.
        val detenido = ObdReading("0D", 0.0, "km/h", timestamp = System.currentTimeMillis() + 60_000)
        every { connectionVm.readings } returns MutableStateFlow(mapOf("0D" to detenido))
        coEvery { connectionVm.leerDtcCompleto(any(), any()) } returns Result.success(scan("P0122"))
        coEvery { connectionVm.borrarDtcConRelectura(any()) } returns Result.success(
            BorradoDtc("44", rechazadoPorCondiciones = false, antes = scan("P0122"), despues = scan()),
        )
    }

    private fun montar(onVolver: () -> Unit = {}) {
        val vm = DtcViewModel(mockk<IntelligenceOrchestrator>(relaxed = true), mockk<PidRegistry>(relaxed = true))
        compose.setContent {
            RevScopeTheme { DtcScreen(onNavigateBack = onVolver, connectionVm = connectionVm, vm = vm) }
        }
    }

    @Test
    fun `borrar DTC pide confirmacion antes de tocar la ECU`() {
        montar()
        compose.onNodeWithText("Leer DTCs").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Borrar DTCs").performClick()

        compose.onNodeWithText("¿Borrar los códigos de falla?").assertIsDisplayed()
        coVerify(exactly = 0) { connectionVm.borrarDtcConRelectura(any()) }

        compose.onNodeWithText("Borrar códigos").performClick()
        compose.waitForIdle()

        coVerify(exactly = 1) { connectionVm.borrarDtcConRelectura(any()) }
        compose.onNodeWithText("Códigos borrados. Antes: P0122. Después: sin códigos activos.").assertIsDisplayed()
    }

    @Test
    fun `sin lectura el boton de borrar esta deshabilitado`() {
        montar()

        compose.onNodeWithText("Borrar DTCs").assertIsNotEnabled()
    }

    @Test
    fun `la barra de DTC tiene Volver`() {
        var volvio = false
        montar(onVolver = { volvio = true })

        compose.onNodeWithContentDescription(DESCRIPCION_VOLVER).performClick()

        assertTrue(volvio)
    }
}
