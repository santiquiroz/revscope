package com.revscope.core.obd.taller.modelo

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.OrigenBanda
import org.json.JSONArray
import org.json.JSONObject

data class ModeloSemilla(val version: Int, val conocimiento: ConocimientoModelo)

object CodecConocimiento {

    fun leerCatalogo(json: String): List<ModeloSemilla> =
        JSONObject(json).getJSONArray("modelos").objetos().map(::leerSemilla)

    fun notasAJson(notas: List<NotaModelo>): String =
        JSONArray(notas.map(::notaAJson)).toString()

    fun notasDesdeJson(texto: String): List<NotaModelo> =
        if (texto.isBlank()) emptyList() else JSONArray(texto).objetos().map(::leerNota)

    fun cableadoAJson(cableado: List<CableadoSensor>): String =
        JSONArray(cableado.map(::cableadoAJson)).toString()

    fun cableadoDesdeJson(texto: String): List<CableadoSensor> =
        if (texto.isBlank()) emptyList() else JSONArray(texto).objetos().map(::leerCableado)

    private fun leerSemilla(o: JSONObject): ModeloSemilla =
        ModeloSemilla(version = o.getInt("seedVersion"), conocimiento = leerConocimiento(o))

    private fun leerConocimiento(o: JSONObject): ConocimientoModelo {
        val clave = o.getString("clave")
        return ConocimientoModelo(
            clave = clave,
            nombre = o.getString("nombre"),
            tipo = VehicleType.valueOf(o.getString("tipo")),
            ecu = o.textoONulo("ecu"),
            fuenteEcu = o.textoONulo("fuenteEcu"),
            notasProtocolo = o.optString("notasProtocolo"),
            notas = o.arreglo("notas").map(::leerNota),
            cableado = o.arreglo("cableado").map(::leerCableado),
            repuestos = o.arreglo("repuestos").map(::leerRepuesto),
            bandas = o.arreglo("bandas").map(::leerBandaConFuente),
        )
    }

    private fun leerNota(o: JSONObject) = NotaModelo(
        tipo = TipoNota.valueOf(o.getString("tipo")),
        texto = o.getString("texto"),
        fuente = o.getString("fuente"),
        url = o.textoONulo("url"),
    )

    private fun notaAJson(n: NotaModelo): JSONObject = JSONObject()
        .put("tipo", n.tipo.name)
        .put("texto", n.texto)
        .put("fuente", n.fuente)
        .putOpt("url", n.url)

    private fun leerCableado(o: JSONObject) = CableadoSensor(
        sensor = o.getString("sensor"),
        titulo = o.getString("titulo"),
        cables = o.getJSONArray("cables").objetos().map(::leerCable),
        fuente = o.getString("fuente"),
    )

    private fun cableadoAJson(c: CableadoSensor): JSONObject = JSONObject()
        .put("sensor", c.sensor)
        .put("titulo", c.titulo)
        .put("cables", JSONArray(c.cables.map(::cableAJson)))
        .put("fuente", c.fuente)

    private fun leerCable(o: JSONObject) =
        CableModelo(funcion = FuncionCable.valueOf(o.getString("funcion")), color = o.textoONulo("color"))

    private fun cableAJson(c: CableModelo): JSONObject =
        JSONObject().put("funcion", c.funcion.name).putOpt("color", c.color)

    private fun leerRepuesto(o: JSONObject) = RepuestoModelo(
        rol = o.getString("rol"),
        referencia = o.getString("referencia"),
        descripcion = o.getString("descripcion"),
        tipo = TipoRepuesto.valueOf(o.getString("tipo")),
        nota = o.optString("nota"),
        fuente = o.getString("fuente"),
    )

    // En la semilla solo hay bandas con fuente citada: las típicas viven en BandasTipicas.
    private fun leerBandaConFuente(o: JSONObject) = BandaReferencia(
        clave = o.getString("clave"),
        min = o.numeroONulo("min"),
        max = o.numeroONulo("max"),
        unidad = o.getString("unidad"),
        origen = OrigenBanda.FUENTE,
        fuente = o.getString("fuente"),
    )

    private fun JSONObject.arreglo(nombre: String): List<JSONObject> =
        optJSONArray(nombre)?.objetos().orEmpty()

    private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.textoONulo(nombre: String): String? =
        if (isNull(nombre)) null else getString(nombre).takeIf { it.isNotBlank() }

    private fun JSONObject.numeroONulo(nombre: String): Double? =
        if (isNull(nombre)) null else getDouble(nombre)
}
