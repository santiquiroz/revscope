package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject

enum class PatronTps(val veredicto: Veredicto) {
    CORTES_O_SALTOS(Veredicto.FALLA),
    SENAL_BAJA_TODO_EL_RECORRIDO(Veredicto.FALLA),
    SENAL_ALTA(Veredicto.FALLA),
    RANGO_DESEMPENO(Veredicto.ATENCION),
    SIN_BARRIDO(Veredicto.ATENCION),
    NORMAL(Veredicto.OK),
}

data class Comprobacion(
    val clave: String,
    val etiqueta: String,
    val valor: Double?,
    val unidad: String,
    val banda: BandaReferencia?,
    val posicion: PosicionEnBanda?,
) {
    val cumple: Boolean get() = valor != null && (posicion == null || posicion == PosicionEnBanda.DENTRO)

    companion object {
        fun contra(clave: String, etiqueta: String, valor: Double?, unidad: String, bandas: Map<String, BandaReferencia>): Comprobacion {
            val banda = bandas[clave]
            return Comprobacion(clave, etiqueta, valor, unidad, banda, valor?.let { banda?.clasificar(it) })
        }

        fun siNo(clave: String, etiqueta: String, cumple: Boolean) =
            Comprobacion(clave, etiqueta, if (cumple) 1.0 else null, "", null, null)
    }
}

data class AnalisisBarridoTps(
    val vref: ReferenciaVoltaje,
    val pasos: List<EstadisticaPaso>,
    val cerradoV: Double,
    val medioV: Double,
    val fondoV: Double,
    val recorridoTipicoV: Double?,
    val repetibilidadV: Double,
    val ruidoMaxV: Double,
    val comprobaciones: List<Comprobacion>,
    val irregularidades: List<Irregularidad>,
    val tasaHz: Double,
    val patron: PatronTps,
    val barridoEvaluado: Boolean = true,
) : DetallePrueba {

    val recorridoV: Double get() = fondoV - cerradoV
    val ordenCorrecto: Boolean get() = cerradoV < medioV && medioV < fondoV
    val linealidad: Double? get() = if (recorridoV > 0) (medioV - cerradoV) / recorridoV else null
    val fraccionRecorrido: Double? get() = recorridoTipicoV?.takeIf { it > 0 }?.let { recorridoV / it }
    val bajaConfianza: Boolean get() = tasaHz < AnalizadorBarridoTps.TASA_CONFIABLE_HZ

    fun paso(clave: String): EstadisticaPaso? = pasos.firstOrNull { it.clave == clave }

    fun comprobacion(clave: String): Comprobacion? = comprobaciones.firstOrNull { it.clave == clave }

    fun cuenta(tipo: TipoIrregularidad): Int = irregularidades.count { it.tipo == tipo }

    override fun json(): JSONObject = JSONObject()
        .put("patron", patron.name)
        .put("vref", JSONObject().put("voltios", vref.voltios).put("origen", vref.origen))
        .put("cerradoV", r3(cerradoV))
        .put("medioV", r3(medioV))
        .put("fondoV", r3(fondoV))
        .put("recorridoV", r3(recorridoV))
        .put("recorridoTipicoV", recorridoTipicoV?.let(::r3) ?: JSONObject.NULL)
        .put("fraccionRecorrido", fraccionRecorrido?.let(::r3) ?: JSONObject.NULL)
        .put("linealidad", linealidad?.let(::r3) ?: JSONObject.NULL)
        .put("repetibilidadV", r3(repetibilidadV))
        .put("ruidoMaxV", r3(ruidoMaxV))
        .put("ordenCorrecto", ordenCorrecto)
        .put("tasaHz", FormatoTaller.redondear(tasaHz, 1))
        .put("bajaConfianza", bajaConfianza)
        .put("barridoEvaluado", barridoEvaluado)
        .put("pasos", JSONArray(pasos.map(::pasoJson)))
        .put("comprobaciones", JSONArray(comprobaciones.map(::comprobacionJson)))
        .put("irregularidades", JSONArray(irregularidades.map(::irregularidadJson)))

    private fun pasoJson(p: EstadisticaPaso) = JSONObject()
        .put("clave", p.clave)
        .put("n", p.n)
        .put("hz", FormatoTaller.redondear(p.hz, 1))
        .put("minPct", r3(p.minPct)).put("maxPct", r3(p.maxPct)).put("mediaPct", r3(p.mediaPct)).put("desvPct", r3(p.desvPct))
        .put("minV", r3(p.minV)).put("maxV", r3(p.maxV)).put("mediaV", r3(p.mediaV)).put("ppV", r3(p.ppV))

    private fun comprobacionJson(c: Comprobacion) = JSONObject()
        .put("clave", c.clave)
        .put("etiqueta", c.etiqueta)
        .put("valor", c.valor?.let(::r3) ?: JSONObject.NULL)
        .put("unidad", c.unidad)
        .put("cumple", c.cumple)
        .put("posicion", c.posicion?.name ?: JSONObject.NULL)
        .put("banda", c.banda?.let(::bandaJson) ?: JSONObject.NULL)

    private fun bandaJson(b: BandaReferencia) = JSONObject()
        .put("min", b.min ?: JSONObject.NULL)
        .put("max", b.max ?: JSONObject.NULL)
        .put("unidad", b.unidad)
        .put("origen", b.etiquetaOrigen)

    private fun irregularidadJson(i: Irregularidad) = JSONObject()
        .put("tipo", i.tipo.name)
        .put("t_ms", i.tMs)
        .put("valorV", r3(i.valorV))
        .put("duracionMs", i.duracionMs)

    private fun r3(x: Double) = FormatoTaller.redondear(x, 3)
}

data class SinDatosTps(val pasosSinMuestras: List<String>) : DetallePrueba {
    override fun json(): JSONObject = JSONObject().put("patron", JSONObject.NULL).put("pasosSinMuestras", JSONArray(pasosSinMuestras))
}
