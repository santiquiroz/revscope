package com.revscope.core.obd.catalogo

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.diagnostics.uds.ClaseComando
import com.revscope.core.obd.diagnostics.uds.ComandoHex

object ValidadorCatalogo {
    private val tipos = setOf("lectura", "escritura", "rutina")
    private val riesgos = setOf("bajo", "medio", "alto")
    private val headerValido = Regex("^[0-9A-F]{3}$")

    fun validar(entrada: EntradaCatalogo, resolver: StringResolver): String? =
        errorCampos(entrada)?.let { resolver.get(it) }
            ?: validarPasos(entrada, resolver)
            ?: errorFormula(entrada)?.let { resolver.get(it) }

    private fun errorCampos(entrada: EntradaCatalogo): Int? = when {
        entrada.id.isBlank() -> R.string.obd_catalogo_val_id_vacio
        entrada.marca.isBlank() -> R.string.obd_catalogo_val_marca_vacia
        entrada.descripcion.isBlank() -> R.string.obd_catalogo_val_descripcion_vacia
        entrada.tipo !in tipos -> R.string.obd_catalogo_val_tipo_invalido
        entrada.riesgo !in riesgos -> R.string.obd_catalogo_val_riesgo_invalido
        entrada.header != null && !headerValido.matches(entrada.header) -> R.string.obd_catalogo_val_header_invalido
        entrada.pasos.size !in 1..10 -> R.string.obd_catalogo_val_cantidad_pasos
        else -> null
    }

    private fun errorFormula(entrada: EntradaCatalogo): Int? =
        R.string.obd_catalogo_val_formula_invalida.takeIf {
            entrada.formula != null && FormulaCatalogo.evaluar(entrada.formula, List(26) { 0 }) == null
        }

    private fun validarPasos(entrada: EntradaCatalogo, resolver: StringResolver): String? {
        entrada.pasos.forEachIndexed { indice, paso ->
            val numero = indice + 1
            val resultado = ComandoHex.validar(paso)
            val comando = resultado.getOrNull()
                ?: return resolver.get(R.string.obd_catalogo_val_paso_invalido, numero, motivo(resultado, resolver))
            if (comando.clase == ClaseComando.BLOQUEADO) return resolver.get(R.string.obd_catalogo_val_paso_bloqueado, numero)
            if (entrada.esLectura && comando.clase != ClaseComando.LECTURA) {
                return resolver.get(R.string.obd_catalogo_val_paso_no_lectura, numero)
            }
        }
        return null
    }

    private fun motivo(resultado: Result<*>, resolver: StringResolver): String =
        resultado.exceptionOrNull()?.message ?: resolver.get(R.string.obd_catalogo_val_comando_no_valido)
}
