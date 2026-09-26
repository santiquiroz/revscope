package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapturasTerminadasTest {

    private fun resumen(id: String) = ResumenCaptura(id, 1_000, emptyList(), null, null, "detenida por el usuario", null)

    @Test
    fun `solo emite las capturas que terminan después de suscribirse y una vez cada una`() = runTest {
        val ultimo = MutableStateFlow<ResumenCaptura?>(resumen("cap-vieja"))
        val vistas = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { ultimo.terminadasDesdeAhora().collect { vistas += it.id } }

        ultimo.value = resumen("cap-1")
        ultimo.value = resumen("cap-1").copy(motivoFin = "otra copia")
        ultimo.value = resumen("cap-2")
        job.cancel()

        assertEquals(listOf("cap-1", "cap-2"), vistas)
    }

    @Test
    fun `sin captura previa emite la primera que termina`() = runTest {
        val ultimo = MutableStateFlow<ResumenCaptura?>(null)
        val vistas = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { ultimo.terminadasDesdeAhora().collect { vistas += it.id } }

        ultimo.value = resumen("cap-1")
        job.cancel()

        assertEquals(listOf("cap-1"), vistas)
    }
}
