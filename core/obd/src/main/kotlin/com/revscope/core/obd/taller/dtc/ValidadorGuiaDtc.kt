package com.revscope.core.obd.taller.dtc

object ValidadorGuiaDtc {

    const val MIN_CAUSAS = 2
    const val MIN_VERIFICACIONES = 3

    fun problemas(guia: GuiaDtc): List<String> = listOfNotNull(
        problemaCodigo(guia),
        "sin título".takeIf { guia.titulo.isBlank() },
        "sin sistema".takeIf { guia.sistema.isBlank() },
        "menos de $MIN_CAUSAS causas".takeIf { guia.causas.count(String::isNotBlank) < MIN_CAUSAS },
        "menos de $MIN_VERIFICACIONES verificaciones".takeIf { verificacionesUtiles(guia) < MIN_VERIFICACIONES },
        problemaRelacionados(guia),
    ).map { "${guia.codigo}: $it" }

    private fun problemaCodigo(guia: GuiaDtc): String? =
        "código con formato inválido".takeIf { DecodificadorDtc.normalizar(guia.codigo) != guia.codigo }

    private fun verificacionesUtiles(guia: GuiaDtc): Int = guia.verificaciones.count { it.paso.isNotBlank() }

    private fun problemaRelacionados(guia: GuiaDtc): String? {
        val invalidos = guia.relacionados.filter { DecodificadorDtc.normalizar(it) != it || it == guia.codigo }
        return if (invalidos.isEmpty()) null else "relacionados inválidos $invalidos"
    }
}
