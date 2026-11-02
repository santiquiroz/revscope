package com.revscope.core.obd.mcp

import org.json.JSONObject
import java.math.BigDecimal

internal object McpArgumentos {

    fun entero(
        arguments: JSONObject,
        nombre: String,
        defecto: Long,
        minimo: Long,
        maximo: Long = Long.MAX_VALUE,
    ): Long? {
        if (!arguments.has(nombre)) return defecto.takeIf { it in minimo..maximo }
        val numero = arguments.opt(nombre) as? Number ?: return null
        val valor = runCatching { BigDecimal(numero.toString()).longValueExact() }.getOrNull() ?: return null
        return valor.takeIf { it in minimo..maximo }
    }
}
