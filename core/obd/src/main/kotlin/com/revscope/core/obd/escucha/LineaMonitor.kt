package com.revscope.core.obd.escucha

object LineaMonitor {

    fun parsear(linea: String, tMs: Long, bits29: Boolean): TramaCan? {
        val texto = linea.trim().uppercase()
        if (esEstado(texto)) return null
        val tokens = texto.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return if (tokens.size == 1) parsearCompacta(tokens[0], tMs, bits29)
        else parsearSeparada(tokens, tMs, bits29)
    }

    fun esDesborde(linea: String): Boolean =
        linea.uppercase().contains("BUFFER FULL")

    fun esError(linea: String): Boolean {
        val texto = linea.uppercase()
        return texto.contains("CAN ERROR") ||
            texto.contains("<RX ERROR") ||
            texto.contains("BUS ERROR")
    }

    private fun esEstado(texto: String): Boolean =
        texto.isEmpty() ||
            texto == ">" ||
            texto == "STOPPED" ||
            texto == "SEARCHING..." ||
            texto == "?" ||
            texto == "OK"

    private fun parsearSeparada(tokens: List<String>, tMs: Long, bits29: Boolean): TramaCan? =
        if (bits29) parsearSeparada29(tokens, tMs) else parsearSeparada11(tokens, tMs)

    private fun parsearSeparada11(tokens: List<String>, tMs: Long): TramaCan? {
        val idTexto = tokens[0]
        if (idTexto.length != 3 || !esHex(idTexto)) return null
        val id = idTexto.toInt(16)
        if (id > 0x7FF) return null
        val datos = parsearBytes(tokens.drop(1)) ?: return null
        return TramaCan(tMs, id, false, datos)
    }

    private fun parsearSeparada29(tokens: List<String>, tMs: Long): TramaCan? {
        if (tokens.size < 4) return null
        val idPartes = tokens.take(4)
        if (!idPartes.all { it.length == 2 && esHex(it) }) return null
        val id = idPartes.joinToString("").toLong(16)
        if (id > 0x1FFFFFFF) return null
        val datos = parsearBytes(tokens.drop(4)) ?: return null
        return TramaCan(tMs, id.toInt(), true, datos)
    }

    private fun parsearCompacta(token: String, tMs: Long, bits29: Boolean): TramaCan? =
        if (bits29) parsearCompacta29(token, tMs) else parsearCompacta11(token, tMs)

    private fun parsearCompacta11(token: String, tMs: Long): TramaCan? {
        if (token.length < 3 || !esHex(token)) return null
        if ((token.length - 3) % 2 != 0) return null
        val id = token.substring(0, 3).toInt(16)
        if (id > 0x7FF) return null
        val datos = parsearPares(token.substring(3))
        if (datos.size > 8) return null
        return TramaCan(tMs, id, false, datos)
    }

    private fun parsearCompacta29(token: String, tMs: Long): TramaCan? {
        if (token.length < 8 || !esHex(token)) return null
        if ((token.length - 8) % 2 != 0) return null
        val id = token.substring(0, 8).toLong(16)
        if (id > 0x1FFFFFFF) return null
        val datos = parsearPares(token.substring(8))
        if (datos.size > 8) return null
        return TramaCan(tMs, id.toInt(), true, datos)
    }

    private fun parsearBytes(tokens: List<String>): List<Int>? {
        if (tokens.size > 8) return null
        if (!tokens.all { it.length == 2 && esHex(it) }) return null
        return tokens.map { it.toInt(16) }
    }

    private fun parsearPares(hex: String): List<Int> =
        hex.chunked(2).map { it.toInt(16) }

    private fun esHex(texto: String): Boolean =
        texto.isNotEmpty() && texto.all { it in '0'..'9' || it in 'A'..'F' }
}
