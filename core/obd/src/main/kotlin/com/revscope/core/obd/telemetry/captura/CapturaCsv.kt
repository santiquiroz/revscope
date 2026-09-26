package com.revscope.core.obd.telemetry.captura

import com.revscope.core.common.export.CsvFormat
import com.revscope.core.obd.pid.PidDefinition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formato del CSV de la captura rápida. Largo (una fila por muestra) es el principal; ancho (una fila
 * por lote) es opcional al exportar. `epoch_ms` y `t_ms` son enteros en ms; `t_ms` es relativo al inicio.
 */
object CapturaCsv {

    val COLUMNAS = listOf("epoch_ms", "t_ms", "pid", "nombre", "valor", "unidad", "lote", "latencia_ms")

    fun metadatos(info: InfoAdaptador, pids: List<String>, inicioEpochMs: Long, tecnicas: Set<TecnicaCaptura>): String =
        listOf(
            "revscope-captura v1",
            "adaptador=${info.nombre ?: "?"}",
            "elm=${info.elm ?: "?"}",
            "protocolo=${info.protocoloDpn ?: "?"}",
            "pids=${pids.joinToString(",")}",
            "inicio=${isoLocal(inicioEpochMs)}",
            "tecnicas=${tecnicas.joinToString(",") { it.clave }}",
        ).joinToString("; ")

    fun lineasIniciales(meta: String): List<String> = listOf("# $meta", CsvFormat.linea(COLUMNAS))

    fun filaLarga(m: MuestraCaptura, inicioEpochMs: Long, def: PidDefinition?): List<Any?> {
        val tMs = m.tMicros / 1_000
        return listOf(inicioEpochMs + tMs, tMs, m.pid, def?.nameEs ?: m.pid, redondear(m.valor), def?.unit ?: "", m.lote, m.latenciaMs)
    }

    fun lineaLarga(m: MuestraCaptura, inicioEpochMs: Long, def: PidDefinition?): String =
        CsvFormat.linea(filaLarga(m, inicioEpochMs, def))

    fun cabeceraAncha(pids: List<String>): List<String> = listOf("epoch_ms", "t_ms") + pids

    fun filasAnchas(muestras: List<MuestraCaptura>, pids: List<String>, inicioEpochMs: Long): List<List<Any?>> =
        muestras.groupBy { it.lote }.values.map { lote ->
            val tMs = lote.first().tMicros / 1_000
            val porPid = lote.associate { it.pid to redondear(it.valor) }
            listOf<Any?>(inicioEpochMs + tMs, tMs) + pids.map { porPid[it] }
        }

    fun redondear(valor: Double): Double = (valor * 1_000).roundToLong() / 1_000.0

    private fun isoLocal(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(epochMs))
}
