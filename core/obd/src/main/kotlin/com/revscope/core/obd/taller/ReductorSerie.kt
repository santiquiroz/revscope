package com.revscope.core.obd.taller

// Mínimo y máximo por cubeta: una caída breve de la señal (lo que busca un diagnóstico) sobrevive a la reducción.
object ReductorSerie {

    const val MAX_PUNTOS = 400

    fun <T> reducir(puntos: List<T>, maximo: Int = MAX_PUNTOS, valor: (T) -> Double): List<T> {
        require(maximo >= 2) { "Se necesitan al menos 2 puntos" }
        if (puntos.size <= maximo) return puntos
        val cubetas = maximo / 2
        return (0 until cubetas).flatMap { i ->
            extremosEnOrden(puntos.subList(i * puntos.size / cubetas, (i + 1) * puntos.size / cubetas), valor)
        }
    }

    private fun <T> extremosEnOrden(cubeta: List<T>, valor: (T) -> Double): List<T> {
        val indices = cubeta.indices
        val minimo = indices.minBy { valor(cubeta[it]) }
        val maximo = indices.maxBy { valor(cubeta[it]) }
        return listOf(minimo, maximo).distinct().sorted().map { cubeta[it] }
    }
}
