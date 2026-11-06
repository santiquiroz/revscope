package com.revscope.core.obd.session

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.telemetry.PollingGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Concesión de diagnóstico: el bloque recibe el transporte SIN compuerta y corre con el sondeo
 * detenido, acotado por [timeoutMs]. Al salir (éxito, error, timeout o cancelación del llamador)
 * la compuerta se libera y el sondeo sigue sin haber soltado el adaptador.
 */
class DiagnosticLease(private val gate: PollingGate, private val resolver: StringResolver) {

    suspend fun <T> run(
        transport: Transport?,
        owner: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend (Transport) -> T,
    ): Result<T> {
        val bt = transport ?: return Result.failure(IllegalStateException(resolver.get(R.string.obd_error_not_connected)))
        return try {
            Result.success(gate.conceder(owner) { withTimeout(timeoutMs) { block(bt) } })
        } catch (e: TimeoutCancellationException) {
            Result.failure(
                IllegalStateException(resolver.get(R.string.obd_error_diagnostic_timeout, timeoutMs / 1_000), e),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}
