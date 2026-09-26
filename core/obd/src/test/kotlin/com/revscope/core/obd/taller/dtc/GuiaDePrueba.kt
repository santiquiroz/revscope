package com.revscope.core.obd.taller.dtc

object GuiaDePrueba {

    fun textoReal(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(BaseConocimientoDtc.ASSET_GUIA)) {
            "${BaseConocimientoDtc.ASSET_GUIA} no está en el classpath de tests"
        }.bufferedReader().use { it.readText() }

    fun baseReal() = BaseConocimientoDtc(::textoReal)

    fun conEntradas(vararg entradas: String): String = """
        {"version": 1, "fuenteDescripciones": "SAE J2012", "aviso": "Orientación general",
         "codigos": [${entradas.joinToString(",")}]}
    """.trimIndent()

    fun entrada(
        codigo: String = "P0122",
        causas: Int = 2,
        verificaciones: Int = 3,
        accion: String? = null,
        relacionados: String = "[]",
    ): String {
        val listaCausas = List(causas) { "\"causa ${it + 1}\"" }.joinToString(",")
        val accionJson = accion?.let { ", \"accion\": \"$it\"" }.orEmpty()
        val listaVerificaciones = List(verificaciones) { "{\"paso\": \"paso ${it + 1}\"$accionJson}" }.joinToString(",")
        return """{"codigo": "$codigo", "titulo": "Título $codigo", "sistema": "TPS", "urgencia": "REVISAR_PRONTO",
            "causas": [$listaCausas], "verificaciones": [$listaVerificaciones], "relacionados": $relacionados}"""
    }
}
