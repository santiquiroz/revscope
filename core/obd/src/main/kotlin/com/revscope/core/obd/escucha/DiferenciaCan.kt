package com.revscope.core.obd.escucha

data class ByteCambiado(
    val id: String,
    val byte: Int,
    val valorBase: Int,
    val valoresNuevos: List<Int>,
)

data class BitCambiado(
    val id: String,
    val byte: Int,
    val bit: Int,
    val valorBase: Int,
)

data class Diferencia(
    val idsNuevos: List<String>,
    val idsAusentes: List<String>,
    val bytes: List<ByteCambiado>,
    val bits: List<BitCambiado>,
)

object DiferenciaCan {

    fun calcular(base: List<TramaCan>, accion: List<TramaCan>): Diferencia {
        val porIdBase = agrupar(base)
        val porIdAccion = agrupar(accion)
        val bytes = calcularBytes(porIdBase, porIdAccion)
        val excluidos = bytes.map { it.id to it.byte }.toSet()
        return Diferencia(
            idsNuevos = (porIdAccion.keys - porIdBase.keys).sorted(),
            idsAusentes = (porIdBase.keys - porIdAccion.keys).sorted(),
            bytes = bytes,
            bits = calcularBits(porIdBase, porIdAccion, excluidos),
        )
    }

    private fun agrupar(tramas: List<TramaCan>): Map<String, List<TramaCan>> =
        tramas.groupBy { IdCan.texto(it.id, it.extendido) }

    private fun calcularBytes(
        porIdBase: Map<String, List<TramaCan>>,
        porIdAccion: Map<String, List<TramaCan>>,
    ): List<ByteCambiado> {
        val comunes = porIdBase.keys.intersect(porIdAccion.keys).sorted()
        return comunes.flatMap { id -> bytesCambiados(id, porIdBase.getValue(id), porIdAccion.getValue(id)) }
    }

    private fun bytesCambiados(id: String, base: List<TramaCan>, accion: List<TramaCan>): List<ByteCambiado> {
        return indicesComunes(base, accion).mapNotNull { indice ->
            val baseUnicos = valoresEn(base, indice).toSet()
            val accionUnicos = valoresEn(accion, indice).toSet()
            val valorBase = baseUnicos.singleOrNull() ?: return@mapNotNull null
            val nuevos = (accionUnicos - valorBase).sorted()
            if (nuevos.isEmpty()) null else ByteCambiado(id, indice, valorBase, nuevos)
        }
    }

    private fun calcularBits(
        porIdBase: Map<String, List<TramaCan>>,
        porIdAccion: Map<String, List<TramaCan>>,
        excluidos: Set<Pair<String, Int>>,
    ): List<BitCambiado> {
        val comunes = porIdBase.keys.intersect(porIdAccion.keys).sorted()
        return comunes.flatMap { id ->
            bitsCambiados(id, porIdBase.getValue(id), porIdAccion.getValue(id), excluidos)
        }
    }

    private fun bitsCambiados(
        id: String,
        base: List<TramaCan>,
        accion: List<TramaCan>,
        excluidos: Set<Pair<String, Int>>,
    ): List<BitCambiado> {
        return indicesComunes(base, accion)
            .filter { indice -> id to indice !in excluidos }
            .filter { indice -> valoresEn(base, indice).toSet().size > 1 }
            .flatMap { indice -> bitsCambiadosEn(id, indice, base, accion) }
    }

    private fun bitsCambiadosEn(id: String, indice: Int, base: List<TramaCan>, accion: List<TramaCan>): List<BitCambiado> {
        val baseValores = valoresEn(base, indice)
        val accionBits = valoresEn(accion, indice)
        return (0..7).mapNotNull { bit ->
            val baseUnico = baseValores.map { (it shr bit) and 1 }.toSet().singleOrNull() ?: return@mapNotNull null
            if (accionBits.any { ((it shr bit) and 1) != baseUnico }) BitCambiado(id, indice, bit, baseUnico)
            else null
        }
    }

    private fun indicesComunes(base: List<TramaCan>, accion: List<TramaCan>): IntRange {
        val maxBase = base.maxOf { it.datos.size }
        val maxAccion = accion.maxOf { it.datos.size }
        return 0 until maxOf(maxBase, maxAccion)
    }

    private fun valoresEn(tramas: List<TramaCan>, indice: Int): List<Int> =
        tramas.filter { it.datos.size > indice }.map { it.datos[indice] }
}
