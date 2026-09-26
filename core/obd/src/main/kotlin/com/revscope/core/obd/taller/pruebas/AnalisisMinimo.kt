package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject

enum class PatronMinimo(val veredicto: Veredicto) {
    SE_APAGA(Veredicto.FALLA),
    INESTABLE(Veredicto.FALLA),
    RETORNO_BAJO(Veredicto.ATENCION),
    FUERA_DE_BANDA(Veredicto.ATENCION),
    TPS_RALENTI_FUERA(Veredicto.ATENCION),
    SIN_RETORNOS(Veredicto.ATENCION),
    SIN_DATOS(Veredicto.ATENCION),
    NORMAL(Veredicto.OK),
}

data class Oscilacion(val lobulos: Int, val amplitudRpm: Double, val periodoS: Double?)

// Inicio y fin salen de la recta ajustada: con ruido, la primera y la última muestra engañan.
data class EstadisticaMinimo(
    val n: Int,
    val duracionS: Double,
    val mediaRpm: Double,
    val desviacionRpm: Double,
    val minRpm: Double,
    val maxRpm: Double,
    val derivaRpmS: Double,
    val inicioRpm: Double,
    val finRpm: Double,
    val oscilacion: Oscilacion?,
    val apagones: Int,
    val inestable: Boolean,
)

data class TpsRalenti(val minV: Double, val maxV: Double, val mediaV: Double, val banda: BandaReferencia?, val posicion: PosicionEnBanda?)

data class RetornoMedido(
    val clave: String,
    val acelerada: Boolean,
    val picoRpm: Double,
    val valleRpm: Double?,
    val valleFraccion: Double?,
    val tiempoS: Double?,
    val seApago: Boolean,
)

data class AnalisisMinimo(
    val minimo: EstadisticaMinimo?,
    val referenciaRpm: Double?,
    val ectMediaC: Double?,
    val tpsRalenti: TpsRalenti?,
    val retornos: List<RetornoMedido>,
    val comprobaciones: List<Comprobacion>,
    val patron: PatronMinimo,
) : DetallePrueba {

    val apagonesAlSoltar: Int get() = retornos.count { it.seApago }
    val retornosValidos: List<RetornoMedido> get() = retornos.filter { it.acelerada }

    fun comprobacion(clave: String): Comprobacion? = comprobaciones.firstOrNull { it.clave == clave }

    override fun json(): JSONObject = JSONObject()
        .put("patron", patron.name)
        .put("referenciaRpm", referenciaRpm?.let { r(it, 0) } ?: JSONObject.NULL)
        .put("ectMediaC", ectMediaC?.let { r(it, 1) } ?: JSONObject.NULL)
        .put("minimo", minimo?.let(::minimoJson) ?: JSONObject.NULL)
        .put("tpsRalenti", tpsRalenti?.let(::tpsJson) ?: JSONObject.NULL)
        .put("retornos", JSONArray(retornos.map(::retornoJson)))
        .put("apagonesAlSoltar", apagonesAlSoltar)
        .put("comprobaciones", JSONArray(comprobaciones.map(ComprobacionJson::de)))

    private fun minimoJson(m: EstadisticaMinimo) = JSONObject()
        .put("n", m.n)
        .put("duracionS", r(m.duracionS, 1))
        .put("mediaRpm", r(m.mediaRpm, 0))
        .put("desviacionRpm", r(m.desviacionRpm, 1))
        .put("minRpm", r(m.minRpm, 0))
        .put("maxRpm", r(m.maxRpm, 0))
        .put("derivaRpmS", r(m.derivaRpmS, 1))
        .put("inicioRpm", r(m.inicioRpm, 0))
        .put("finRpm", r(m.finRpm, 0))
        .put("apagones", m.apagones)
        .put("inestable", m.inestable)
        .put("oscilacion", m.oscilacion?.let(::oscilacionJson) ?: JSONObject.NULL)

    private fun oscilacionJson(o: Oscilacion) = JSONObject()
        .put("lobulos", o.lobulos)
        .put("amplitudRpm", r(o.amplitudRpm, 0))
        .put("periodoS", o.periodoS?.let { r(it, 1) } ?: JSONObject.NULL)

    private fun tpsJson(t: TpsRalenti) = JSONObject()
        .put("minV", r(t.minV, 3))
        .put("maxV", r(t.maxV, 3))
        .put("mediaV", r(t.mediaV, 3))
        .put("posicion", t.posicion?.name ?: JSONObject.NULL)

    private fun retornoJson(x: RetornoMedido) = JSONObject()
        .put("clave", x.clave)
        .put("acelerada", x.acelerada)
        .put("picoRpm", r(x.picoRpm, 0))
        .put("valleRpm", x.valleRpm?.let { r(it, 0) } ?: JSONObject.NULL)
        .put("valleFraccion", x.valleFraccion?.let { r(it, 3) } ?: JSONObject.NULL)
        .put("tiempoS", x.tiempoS?.let { r(it, 1) } ?: JSONObject.NULL)
        .put("seApago", x.seApago)

    private fun r(x: Double, decimales: Int) = FormatoTaller.redondear(x, decimales)
}

object ComprobacionJson {
    fun de(c: Comprobacion): JSONObject = JSONObject()
        .put("clave", c.clave)
        .put("etiqueta", c.etiqueta)
        .put("valor", c.valor?.let { FormatoTaller.redondear(it, 3) } ?: JSONObject.NULL)
        .put("unidad", c.unidad)
        .put("cumple", c.cumple)
        .put("posicion", c.posicion?.name ?: JSONObject.NULL)
        .put("banda", c.banda?.let(::banda) ?: JSONObject.NULL)

    private fun banda(b: BandaReferencia) = JSONObject()
        .put("min", b.min ?: JSONObject.NULL)
        .put("max", b.max ?: JSONObject.NULL)
        .put("unidad", b.unidad)
        .put("origen", b.etiquetaOrigen)
}
