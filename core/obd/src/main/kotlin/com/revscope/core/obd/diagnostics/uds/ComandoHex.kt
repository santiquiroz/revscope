package com.revscope.core.obd.diagnostics.uds

enum class ClaseComando { LECTURA, ESCRITURA, BLOQUEADO }

data class ComandoValidado(val texto: String, val bytes: List<Int>, val clase: ClaseComando)

object ComandoHex {
    const val MAX_BYTES = 7

    private val whitespace = Regex("\\s+")
    private val hexDigits = Regex("^[0-9A-F]+$")
    private val serviciosLectura = setOf(
        0x01, 0x02, 0x03, 0x06, 0x07, 0x09, 0x0A, 0x19,
        0x1A, 0x21, 0x22, 0x23, 0x24,
    )
    private val serviciosBloqueados = setOf(0x34, 0x35, 0x36, 0x37, 0x38, 0x3D)

    fun validar(entrada: String): Result<ComandoValidado> {
        val texto = entrada.trim()
        if (texto.startsWith("AT", ignoreCase = true)) return validarAt(texto)
        if (texto.isEmpty()) return fallo("comando vacío")

        val compacto = texto.replace(whitespace, "").uppercase()
        if (!hexDigits.matches(compacto)) return fallo("solo dígitos hexadecimales")
        if (compacto.length % 2 != 0) return fallo("número impar de dígitos hex")
        val bytes = compacto.chunked(2).map { it.toInt(16) }
        if (bytes.size > MAX_BYTES) {
            return fallo("máximo 7 bytes: el ELM327 solo envía tramas simples")
        }
        return Result.success(ComandoValidado(bytes.joinToString(" ") { it.hexByte() }, bytes, clasificar(bytes)))
    }

    fun clasificar(bytes: List<Int>): ClaseComando {
        val servicio = bytes.firstOrNull() ?: return ClaseComando.ESCRITURA
        // Cambiar o sostener la sesión de diagnóstico (10 xx, 3E) altera el estado del ECU: va por la puerta de escritura.
        if (servicio == 0x10) return if (bytes.getOrNull(1) == 0x02) ClaseComando.BLOQUEADO else ClaseComando.ESCRITURA
        return when (servicio) {
            in serviciosLectura -> ClaseComando.LECTURA
            in serviciosBloqueados -> ClaseComando.BLOQUEADO
            else -> ClaseComando.ESCRITURA
        }
    }

    private fun validarAt(texto: String): Result<ComandoValidado> {
        val resto = texto.drop(2).replace(whitespace, "").uppercase()
        val comando = "AT $resto"
        val clase = if (resto in setOf("RV", "DPN", "I")) {
            ClaseComando.LECTURA
        } else {
            ClaseComando.BLOQUEADO
        }
        return Result.success(ComandoValidado(comando, emptyList(), clase))
    }

    private fun fallo(mensaje: String): Result<ComandoValidado> =
        Result.failure(IllegalArgumentException(mensaje))

    private fun Int.hexByte(): String = toString(16).uppercase().padStart(2, '0')
}
