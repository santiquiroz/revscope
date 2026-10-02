package com.revscope.core.obd.catalogo

import com.revscope.core.obd.diagnostics.uds.ClaseComando
import com.revscope.core.obd.diagnostics.uds.ComandoHex

object ValidadorCatalogo {
    private val tipos = setOf("lectura", "escritura", "rutina")
    private val riesgos = setOf("bajo", "medio", "alto")
    private val headerValido = Regex("^[0-9A-F]{3}$")

    fun validar(entrada: EntradaCatalogo): String? {
        if (entrada.id.isBlank()) return "el id no puede estar vacío"
        if (entrada.marca.isBlank()) return "la marca no puede estar vacía"
        if (entrada.descripcion.isBlank()) return "la descripción no puede estar vacía"
        if (entrada.tipo !in tipos) return "tipo inválido: debe ser lectura, escritura o rutina"
        if (entrada.riesgo !in riesgos) return "riesgo inválido: debe ser bajo, medio o alto"
        if (entrada.header != null && !headerValido.matches(entrada.header)) return "header inválido: usa tres dígitos hexadecimales en mayúsculas"
        if (entrada.pasos.size !in 1..10) return "debe haber entre 1 y 10 pasos"
        validarPasos(entrada)?.let { return it }
        if (entrada.formula != null && FormulaCatalogo.evaluar(entrada.formula, List(26) { 0 }) == null) {
            return "la fórmula no es válida o no produce un número finito"
        }
        return null
    }

    private fun validarPasos(entrada: EntradaCatalogo): String? {
        entrada.pasos.forEachIndexed { indice, paso ->
            val resultado = ComandoHex.validar(paso)
            val comando = resultado.getOrNull()
                ?: return "paso ${indice + 1} inválido: ${resultado.exceptionOrNull()?.message ?: "comando no válido"}"
            if (comando.clase == ClaseComando.BLOQUEADO) return "paso ${indice + 1} bloqueado"
            if (entrada.esLectura && comando.clase != ClaseComando.LECTURA) {
                return "paso ${indice + 1} no es de lectura"
            }
        }
        return null
    }
}
