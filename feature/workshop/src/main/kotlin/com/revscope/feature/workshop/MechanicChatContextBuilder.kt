package com.revscope.feature.workshop

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.workshop.DiagnosticRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TYPE_MOTORCYCLE = "MOTORCYCLE"
private const val MAX_HISTORY_TURNS = 8

private const val RPM_PID = "0C"
private const val COOLANT_TEMP_PID = "05"

/** Matches ObdSessionManager.VBAT_PID — duplicated as a literal to keep this object dependency-free. */
private const val VOLTAGE_PID = "VBAT"
private const val SHORT_TRIM_B1_PID = "06"
private const val LONG_TRIM_B1_PID = "07"

private val LIVE_PID_LABELS = linkedMapOf(
    RPM_PID to "RPM",
    COOLANT_TEMP_PID to "Temp",
    VOLTAGE_PID to "Voltaje",
    SHORT_TRIM_B1_PID to "Trim corto B1",
    LONG_TRIM_B1_PID to "Trim largo B1",
)

/**
 * Pure formatting logic for the "Mecánico IA" chat — turns already-fetched vehicle data into
 * the compact context block embedded in the system prompt, and folds the in-memory
 * conversation history into a single transcript for [com.revscope.core.intelligence.provider.AiRequest.user]
 * (the shared [com.revscope.core.intelligence.provider.AiProvider] interface has no multi-turn
 * concept — every provider only sees one system + one user string).
 */
object MechanicChatContextBuilder {

    data class ChequeoItem(val titulo: String, val nivel: DiagnosticRules.Nivel)

    data class ViajeResumen(val startedAtEpochMs: Long, val distanceKm: Float, val ecoScore: Int?)

    data class VehicleContext(
        val profileName: String?,
        val profileType: String?,
        val fuelType: String?,
        val odometroKm: Double?,
        val conectado: Boolean,
        val lecturas: Map<String, ObdReading>,
        val ultimoChequeo: List<ChequeoItem>,
        val ultimosViajes: List<ViajeResumen>,
        val mantenimientoProximo: String?,
    )

    data class ChatTurn(val question: String, val answer: String)

    fun buildSystemPrompt(context: VehicleContext): String = buildString {
        append("Eres un mecánico experto y directo especializado en ${tipoVehiculoTexto(context.profileType)}. ")
        append("Responde en español, corto y práctico (máx 6 frases salvo que pidan detalle). ")
        append("Si el problema es serio o de seguridad, dilo sin rodeos y recomienda taller.")
        append("\n\nDatos actuales del vehículo del usuario:\n")
        append(buildContextBlock(context))
    }

    fun buildUserMessage(history: List<ChatTurn>, newQuestion: String): String = buildString {
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            appendLine("Usuario: ${turn.question}")
            appendLine("Mecánico: ${turn.answer}")
        }
        append("Usuario: $newQuestion")
    }

    private fun buildContextBlock(context: VehicleContext): String = buildString {
        appendLine("Perfil: ${perfilTexto(context)}")
        appendLine("Conexión: ${conexionTexto(context)}")
        appendLine("Último chequeo: ${chequeoTexto(context.ultimoChequeo)}")
        appendLine("Últimos viajes: ${viajesTexto(context.ultimosViajes)}")
        append("Mantenimiento: ${context.mantenimientoProximo ?: "sin configurar"}")
    }

    private fun tipoVehiculoTexto(profileType: String?): String = when (profileType) {
        TYPE_MOTORCYCLE -> "motos"
        null -> "vehículos"
        else -> "carros"
    }

    private fun perfilTexto(context: VehicleContext): String {
        val nombre = context.profileName ?: return "sin configurar"
        val tipo = tipoVehiculoTexto(context.profileType)
        val combustible = context.fuelType ?: "desconocido"
        val odometro = context.odometroKm?.let { String.format(Locale.US, "%,.0f km", it) } ?: "sin datos"
        return "$nombre ($tipo), combustible $combustible, odómetro app: $odometro"
    }

    private fun conexionTexto(context: VehicleContext): String {
        if (!context.conectado) return "desconectado"
        val lecturas = LIVE_PID_LABELS.mapNotNull { (pid, label) ->
            context.lecturas[pid]?.let { "$label ${formatValor(it)} ${it.unit}" }
        }
        if (lecturas.isEmpty()) return "conectado — sin lecturas aún"
        return "conectado — ${lecturas.joinToString(", ")}"
    }

    private fun formatValor(reading: ObdReading): String =
        if (reading.value % 1.0 == 0.0) {
            reading.value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", reading.value)
        }

    private fun chequeoTexto(items: List<ChequeoItem>): String {
        if (items.isEmpty()) return "sin datos"
        return items.joinToString("; ") { "${it.titulo} (${it.nivel.name})" }
    }

    private fun viajesTexto(viajes: List<ViajeResumen>): String {
        if (viajes.isEmpty()) return "sin datos"
        return viajes.joinToString("; ") { viaje ->
            val eco = viaje.ecoScore?.toString() ?: "sin dato"
            val distancia = String.format(Locale.US, "%.0f", viaje.distanceKm)
            "${fechaCorta(viaje.startedAtEpochMs)} — $distancia km, eco $eco"
        }
    }

    private fun fechaCorta(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))
}
