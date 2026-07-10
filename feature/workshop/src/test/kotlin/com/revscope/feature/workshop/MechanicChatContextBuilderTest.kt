package com.revscope.feature.workshop

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.workshop.DiagnosticRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MechanicChatContextBuilderTest {

    private fun emptyContext() = MechanicChatContextBuilder.VehicleContext(
        profileName = null,
        profileType = null,
        fuelType = null,
        odometroKm = null,
        conectado = false,
        lecturas = emptyMap(),
        ultimoChequeo = emptyList(),
        ultimosViajes = emptyList(),
        mantenimientoProximo = null,
    )

    @Test
    fun `system prompt sin perfil describe vehiculos genericos y contexto sin datos`() {
        val prompt = MechanicChatContextBuilder.buildSystemPrompt(emptyContext())

        assertTrue(prompt.contains("especializado en vehículos"))
        assertTrue(prompt.contains("Perfil: sin configurar"))
        assertTrue(prompt.contains("Conexión: desconectado"))
        assertTrue(prompt.contains("Último chequeo: sin datos"))
        assertTrue(prompt.contains("Últimos viajes: sin datos"))
        assertTrue(prompt.contains("Mantenimiento: sin configurar"))
    }

    @Test
    fun `system prompt distingue motos de carros`() {
        val motoPrompt = MechanicChatContextBuilder.buildSystemPrompt(
            emptyContext().copy(profileType = "MOTORCYCLE"),
        )
        val carroPrompt = MechanicChatContextBuilder.buildSystemPrompt(
            emptyContext().copy(profileType = "CAR"),
        )

        assertTrue(motoPrompt.contains("especializado en motos"))
        assertTrue(carroPrompt.contains("especializado en carros"))
    }

    @Test
    fun `perfil incluye nombre tipo combustible y odometro`() {
        val context = emptyContext().copy(
            profileName = "Kawasaki Ninja 400",
            profileType = "MOTORCYCLE",
            fuelType = "EXTRA",
            odometroKm = 12345.0,
        )

        val prompt = MechanicChatContextBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("Perfil: Kawasaki Ninja 400 (motos), combustible EXTRA, odómetro app: 12,345 km"))
    }

    @Test
    fun `conexion desconectada ignora lecturas en vivo aunque existan`() {
        val context = emptyContext().copy(
            conectado = false,
            lecturas = mapOf("0C" to ObdReading("0C", 3200.0, "rpm")),
        )

        val prompt = MechanicChatContextBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("Conexión: desconectado"))
        assertTrue(!prompt.contains("RPM"))
    }

    @Test
    fun `conexion conectada solo incluye pids clave presentes en orden fijo`() {
        val context = emptyContext().copy(
            conectado = true,
            lecturas = mapOf(
                "07" to ObdReading("07", -1.5, "%"),
                "0C" to ObdReading("0C", 3200.0, "rpm"),
                "05" to ObdReading("05", 89.0, "°C"),
                "11" to ObdReading("11", 40.0, "%"), // throttle — not a key PID, must be ignored
            ),
        )

        val prompt = MechanicChatContextBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("conectado — RPM 3200 rpm, Temp 89 °C, Trim largo B1 -1.5 %"))
    }

    @Test
    fun `conexion conectada sin lecturas aun`() {
        val context = emptyContext().copy(conectado = true, lecturas = emptyMap())

        val prompt = MechanicChatContextBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("Conexión: conectado — sin lecturas aún"))
    }

    @Test
    fun `ultimo chequeo resume titulo y nivel de cada item`() {
        val context = emptyContext().copy(
            ultimoChequeo = listOf(
                MechanicChatContextBuilder.ChequeoItem("Sin códigos de falla", DiagnosticRules.Nivel.OK),
                MechanicChatContextBuilder.ChequeoItem("Voltaje 13.8V", DiagnosticRules.Nivel.OK),
            ),
        )

        val prompt = MechanicChatContextBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("Último chequeo: Sin códigos de falla (OK); Voltaje 13.8V (OK)"))
    }

    @Test
    fun `ultimos viajes muestra fecha distancia y eco score`() {
        val context = emptyContext().copy(
            ultimosViajes = listOf(
                MechanicChatContextBuilder.ViajeResumen(1_752_000_000_000L, 45.3f, 78),
                MechanicChatContextBuilder.ViajeResumen(1_751_800_000_000L, 12.0f, null),
            ),
        )

        val prompt = MechanicChatContextBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("45 km, eco 78"))
        assertTrue(prompt.contains("12 km, eco sin dato"))
    }

    @Test
    fun `mantenimiento proximo pasa el texto tal cual`() {
        val context = emptyContext().copy(mantenimientoProximo = "Aceite en 420 km")

        val prompt = MechanicChatContextBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("Mantenimiento: Aceite en 420 km"))
    }

    @Test
    fun `build user message sin historial es solo la pregunta`() {
        val message = MechanicChatContextBuilder.buildUserMessage(emptyList(), "¿por qué vibra al frenar?")

        assertEquals("Usuario: ¿por qué vibra al frenar?", message)
    }

    @Test
    fun `build user message concatena historial antes de la nueva pregunta`() {
        val history = listOf(
            MechanicChatContextBuilder.ChatTurn("¿qué aceite uso?", "20W-50 semisintético"),
        )

        val message = MechanicChatContextBuilder.buildUserMessage(history, "¿cada cuánto lo cambio?")

        assertEquals(
            "Usuario: ¿qué aceite uso?\nMecánico: 20W-50 semisintético\nUsuario: ¿cada cuánto lo cambio?",
            message,
        )
    }

    @Test
    fun `build user message conserva solo los ultimos 8 turnos`() {
        val history = (1..10).map {
            MechanicChatContextBuilder.ChatTurn("pregunta $it", "respuesta $it")
        }

        val message = MechanicChatContextBuilder.buildUserMessage(history, "última pregunta")

        assertTrue(!message.contains("pregunta 1\n"))
        assertTrue(!message.contains("pregunta 2\n"))
        assertTrue(message.contains("pregunta 3"))
        assertTrue(message.contains("pregunta 10"))
        assertTrue(message.endsWith("Usuario: última pregunta"))
    }
}
