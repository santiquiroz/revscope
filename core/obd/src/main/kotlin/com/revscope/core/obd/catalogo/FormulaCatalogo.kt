package com.revscope.core.obd.catalogo

import net.objecthunter.exp4j.ExpressionBuilder

object FormulaCatalogo {
    private val variables = ('A'..'Z').map(Char::toString)
    private val referencia = Regex("(?<![A-Z])[A-Z](?![A-Z])")

    fun evaluar(formula: String, datos: List<Int>): Double? {
        val requeridas = referencia.findAll(formula).map { it.value.single() - 'A' }
        if (requeridas.any { it >= datos.size }) return null
        return runCatching {
            val evaluador = ExpressionBuilder(formula)
                .variables(*variables.toTypedArray())
                .build()
            variables.forEachIndexed { indice, nombre ->
                evaluador.setVariable(nombre, datos.getOrNull(indice)?.toDouble() ?: 0.0)
            }
            evaluador.evaluate().takeIf(Double::isFinite)
        }.getOrNull()
    }
}
