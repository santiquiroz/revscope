package com.revscope.core.common.export

/** Filas CSV (coma, punto decimal, escape RFC 4180) sin Context: la usan CsvShare y la captura rápida. */
object CsvFormat {

    fun linea(campos: List<Any?>): String = campos.joinToString(",") { escapar(celda(it)) }

    fun celda(value: Any?): String = value?.toString() ?: ""

    fun escapar(campo: String): String =
        if (campo.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + campo.replace("\"", "\"\"") + "\""
        } else {
            campo
        }
}
