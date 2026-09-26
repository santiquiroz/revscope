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
 */
class ElmSpeedTuning(private val exchange: suspend (String) -> String) {

    suspend fun aplicar(esCan11Bit: Boolean): Set<TecnicaCaptura> = buildSet {
        if (esCan11Bit && aplicarDireccionFisica()) add(TecnicaCaptura.DIRECCION_FISICA)
        if (aceptado(AT_TIMING_AGRESIVO)) add(TecnicaCaptura.TIMING_AGRESIVO)
    }

    /** Siempre corre completo, aunque la corrutina esté cancelada: el sondeo normal depende de esto. */
    suspend fun revertir(aplicadas: Set<TecnicaCaptura>) = withContext(NonCancellable) {
        if (TecnicaCaptura.DIRECCION_FISICA in aplicadas) quitarDireccionFisica()
        if (TecnicaCaptura.TIMING_AGRESIVO in aplicadas) aceptado(AT_TIMING_NORMAL)
    }

    suspend fun aplicarDireccionFisica(): Boolean {
        if (!aceptado(AT_HEADER_ECM)) return false
        if (aceptado(AT_FILTRO_ECM)) return true
        aceptado(AT_HEADER_FUNCIONAL)
        return false
    }

    suspend fun quitarDireccionFisica() {
        aceptado(AT_FILTRO_RESET)
        aceptado(AT_HEADER_FUNCIONAL)
    }

    private suspend fun aceptado(comando: String): Boolean = try {
        val respuesta = exchange(comando)
        !respuesta.contains("?") && !ResponseParser.isErrorResponse(respuesta)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "ElmSpeedTuning: ${comando.trim()} falló")
        false
    }

    companion object {
        const val AT_HEADER_ECM = "AT SH 7E0\r"
        const val AT_FILTRO_ECM = "AT CRA 7E8\r"
        const val AT_FILTRO_RESET = "AT CRA\r"
        const val AT_HEADER_FUNCIONAL = "AT SH 7DF\r"
        const val AT_TIMING_AGRESIVO = "AT AT 2\r"
        const val AT_TIMING_NORMAL = "AT AT 1\r"
    }
}
