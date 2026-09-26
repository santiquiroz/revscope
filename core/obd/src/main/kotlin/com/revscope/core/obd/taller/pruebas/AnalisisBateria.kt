package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject

enum class PatronBateria(val veredicto: Veredicto) {
    SIN_DATOS(Veredicto.ATENCION),
    SOBRECARGA(Veredicto.FALLA),
    ARRANQUE_BAJO(Veredicto.FALLA),
    ADAPTADOR_REINICIADO(Veredicto.ATENCION),
    NO_CARGA(Veredicto.FALLA),
    CARGA_ALTA(Veredicto.ATENCION),
    SIN_CARGA_EN_MINIMO(Veredicto.ATENCION),
    BATERIA_BAJA(Veredicto.ATENCION),
    NORMAL(Veredicto.OK),
}

data class TramoVoltaje(val mediaV: Double, val minV: Double, val maxV: Double, val n: Int)

// [sinRespuestaMs]: el hueco más largo sin lecturas en el paso; [enlacePerdido]: el paso terminó sin adaptador.
data class ArranqueVoltaje(
    val valleV: Double?,
    val caidaV: Double?,
    val tasaHz: Double,
    val sinRespuestaMs: Long,
    val enlacePerdido: Boolean,
) {
    val visto: Boolean get() = (caidaV ?: 0.0) >= AnalizadorBateria.CAIDA_ARRANQUE_VISTA_V
    val adaptadorReiniciado: Boolean get() = enlacePerdido || sinRespuestaMs >= AnalizadorBateria.SIN_RESPUESTA_MS
}

data class AnalisisBateria(
    val desfase: DesfaseVoltaje,
    val conFarola: Boolean,
    val contacto: TramoVoltaje?,
    val arranque: ArranqueVoltaje?,
    val minimo: TramoVoltaje?,
    val rpmAltas: TramoVoltaje?,
    val comprobaciones: List<Comprobacion>,
    val patron: PatronBateria,
) : DetallePrueba {

    val cargaMaxV: Double? get() = listOfNotNull(minimo?.mediaV, rpmAltas?.mediaV).maxOrNull()

    // Con pocas lecturas por segundo el valle medido puede quedarse corto: el real es igual o más bajo.
    val bajaConfianza: Boolean
        get() = arranque != null && arranque.tasaHz < AnalizadorBateria.TASA_CONFIABLE_HZ && !arranque.adaptadorReiniciado

    fun comprobacion(clave: String): Comprobacion? = comprobaciones.firstOrNull { it.clave == clave }

    override fun json(): JSONObject = JSONObject()
        .put("patron", patron.name)
        .put("conFarola", conFarola)
        .put("desfase", JSONObject().put("voltios", r(desfase.voltios, 2)).put("origen", desfase.origen))
        .put("contacto", contacto?.let(::tramoJson) ?: JSONObject.NULL)
        .put("arranque", arranque?.let(::arranqueJson) ?: JSONObject.NULL)
        .put("minimo", minimo?.let(::tramoJson) ?: JSONObject.NULL)
        .put("rpmAltas", rpmAltas?.let(::tramoJson) ?: JSONObject.NULL)
        .put("bajaConfianza", bajaConfianza)
        .put("comprobaciones", JSONArray(comprobaciones.map(ComprobacionJson::de)))

    private fun tramoJson(t: TramoVoltaje) = JSONObject()
        .put("mediaV", r(t.mediaV, 2))
        .put("minV", r(t.minV, 2))
        .put("maxV", r(t.maxV, 2))
        .put("n", t.n)

    private fun arranqueJson(a: ArranqueVoltaje) = JSONObject()
        .put("valleV", a.valleV?.let { r(it, 2) } ?: JSONObject.NULL)
        .put("caidaV", a.caidaV?.let { r(it, 2) } ?: JSONObject.NULL)
        .put("visto", a.visto)
        .put("tasaHz", r(a.tasaHz, 1))
        .put("sinRespuestaMs", a.sinRespuestaMs)
        .put("enlacePerdido", a.enlacePerdido)
        .put("adaptadorReiniciado", a.adaptadorReiniciado)

    private fun r(x: Double, decimales: Int) = FormatoTaller.redondear(x, decimales)
}
