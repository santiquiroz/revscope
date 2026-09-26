package com.revscope.core.obd.taller.dtc

import org.json.JSONObject

data class CargaGuiaDtc(val catalogo: CatalogoDtc, val problemas: List<String>) {

    companion object {
        val VACIA = CargaGuiaDtc(CatalogoDtc(0, "", "", emptyList()), emptyList())

        fun desdeJson(json: String): CargaGuiaDtc {
            val raiz = JSONObject(json)
            val lecturas = CodecGuiaDtc.entradas(raiz).mapIndexed(::leerEntrada)
            val validas = lecturas.mapNotNull { it.guia }
            val duplicados = codigosDuplicados(validas)
            val catalogo = catalogoCon(CodecGuiaDtc.leerCabecera(raiz), validas.filterNot { it.codigo in duplicados })
            val problemas = lecturas.flatMap { it.problemas } + duplicados.map { "$it: código repetido" }
            return CargaGuiaDtc(catalogo, problemas)
        }

        private fun leerEntrada(indice: Int, o: JSONObject): LecturaEntrada {
            val guia = runCatching { CodecGuiaDtc.leerGuia(o) }
                .getOrElse { return LecturaEntrada(null, listOf("entrada ${indice + 1}: ${it.message}")) }
            val problemas = ValidadorGuiaDtc.problemas(guia)
            return LecturaEntrada(guia.takeIf { problemas.isEmpty() }, problemas)
        }

        private fun codigosDuplicados(guias: List<GuiaDtc>): Set<String> =
            guias.groupingBy { it.codigo }.eachCount().filterValues { it > 1 }.keys

        private fun catalogoCon(cabecera: CabeceraGuiaDtc, guias: List<GuiaDtc>) = CatalogoDtc(
            version = cabecera.version,
            fuenteDescripciones = cabecera.fuenteDescripciones,
            aviso = cabecera.aviso,
            guias = guias,
        )
    }

    private data class LecturaEntrada(val guia: GuiaDtc?, val problemas: List<String>)
}
