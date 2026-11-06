package com.revscope.core.obd.taller.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import org.json.JSONObject

data class CargaGuiaDtc(val catalogo: CatalogoDtc, val problemas: List<String>) {

    companion object {
        val VACIA = CargaGuiaDtc(CatalogoDtc(0, "", "", emptyList()), emptyList())

        fun desdeJson(json: String, resolver: StringResolver): CargaGuiaDtc {
            val raiz = JSONObject(json)
            val lecturas = CodecGuiaDtc.entradas(raiz).mapIndexed { indice, entrada ->
                leerEntrada(indice, entrada, resolver)
            }
            val validas = lecturas.mapNotNull { it.guia }
            val duplicados = codigosDuplicados(validas)
            val catalogo = catalogoCon(CodecGuiaDtc.leerCabecera(raiz), validas.filterNot { it.codigo in duplicados })
            val problemas = lecturas.flatMap { it.problemas } +
                duplicados.map { resolver.get(R.string.obd_dtc_validation_duplicate_code, it) }
            return CargaGuiaDtc(catalogo, problemas)
        }

        private fun leerEntrada(indice: Int, o: JSONObject, resolver: StringResolver): LecturaEntrada {
            val guia = runCatching { CodecGuiaDtc.leerGuia(o, resolver) }
                .getOrElse {
                    return LecturaEntrada(null, listOf(resolver.get(R.string.obd_dtc_validation_entry_error, indice + 1, it.message.orEmpty())))
                }
            val problemas = ValidadorGuiaDtc.problemas(guia, resolver)
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
