package com.revscope.feature.map.social

/**
 * Registro puro del orden REAL de cruce de una carrera (spec F1). [RankingCalc.rank] no tiene
 * memoria entre emisiones: agrupa "llegados" preservando el orden en que las entries llegan a
 * la función (self siempre primero), así que no sirve como orden de podio. Este objeto sí tiene
 * memoria — vía [State], que el llamador conserva entre ticks (mismo patrón que
 * `RaceArrival.State` en Leaderboard.kt) — y resuelve quién cruzó primero de verdad.
 */
object ArrivalLedger {

    /** [order] es 1-based, en orden de cruce real. [entry] queda congelado al momento del
     * cruce: es lo que se sigue mostrando en el podio aunque el Peer deje de emitir después
     * (estacionado) o sea pruneado por stale — comportamiento "sticky" de la spec F1. */
    data class Finisher(val order: Int, val entry: RankingCalc.Entry)

    data class State(
        val raceKey: Long? = null,
        val wasArrived: Map<String, Boolean> = emptyMap(),
        val finishers: Map<String, Finisher> = emptyMap(),
    )

    /**
     * [raceKey] es el `startAtMs` de la carrera activa — null resetea todo (sin carrera). Al
     * ver un [raceKey] nuevo se siembra `wasArrived` tal cual está en ese instante para cada
     * nombre presente en [entries] — mismo criterio que `RaceArrival.step`: quien ya está en el
     * destino al armarse la carrera (rematch) no genera un cruce falso false→true.
     *
     * Un cruce solo se acredita con número de orden si ocurre con la carrera ya arrancada
     * ([nowMs] >= [raceKey]); un cruce pre-largada se registra en `wasArrived` (para no
     * "reaparecer" como cruce nuevo después) pero no genera orden — semántica conservadora
     * heredada de `RaceArrival`. Empates del mismo tick se desempatan por restante ascendente y
     * luego por nombre, así el orden es determinístico.
     */
    fun step(state: State, entries: List<RankingCalc.Entry>, raceKey: Long?, nowMs: Long): State {
        if (raceKey == null) return State()
        val baseline = if (state.raceKey == raceKey) state else seed(entries, raceKey)
        val startOrder = (baseline.finishers.values.maxOfOrNull { it.order } ?: 0) + 1
        val newFinishers = crossings(entries, baseline, nowMs)
            .mapIndexed { offset, entry -> entry.name to Finisher(startOrder + offset, entry) }
        return baseline.copy(
            wasArrived = baseline.wasArrived + entries.associate { it.name to it.arrived },
            finishers = baseline.finishers + newFinishers,
        )
    }

    private fun seed(entries: List<RankingCalc.Entry>, raceKey: Long): State =
        State(raceKey = raceKey, wasArrived = entries.associate { it.name to it.arrived })

    /** Entries que cruzan false→true en este tick, ya arrancada la carrera y sin orden todavía. */
    private fun crossings(entries: List<RankingCalc.Entry>, baseline: State, nowMs: Long): List<RankingCalc.Entry> {
        val raceKey = baseline.raceKey ?: return emptyList()
        if (nowMs < raceKey) return emptyList()
        return entries
            .filter { it.arrived && baseline.wasArrived[it.name] != true && it.name !in baseline.finishers }
            .sortedWith(compareBy({ it.remainingM }, { it.name }))
    }
}
