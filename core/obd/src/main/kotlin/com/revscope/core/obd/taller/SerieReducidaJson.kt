package com.revscope.core.obd.taller

import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import org.json.JSONArray
import org.json.JSONObject

// Serie por PID reducida a ReductorSerie.MAX_PUNTOS para el payload de un evento; el CSV adjunto guarda todo.
object SerieReducidaJson {

    fun porPid(muestras: List<MuestraCaptura>): JSONObject = JSONObject().apply {
        muestras.groupBy { it.pid }.forEach { (pid, lista) ->
            val reducida = ReductorSerie.reducir(lista.sortedBy { it.tMicros }) { it.valor }
            put(
                pid,
                JSONObject()
                    .put("t_ms", JSONArray(reducida.map { it.tMicros / 1_000 }))
                    .put("v", JSONArray(reducida.map { FormatoTaller.redondear(it.valor, 3) }))
                    .put("n", lista.size),
            )
        }
    }
}
