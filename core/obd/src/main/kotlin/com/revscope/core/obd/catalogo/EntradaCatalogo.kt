package com.revscope.core.obd.catalogo

data class EntradaCatalogo(
    val id: String,
    val marca: String,
    val modelos: List<String>,
    val anios: String?,
    val modulo: String?,
    val header: String?,
    val tipo: String,
    val pasos: List<String>,
    val formula: String?,
    val unidad: String?,
    val descripcion: String,
    val requiereSecurityAccess: Boolean,
    val riesgo: String,
    val fuente: String?,
    val licencia: String?,
    val verificado: Boolean,
    val notas: String?,
) {
    val esLectura: Boolean get() = tipo == "lectura"
}
