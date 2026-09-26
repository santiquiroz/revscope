package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.protocol.ResponseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Afinado del ELM para la captura rápida: direccionamiento físico al ECM (solo CAN 11-bit) y timing
 * adaptativo agresivo. Cada comando que el ELM rechaza («?» o error) se omite y la captura sigue.
 * [exchange] ya pasa por la compuerta del canal serie.
 *
 * Al detener la captura casi siempre se corta un intercambio a mitad: el ELM puede seguir esperando
 * a la ECU, y el primer carácter del siguiente comando lo interrumpe («STOPPED», comando perdido) o
 * su respuesta llega tarde. Por eso [revertir] primero resincroniza y cada AT se reintenta mientras
 * no responda un OK limpio.
 */
class ElmSpeedTuning(private val exchange: suspend (String) -> String) {

    suspend fun aplicar(esCan11Bit: Boolean): Set<TecnicaCaptura> = buildSet {
        if (esCan11Bit && aplicarDireccionFisica()) add(TecnicaCaptura.DIRECCION_FISICA)
        if (aceptado(AT_TIMING_AGRESIVO)) add(TecnicaCaptura.TIMING_AGRESIVO)
    }

    /**
     * Siempre corre completo, aunque la corrutina esté cancelada: el sondeo normal depende de esto.
     * Devuelve las técnicas que no se pudieron revertir (vacío si todo quedó en el estándar).
     */
    suspend fun revertir(aplicadas: Set<TecnicaCaptura>): Set<TecnicaCaptura> = withContext(NonCancellable) {
        if (!sincronizar()) Timber.w("ElmSpeedTuning: el ELM no confirmó la resincronización")
        buildSet {
            if (TecnicaCaptura.DIRECCION_FISICA in aplicadas && !quitarDireccionFisica()) add(TecnicaCaptura.DIRECCION_FISICA)
            if (TecnicaCaptura.TIMING_AGRESIVO in aplicadas && !aceptado(AT_TIMING_NORMAL)) add(TecnicaCaptura.TIMING_AGRESIVO)
        }
    }

    suspend fun aplicarDireccionFisica(): Boolean {
        if (!aceptado(AT_HEADER_ECM)) return false
        if (aceptado(AT_FILTRO_ECM)) return true
        aceptado(AT_HEADER_FUNCIONAL)
        return false
    }

    suspend fun quitarDireccionFisica(): Boolean {
        val filtro = aceptado(AT_FILTRO_RESET)
        val header = aceptado(AT_HEADER_FUNCIONAL)
        return filtro && header
    }

    // Un comando idempotente (el init ya dejó el eco apagado) absorbe el STOPPED y la respuesta rezagada.
    suspend fun sincronizar(): Boolean {
        repeat(MAX_INTENTOS_SINCRONIZAR) {
            val respuesta = intercambiar(AT_SINCRONIZAR) ?: return false
            if (confirma(respuesta)) return true
        }
        return false
    }

    private suspend fun aceptado(comando: String): Boolean {
        var ultima: String? = null
        for (intento in 0 until MAX_INTENTOS_AT) {
            val respuesta = intercambiar(comando) ?: return false
            if (confirma(respuesta)) return true
            // Tras un STOPPED, el «?» suele ser el resto del comando interrumpido, no un rechazo.
            if (intento == 0 && rechaza(respuesta)) return false
            ultima = respuesta
        }
        return ultima?.let(::aceptable) ?: false
    }

    private suspend fun intercambiar(comando: String): String? = try {
        exchange(comando)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "ElmSpeedTuning: ${comando.trim()} falló")
        null
    }

    private fun confirma(respuesta: String): Boolean =
        ResponseParser.cleanResponse(respuesta).contains("OK") && !ResponseParser.isErrorResponse(respuesta)

    private fun rechaza(respuesta: String): Boolean =
        respuesta.contains("?") && !ResponseParser.cleanResponse(respuesta).contains("STOPPED")

    private fun aceptable(respuesta: String): Boolean =
        !respuesta.contains("?") && !ResponseParser.isErrorResponse(respuesta)

    companion object {
        const val AT_HEADER_ECM = "AT SH 7E0\r"
        const val AT_FILTRO_ECM = "AT CRA 7E8\r"
        const val AT_FILTRO_RESET = "AT CRA\r"
        const val AT_HEADER_FUNCIONAL = "AT SH 7DF\r"
        const val AT_TIMING_AGRESIVO = "AT AT 2\r"
        const val AT_TIMING_NORMAL = "AT AT 1\r"
        const val AT_SINCRONIZAR = "AT E0\r"
        private const val MAX_INTENTOS_AT = 3
        private const val MAX_INTENTOS_SINCRONIZAR = 4
    }
}
