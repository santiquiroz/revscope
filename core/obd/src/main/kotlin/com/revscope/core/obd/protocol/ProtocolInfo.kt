package com.revscope.core.obd.protocol

/** Interpreta la respuesta de AT DPN ("6", "A6" = automático → 6). */
object ProtocolInfo {

    private val CAN_PROTOCOLS = setOf('6', '7', '8', '9', 'A', 'B', 'C')
    private val NON_CAN_PROTOCOLS = setOf('1', '2', '3', '4', '5')

    fun esCan(dpn: String?): Boolean? {
        val numero = numeroDe(dpn) ?: return null
        return when (numero) {
            in CAN_PROTOCOLS -> true
            in NON_CAN_PROTOCOLS -> false
            else -> null
        }
    }

    /** CAN 11-bit (protocolos 6 y 8): el único donde la captura rápida usa direccionamiento físico 7E0. */
    fun esCan11Bit(dpn: String?): Boolean = numeroDe(dpn) in CAN_11_BIT

    private val CAN_11_BIT = setOf('6', '8')

    private fun numeroDe(dpn: String?): Char? {
        val clean = dpn?.let(ResponseParser::cleanResponse).orEmpty()
        val sinAuto = if (clean.length == 2 && clean.startsWith("A")) clean.drop(1) else clean
        return sinAuto.singleOrNull()
    }
}
