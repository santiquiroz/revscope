package com.revscope.feature.settings

import com.revscope.core.obd.protocol.ResponseParser

/**
 * Lógica pura para descubrir módulos por header CAN de 11 bits y para dirigir
 * el escaneo Modo 22 a un módulo distinto de la ECU de motor.
 */
object ModuleDiscovery {

    data class Candidate(val header: String, val label: String)
    data class ProbeResult(val requestHeader: String, val replyHeader: String?, val present: Boolean)

    private val HEADER_11BIT = Regex("^[0-9A-Fa-f]{3}$")

    /** Header de petición 11-bit válido = exactamente 3 dígitos hex. */
    fun isValid11BitHeader(header: String): Boolean = HEADER_11BIT.matches(header.trim())

    /**
     * True solo en ISO 15765-4 CAN de 11 bits (protocolos 6 y 8).
     * AT DPN puede devolver "A6" (auto encontró 6) o "6"; tomamos el último dígito.
     * 7/9 = 29-bit; 1-5 = no-CAN. Restauramos a 7DF, válido solo en 11-bit.
     */
    fun isCan11Bit(protocolNumber: String?): Boolean {
        val p = protocolNumber?.trim()?.uppercase()?.removePrefix("A") ?: return false
        return p == "6" || p == "8"
    }

    /**
     * Lista curada de headers 11-bit a sondear. Las ECU OBD estándar viven en
     * 7E0–7E7; los módulos de carrocería/chasis usan direcciones propietarias
     * (varían por marca — etiqueta genérica + hex).
     */
    fun candidateHeaders(): List<Candidate> = buildList {
        add(Candidate("7DF", "Difusión funcional (todas las ECU)"))
        for (i in 0..7) add(Candidate("7E${i}", "ECU física 7E$i (motor/trans/ABS…)"))
        listOf("700", "710", "720", "726", "730", "740", "745", "750", "760", "765", "770", "7A0", "7B0", "7C0")
            .forEach { add(Candidate(it, "Módulo propietario $it")) }
    }

    /**
     * Interpreta la respuesta cruda (con header, porque H1 está activo) de un sondeo.
     * present = el módulo contestó ALGO — incluye una respuesta negativa UDS "7F"
     * (el módulo existe pero rechazó el DID). Solo NO DATA / error / vacío = ausente.
     */
    fun interpretProbe(requestHeader: String, raw: String): ProbeResult {
        val clean = ResponseParser.cleanResponse(raw)
        if (clean.isEmpty() || ResponseParser.isErrorResponse(raw)) {
            return ProbeResult(requestHeader, replyHeader = null, present = false)
        }
        // Con H1, un frame CAN 11-bit empieza por el header de respuesta (3 hex).
        val replyHeader = clean.take(3).takeIf { HEADER_11BIT.matches(it) }
        return ProbeResult(requestHeader, replyHeader, present = true)
    }
}
