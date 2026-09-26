package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

enum class CondicionMotor { FRIO, TIBIO, SIN_DATO }

enum class PatronArranque(val veredicto: Veredicto) {
    SIN_DATOS(Veredicto.ATENCION),
    NO_ARRANCO(Veredicto.FALLA),
    SE_APAGA(Veredicto.FALLA),
    SALTOS_ECT(Veredicto.FALLA),
    VARIOS_INTENTOS(Veredicto.ATENCION),
    IRREGULAR(Veredicto.ATENCION),
    NORMAL(Veredicto.OK),
}

// Con el contacto puesto y tras el reposo, motor, aire y ambiente deberían marcar casi lo mismo.
data class PlausibilidadFrio(
    val ectC: Double?,
    val iatC: Double?,
    val ambienteC: Double?,
    val limiteC: Double,
    val banda: BandaReferencia?,
    val condicion: CondicionMotor,
) {
    val diferenciaC: Double? get() = if (ectC != null && iatC != null) ectC - iatC else null
    val motorBajoAire: Boolean get() = (diferenciaC ?: 0.0) <= -limiteC
    val aireLejosDelAmbiente: Boolean
        get() = condicion == CondicionMotor.FRIO && iatC != null && ambienteC != null && abs(iatC - ambienteC) >= limiteC
    val coherente: Boolean get() = !motorBajoAire && !aireLejosDelAmbiente
}

data class ArranqueMedido(val arranco: Boolean, val intentos: Int, val tiempoS: Double?, val tInicioMs: Long?)

data class PerfilMinimoRapido(val rpmInicio: Double, val ectInicioC: Double, val rpmFin: Double, val ectFinC: Double, val baja: Boolean?)

data class AnalisisArranqueFrio(
    val plausibilidad: PlausibilidadFrio,
    val arranque: ArranqueMedido,
    val apagones: Int,
    val perfil: PerfilMinimoRapido?,
    val tiempoHasta60S: Double?,
    val ectMaxC: Double?,
    val saltosEct: List<Escalon>,
    val caidaEctMaxC: Double,
    val comprobaciones: List<Comprobacion>,
    val patron: PatronArranque,
) : DetallePrueba {

    val ectBajaMientrasCalienta: Boolean get() = saltosEct.isEmpty() && caidaEctMaxC > AnalizadorArranqueFrio.CAIDA_ECT_TOLERADA_C

    override fun json(): JSONObject = JSONObject()
        .put("patron", patron.name)
        .put("plausibilidad", plausibilidadJson())
        .put("arranque", arranqueJson())
        .put("apagones", apagones)
        .put("perfilMinimoRapido", perfil?.let(::perfilJson) ?: JSONObject.NULL)
        .put("tiempoHasta60S", tiempoHasta60S?.let { r(it, 1) } ?: JSONObject.NULL)
        .put("ectMaxC", ectMaxC?.let { r(it, 1) } ?: JSONObject.NULL)
        .put("saltosEct", JSONArray(saltosEct.map(::saltoJson)))
        .put("caidaEctMaxC", r(caidaEctMaxC, 1))
        .put("comprobaciones", JSONArray(comprobaciones.map(ComprobacionJson::de)))

    private fun plausibilidadJson() = JSONObject()
        .put("condicion", plausibilidad.condicion.name)
        .put("ectC", nulo(plausibilidad.ectC))
        .put("iatC", nulo(plausibilidad.iatC))
        .put("ambienteC", nulo(plausibilidad.ambienteC))
        .put("diferenciaC", nulo(plausibilidad.diferenciaC))
        .put("limiteC", plausibilidad.limiteC)
        .put("origenLimite", plausibilidad.banda?.etiquetaOrigen ?: JSONObject.NULL)
        .put("coherente", plausibilidad.coherente)

    private fun arranqueJson() = JSONObject()
        .put("arranco", arranque.arranco)
        .put("intentos", arranque.intentos)
        .put("tiempoS", nulo(arranque.tiempoS))

    private fun perfilJson(p: PerfilMinimoRapido) = JSONObject()
        .put("rpmInicio", r(p.rpmInicio, 0))
        .put("ectInicioC", r(p.ectInicioC, 1))
        .put("rpmFin", r(p.rpmFin, 0))
        .put("ectFinC", r(p.ectFinC, 1))
        .put("baja", p.baja ?: JSONObject.NULL)

    private fun saltoJson(e: Escalon) = JSONObject().put("t_ms", e.tMs).put("deltaC", r(e.delta, 1)).put("duracionMs", e.duracionMs)

    private fun nulo(x: Double?): Any = x?.let { r(it, 1) } ?: JSONObject.NULL

    private fun r(x: Double, decimales: Int) = FormatoTaller.redondear(x, decimales)
}
