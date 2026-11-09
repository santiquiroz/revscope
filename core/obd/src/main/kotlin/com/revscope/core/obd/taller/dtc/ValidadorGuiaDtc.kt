package com.revscope.core.obd.taller.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

object ValidadorGuiaDtc {

    const val MIN_CAUSAS = 2
    const val MIN_VERIFICACIONES = 3

    fun problemas(guia: GuiaDtc, resolver: StringResolver): List<String> = listOfNotNull(
        problemaCodigo(guia, resolver),
        resolver.get(R.string.obd_dtc_validation_no_title).takeIf { guia.titulo.isBlank() },
        resolver.get(R.string.obd_dtc_validation_no_system).takeIf { guia.sistema.isBlank() },
        resolver.get(R.string.obd_dtc_validation_too_few_causes, MIN_CAUSAS)
            .takeIf { guia.causas.count(String::isNotBlank) < MIN_CAUSAS },
        resolver.get(R.string.obd_dtc_validation_too_few_checks, MIN_VERIFICACIONES)
            .takeIf { verificacionesUtiles(guia) < MIN_VERIFICACIONES },
        problemaRelacionados(guia, resolver),
    ).map { resolver.get(R.string.obd_dtc_validation_entry_prefix, guia.codigo, it) }

    private fun problemaCodigo(guia: GuiaDtc, resolver: StringResolver): String? =
        resolver.get(R.string.obd_dtc_validation_bad_code_format)
            .takeIf { DecodificadorDtc.normalizar(guia.codigo) != guia.codigo }

    private fun verificacionesUtiles(guia: GuiaDtc): Int = guia.verificaciones.count { it.paso.isNotBlank() }

    private fun problemaRelacionados(guia: GuiaDtc, resolver: StringResolver): String? {
        val invalidos = guia.relacionados.filter { DecodificadorDtc.normalizar(it) != it || it == guia.codigo }
        return if (invalidos.isEmpty()) null else resolver.get(R.string.obd_dtc_validation_bad_related_codes, invalidos)
    }
}
