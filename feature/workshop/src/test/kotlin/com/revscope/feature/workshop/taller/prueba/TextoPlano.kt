package com.revscope.feature.workshop.taller.prueba

// Los textos llevan espacio duro entre número y unidad (y «rpm/s» unido); los tests comparan contra el texto plano.
internal fun String.plano(): String = replace('\u00A0', ' ').replace("\u2060", "")
