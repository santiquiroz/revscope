package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONObject

// Los veredictos se redactan como «compatible con…»: la prueba orienta, no dictamina la causa.
data class ResultadoPrueba(
    val tipo: TipoPrueba,
    val veredicto: Veredicto,
    val titulo: String,
    val interpretacion: String,
    val siguientePaso: String?,
    val hallazgos: List<String>,
    val bajaConfianza: Boolean,
    val detalle: DetallePrueba,
)

interface DetallePrueba {
    fun json(): JSONObject
}
