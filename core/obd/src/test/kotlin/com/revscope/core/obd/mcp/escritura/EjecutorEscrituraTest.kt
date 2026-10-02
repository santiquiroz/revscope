package com.revscope.core.obd.mcp.escritura

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class EjecutorEscrituraTest {

    @get:Rule
    val temporal = TemporaryFolder()

    @Test
    fun `ejecuta pasos autorizados y registra las respuestas`() = runTest {
        val fixture = fixture(Autorizacion.Concedida(OrigenAutorizacion.TOQUE))
        val respuestas = listOf("54", "51 03")

        val resultado = fixture.ejecutor.ejecutar(solicitud) { respuestas }

        assertEquals(ResultadoEscritura.Ejecutada(OrigenAutorizacion.TOQUE, respuestas), resultado)
        assertEquals("TOQUE", fixture.registro.ultimas(1).single().autorizacion)
        assertEquals(respuestas, fixture.registro.ultimas(1).single().respuestas)
    }

    @Test
    fun `vehiculo en movimiento deniega antes de autorizar`() = runTest {
        val fixture = fixture(Autorizacion.Concedida(OrigenAutorizacion.TOQUE))
        fixture.lecturas.value = lecturas(velocidad = 12.0)

        val resultado = fixture.ejecutor.ejecutar(solicitud) { error("No debe ejecutar") }

        assertTrue(resultado is ResultadoEscritura.NoAutorizada)
        coVerify(exactly = 0) { fixture.autorizador.autorizar(any()) }
        assertEquals("GUARDA", fixture.registro.ultimas(1).single().autorizacion)
    }

    @Test
    fun `comando bloqueado deniega antes de autorizar`() = runTest {
        val fixture = fixture(Autorizacion.Concedida(OrigenAutorizacion.TOQUE))

        val resultado = fixture.ejecutor.ejecutar(solicitud.copy(pasos = listOf("34 00 00"))) { emptyList() }

        assertTrue(resultado is ResultadoEscritura.NoAutorizada)
        assertTrue((resultado as ResultadoEscritura.NoAutorizada).motivo.contains("bloqueado"))
        coVerify(exactly = 0) { fixture.autorizador.autorizar(any()) }
    }

    @Test
    fun `paso hexadecimal invalido se deniega`() = runTest {
        val fixture = fixture(Autorizacion.Concedida(OrigenAutorizacion.TOQUE))

        val resultado = fixture.ejecutor.ejecutar(solicitud.copy(pasos = listOf("ZZ"))) { emptyList() }

        assertTrue(resultado is ResultadoEscritura.NoAutorizada)
        assertTrue((resultado as ResultadoEscritura.NoAutorizada).motivo.contains("inválido"))
        coVerify(exactly = 0) { fixture.autorizador.autorizar(any()) }
    }

    @Test
    fun `registra rechazo del dueño`() = verificaDenegacion(
        Autorizacion.Rechazada,
        autorizacionRegistro = "RECHAZADA",
        motivo = "rechazó",
    )

    @Test
    fun `registra falta de respuesta`() = verificaDenegacion(
        Autorizacion.SinRespuesta,
        autorizacionRegistro = "SIN_RESPUESTA",
        motivo = "sin respuesta",
    )

    @Test
    fun `registra notificaciones bloqueadas`() = verificaDenegacion(
        Autorizacion.NotificacionesBloqueadas,
        autorizacionRegistro = "NOTIFICACIONES_BLOQUEADAS",
        motivo = "notificaciones",
    )

    @Test
    fun `registra fallo de la concesion de diagnostico`() = runTest {
        val fixture = fixture(Autorizacion.Concedida(OrigenAutorizacion.TOQUE))
        coEvery {
            fixture.manager.withDiagnosticLease<Any?>(any(), any(), any())
        } returns Result.failure<Any?>(IOException("timeout"))

        val resultado = fixture.ejecutor.ejecutar(solicitud) { emptyList() }

        assertEquals(ResultadoEscritura.Fallida(OrigenAutorizacion.TOQUE, "timeout"), resultado)
        val registro = fixture.registro.ultimas(1).single()
        assertEquals("timeout", registro.error)
        assertEquals("TOQUE", registro.autorizacion)
    }

    @Test
    fun `motor encendido deniega una escritura que requiere motor apagado`() = runTest {
        val fixture = fixture(Autorizacion.Concedida(OrigenAutorizacion.TOQUE))
        fixture.lecturas.value = lecturas(rpm = 900.0)

        val resultado = fixture.ejecutor.ejecutar(solicitud.copy(requiereMotorApagado = true)) { emptyList() }

        assertTrue(resultado is ResultadoEscritura.NoAutorizada)
        coVerify(exactly = 0) { fixture.autorizador.autorizar(any()) }
        assertEquals("GUARDA", fixture.registro.ultimas(1).single().autorizacion)
    }

    @Test
    fun `json representa ejecucion fallo y denegacion`() {
        val ejecutada = EscrituraJson.de(ResultadoEscritura.Ejecutada(OrigenAutorizacion.TOQUE, listOf("54")))
        val fallida = EscrituraJson.de(ResultadoEscritura.Fallida(OrigenAutorizacion.BYPASS, "timeout"))
        val denegada = EscrituraJson.de(ResultadoEscritura.NoAutorizada("rechazada"))

        assertTrue(ejecutada.getBoolean("ejecutada"))
        assertEquals("toque en el teléfono", ejecutada.getString("autorizadoPor"))
        assertFalse(fallida.getBoolean("ejecutada"))
        assertEquals("bypass activo", fallida.getString("autorizadoPor"))
        assertFalse(denegada.getBoolean("ejecutada"))
        assertEquals("rechazada", denegada.getString("motivo"))
    }

    private fun verificaDenegacion(
        autorizacion: Autorizacion,
        autorizacionRegistro: String,
        motivo: String,
    ) = runTest {
        val fixture = fixture(autorizacion)

        val resultado = fixture.ejecutor.ejecutar(solicitud) { error("No debe ejecutar") }

        assertTrue(resultado is ResultadoEscritura.NoAutorizada)
        assertTrue((resultado as ResultadoEscritura.NoAutorizada).motivo.contains(motivo, ignoreCase = true))
        assertEquals(autorizacionRegistro, fixture.registro.ultimas(1).single().autorizacion)
    }

    private fun fixture(autorizacion: Autorizacion): Fixture {
        val manager = mockk<ObdSessionManager>()
        val lecturas = MutableStateFlow(lecturas())
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.Connected("Vlink"))
        every { manager.readings } returns lecturas
        val transporte = mockk<Transport>(relaxed = true)
        coEvery {
            manager.withDiagnosticLease<Any?>(any(), any(), any())
        } coAnswers {
            Result.success(thirdArg<suspend (Transport) -> Any?>().invoke(transporte))
        }
        val autorizador = mockk<AutorizadorEscritura>()
        coEvery { autorizador.autorizar(any()) } returns autorizacion
        val registro = RegistroEscrituras(File(temporal.root, "escrituras.jsonl"))
        val ejecutor = EjecutorEscritura(manager, autorizador, registro) { ahoraMs }
        return Fixture(manager, lecturas, autorizador, registro, ejecutor)
    }

    private fun lecturas(velocidad: Double = 0.0, rpm: Double? = 0.0) = buildMap {
        put("0D", ObdReading("0D", velocidad, "km/h", ahoraMs - 100))
        rpm?.let { put("0C", ObdReading("0C", it, "rpm", ahoraMs - 100)) }
    }

    private data class Fixture(
        val manager: ObdSessionManager,
        val lecturas: MutableStateFlow<Map<String, ObdReading>>,
        val autorizador: AutorizadorEscritura,
        val registro: RegistroEscrituras,
        val ejecutor: EjecutorEscritura,
    )

    private companion object {
        const val ahoraMs = 100_000L
        val solicitud = SolicitudEscritura(
            tool = "comando_escritura",
            resumen = "prueba",
            header = null,
            pasos = listOf("14 FF FF FF", "11 03"),
        )
    }
}
