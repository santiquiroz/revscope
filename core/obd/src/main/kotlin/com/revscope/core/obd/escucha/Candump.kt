package com.revscope.core.obd.escucha

data class LineaCandump(
    val epochMs: Long,
    val id: Int,
    val extendido: Boolean,
    val datos: List<Int>,
)

object Candump {

    fun linea(trama: TramaCan, inicioEpochMs: Long, interfaz: String = "can0"): String {
        val epochMs = inicioEpochMs + trama.tMs
        val segundos = epochMs / 1000
        val micros = (epochMs % 1000) * 1000
        val datos = trama.datos.joinToString("") { "%02X".format(it) }
        return "($segundos.${"%06d".format(micros)}) $interfaz ${IdCan.texto(trama.id, trama.extendido)}#$datos"
    }

    fun parsear(linea: String): LineaCandump? {
        val texto = linea.trim()
        if (!texto.startsWith("(")) return null
        val cierre = texto.indexOf(')')
        if (cierre < 0) return null
        val epochMs = parsearTiempo(texto.substring(1, cierre)) ?: return null
        val partes = texto.substring(cierre + 1).trim().split(Regex("\\s+"))
        if (partes.size < 2) return null
        return parsearMarco(partes[1], epochMs)
    }

    fun aTrama(linea: LineaCandump, inicioEpochMs: Long): TramaCan =
        TramaCan(linea.epochMs - inicioEpochMs, linea.id, linea.extendido, linea.datos)

    private fun parsearTiempo(texto: String): Long? {
        val punto = texto.indexOf('.')
        if (punto < 0) return null
        val segundos = texto.substring(0, punto)
        val fraccion = texto.substring(punto + 1)
        if (segundos.isEmpty() || !segundos.all { it in '0'..'9' }) return null
        if (fraccion.isEmpty() || fraccion.length > 6 || !fraccion.all { it in '0'..'9' }) return null
        val micros = fraccion.padEnd(6, '0').toLong()
        return segundos.toLong() * 1000 + micros / 1000
    }

    private fun parsearMarco(marco: String, epochMs: Long): LineaCandump? {
        val separador = marco.indexOf('#')
        if (separador < 0) return null
        val idTexto = marco.substring(0, separador)
        val datosTexto = marco.substring(separador + 1).uppercase()
        if (idTexto.isEmpty() || idTexto.length > 8 || !esHex(idTexto)) return null
        if (datosTexto.startsWith("R")) return null
        if (datosTexto.length > 16 || datosTexto.length % 2 != 0 || !esHex(datosTexto)) return null
        val id = idTexto.toLong(16)
        if (idTexto.length < 8 && id > 0x7FF) return null
        if (idTexto.length == 8 && id > 0x1FFFFFFF) return null
        val datos = datosTexto.chunked(2).map { it.toInt(16) }
        return LineaCandump(epochMs, id.toInt(), idTexto.length == 8, datos)
    }

    private fun esHex(texto: String): Boolean =
        texto.all { it in '0'..'9' || it in 'A'..'F' }
}
