package com.revscope.core.obd.taller.pruebas

// Los textos llevan espacio duro entre número y unidad (y «rpm/s» unido); los tests comparan contra el texto plano.
fun String.plano(): String = replace('\u00A0', ' ').replace("\u2060", "")

fun ResultadoPrueba.plano(): ResultadoPrueba = copy(
    titulo = titulo.plano(),
    interpretacion = interpretacion.plano(),
    siguientePaso = siguientePaso?.plano(),
    hallazgos = hallazgos.map { it.plano() },
)
