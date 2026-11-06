package com.revscope.core.obd.perfilcan

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

object DecodificadorSenal {

    fun valor(senal: SenalCan, datos: ByteArray): Double? {
        val crudo = crudoValido(senal, datos) ?: return null
        val numero = conSigno(senal, crudo).toDouble()
        return if (senal.tipo == TipoSenal.NUMERO) numero * senal.factor + senal.offset else numero
    }

    fun texto(senal: SenalCan, datos: ByteArray, resolver: StringResolver): String? {
        val crudo = crudoValido(senal, datos) ?: return null
        return when (senal.tipo) {
            TipoSenal.ENUM -> senal.valores[crudo] ?: crudo.toString()
            TipoSenal.BANDERA -> resolver.get(if (crudo != 0L) R.string.obd_signal_flag_yes else R.string.obd_signal_flag_no)
            TipoSenal.NUMERO -> numeroConUnidad(valor(senal, datos)!!, senal.unidad)
        }
    }

    private fun crudoValido(senal: SenalCan, datos: ByteArray): Long? =
        crudo(senal, datos)?.takeIf { it !in senal.invalidos }

    // Bit 0 = LSB del byte 0; el campo arranca en el byte bitInicio/8 y sigue hacia los siguientes.
    private fun crudo(senal: SenalCan, datos: ByteArray): Long? {
        val desplazamiento = senal.bitInicio % 8
        val primero = senal.bitInicio / 8
        val cantidad = (desplazamiento + senal.largo + 7) / 8
        if (primero + cantidad > datos.size) return null
        val bytes = datos.copyOfRange(primero, primero + cantidad)
        val ordenados = if (senal.orden == OrdenBytes.LITTLE) bytes.reversedArray() else bytes
        val acumulado = ordenados.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        return (acumulado ushr desplazamiento) and mascara(senal.largo)
    }

    private fun conSigno(senal: SenalCan, crudo: Long): Long {
        if (!senal.firmado || senal.largo >= 64) return crudo
        val bitSigno = 1L shl (senal.largo - 1)
        return if (crudo and bitSigno != 0L) crudo - (1L shl senal.largo) else crudo
    }

    private fun mascara(largo: Int): Long = if (largo >= 64) -1L else (1L shl largo) - 1

    private fun numeroConUnidad(numero: Double, unidad: String): String {
        val texto = if (numero == Math.rint(numero)) numero.toLong().toString() else "%.2f".format(numero)
        return if (unidad.isBlank()) texto else "$texto $unidad"
    }
}
