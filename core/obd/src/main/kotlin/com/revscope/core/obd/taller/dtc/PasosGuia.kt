package com.revscope.core.obd.taller.dtc

// Las casillas de la guía se guardan en la sesión como «P0122#2»: código y número de verificación desde 1.
object PasosGuia {

    private const val SEPARADOR = "#"

    fun clave(codigo: String, numero: Int): String = "$codigo$SEPARADOR$numero"

    fun marcado(marcados: Set<String>, codigo: String, numero: Int): Boolean = clave(codigo, numero) in marcados

    fun contarMarcados(marcados: Set<String>, codigo: String, totalPasos: Int): Int =
        (1..totalPasos).count { marcado(marcados, codigo, it) }
}
