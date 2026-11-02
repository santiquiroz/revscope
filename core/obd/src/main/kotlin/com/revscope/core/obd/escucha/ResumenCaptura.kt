package com.revscope.core.obd.escucha

data class ResumenId(
    val id: String,
    val tramas: Int,
    val hz: Double,
    val largos: Set<Int>,
    val bytesQueCambian: List<Int>,
)

object ResumenCaptura {

    fun calcular(captura: CapturaCan): List<ResumenId> {
        val grupos = captura.tramas.groupBy { IdCan.texto(it.id, it.extendido) }
        return grupos.map { (id, tramas) -> resumir(id, tramas, captura.duracionMs) }
            .sortedBy { it.id }
    }

    private fun resumir(id: String, tramas: List<TramaCan>, duracionMs: Long): ResumenId {
        val segundos = duracionMs / 1000.0
        val hz = if (duracionMs == 0L) 0.0 else tramas.size / segundos
        return ResumenId(
            id = id,
            tramas = tramas.size,
            hz = hz,
            largos = tramas.map { it.datos.size }.toSet(),
            bytesQueCambian = indicesQueCambian(tramas),
        )
    }

    private fun indicesQueCambian(tramas: List<TramaCan>): List<Int> {
        val maximo = tramas.maxOf { it.datos.size }
        return (0 until maximo).filter { indice -> valoresEn(tramas, indice).toSet().size > 1 }
    }

    private fun valoresEn(tramas: List<TramaCan>, indice: Int): List<Int> =
        tramas.filter { it.datos.size > indice }.map { it.datos[indice] }
}
