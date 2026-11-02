package com.revscope.core.obd.connection

internal object CortadorLineasMonitor {

    fun drenarLineas(pendiente: StringBuilder, alLinea: (String) -> Unit) {
        var corte = indiceCorte(pendiente)
        while (corte >= 0) {
            val linea = pendiente.substring(0, corte)
            if (linea.isNotEmpty()) alLinea(linea)
            pendiente.delete(0, corte + 1)
            corte = indiceCorte(pendiente)
        }
    }

    fun indiceCorte(texto: StringBuilder): Int {
        val retorno = texto.indexOf("\r")
        val salto = texto.indexOf("\n")
        if (retorno < 0) return salto
        if (salto < 0) return retorno
        return minOf(retorno, salto)
    }

    fun entregarRestos(texto: String, alLinea: (String) -> Unit) {
        texto.split('\r', '\n').forEach { if (it.isNotEmpty()) alLinea(it) }
    }
}
