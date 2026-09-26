package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

enum class PatronMapBaro(val veredicto: Veredicto) {
    SIN_DATOS(Veredicto.ATENCION),
    SIN_REFERENCIA(Veredicto.ATENCION),
    MAP_BAJO(Veredicto.FALLA),
    MAP_ALTO(Veredicto.FALLA),
    NO_CONCLUYENTE(Veredicto.ATENCION),
    NORMAL(Veredicto.OK),
}

data class MapMedido(val mediaKpa: Double, val minKpa: Double, val maxKpa: Double, val n: Int) {
    val variacionKpa: Double get() = maxKpa - minKpa
}

data class AnalisisMapBaro(
    val map: MapMedido?,
    val referencia: ReferenciaBarometrica?,
    val alternativa: ReferenciaBarometrica?,
    val banda: BandaReferencia?,
    val comprobaciones: List<Comprobacion>,
    val patron: PatronMapBaro,
) : DetallePrueba {

    val deltaKpa: Double? get() = if (map != null && referencia != null) map.mediaKpa - referencia.kPa else null

    // La banda típica más lo que puede errar la fuente: con una estimación por altitud, la tolerancia crece.
    val toleranciaKpa: Double? get() = referencia?.let { (banda?.max ?: AnalizadorMapBaro.BANDA_TIPICA_KPA) + it.incertidumbreKpa }

    val altitudEquivalenteM: Double? get() = map?.let { FuenteBarometrica.altitudPorPresion(it.mediaKpa) }

    val deltaAlternativaKpa: Double? get() = if (map != null && alternativa != null) map.mediaKpa - alternativa.kPa else null

    val toleranciaAlternativaKpa: Double?
        get() = alternativa?.let { (banda?.max ?: AnalizadorMapBaro.BANDA_TIPICA_KPA) + it.incertidumbreKpa }

    // La segunda fuente no concuerda con el MAP: si la primera es el PID 33, puede salir del mismo sensor.
    val contrasteDiscrepa: Boolean
        get() {
            val delta = deltaAlternativaKpa ?: return false
            val tolerancia = toleranciaAlternativaKpa ?: return false
            return abs(delta) > tolerancia
        }

    override fun json(): JSONObject = JSONObject()
        .put("patron", patron.name)
        .put("mapKpa", nulo(map?.mediaKpa))
        .put("mapMinKpa", nulo(map?.minKpa))
        .put("mapMaxKpa", nulo(map?.maxKpa))
        .put("n", map?.n ?: 0)
        .put("referencia", referencia?.let(::referenciaJson) ?: JSONObject.NULL)
        .put("alternativa", alternativa?.let(::referenciaJson) ?: JSONObject.NULL)
        .put("deltaKpa", nulo(deltaKpa))
        .put("toleranciaKpa", nulo(toleranciaKpa))
        .put("altitudEquivalenteM", altitudEquivalenteM?.let { FormatoTaller.redondear(it, 0) } ?: JSONObject.NULL)
        .put("comprobaciones", JSONArray(comprobaciones.map(ComprobacionJson::de)))

    private fun referenciaJson(r: ReferenciaBarometrica) = JSONObject()
        .put("kPa", FormatoTaller.redondear(r.kPa, 2))
        .put("origen", r.origen.name)
        .put("etiqueta", r.etiqueta)
        .put("incertidumbreKpa", r.incertidumbreKpa)

    private fun nulo(x: Double?): Any = x?.let { FormatoTaller.redondear(it, 2) } ?: JSONObject.NULL
}
