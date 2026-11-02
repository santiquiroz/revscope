package com.revscope.core.obd.escucha

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapturasCanRepositorio internal constructor(
    private val carpeta: File,
    private val tope: Int = MAX_CAPTURAS,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, CARPETA))

    @Synchronized
    fun guardar(c: CapturaCan, gps: List<MuestraGps> = emptyList()) {
        carpeta.mkdirs()
        File(carpeta, "${c.id}.log").writeText(textoLog(c))
        if (gps.isNotEmpty()) archivoGps(c.id).writeText(GpsCsv.texto(gps))
        File(carpeta, "${c.id}.json").writeText(aJsonConOrigen(c.copy(muestrasGps = gps.size)).toString())
        podar()
    }

    @Synchronized
    fun listar(): List<CapturaCan> =
        runCatching { listarInterno() }
            .onFailure { Timber.w(it, "CapturasCanRepositorio: no se pudo listar") }
            .getOrDefault(emptyList())

    @Synchronized
    fun leer(id: String): CapturaCan? {
        if (!ID_VALIDO.matches(id)) return null
        return runCatching {
            val meta = leerMeta(id) ?: return null
            meta.copy(tramas = leerTramas(id, meta.inicioEpochMs))
        }.onFailure { Timber.w(it, "CapturasCanRepositorio: no se pudo leer $id") }.getOrNull()
    }

    @Synchronized
    fun existe(id: String): Boolean = ID_VALIDO.matches(id) && File(carpeta, "$id.json").exists()

    @Synchronized
    fun leerGps(id: String): List<MuestraGps> {
        if (!ID_VALIDO.matches(id)) return emptyList()
        val archivo = archivoGps(id)
        if (!archivo.exists()) return emptyList()
        return runCatching { archivo.readLines().drop(1).filter { it.isNotBlank() }.mapNotNull { GpsCsv.parsear(it) } }
            .onFailure { Timber.w(it, "CapturasCanRepositorio: no se pudo leer el GPS de $id") }
            .getOrDefault(emptyList())
    }

    private fun archivoGps(id: String) = File(carpeta, "$id.gps.csv")

    private fun textoLog(c: CapturaCan): String =
        c.tramas.joinToString("\n") { Candump.linea(it, c.inicioEpochMs) }.let { if (it.isEmpty()) "" else "$it\n" }

    private fun listarInterno(): List<CapturaCan> =
        (carpeta.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { desdeJson(it.readText()) }
            .sortedByDescending { it.inicioEpochMs }

    private fun leerMeta(id: String): CapturaCan? {
        val archivo = File(carpeta, "$id.json")
        if (!archivo.exists()) return null
        return desdeJson(archivo.readText())
    }

    private fun leerTramas(id: String, inicioEpochMs: Long): List<TramaCan> {
        val archivo = File(carpeta, "$id.log")
        if (!archivo.exists()) return emptyList()
        return archivo.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { Candump.parsear(it) }
            .map { Candump.aTrama(it, inicioEpochMs) }
    }

    private fun podar() {
        listarInterno().drop(tope).forEach { vieja ->
            File(carpeta, "${vieja.id}.log").delete()
            File(carpeta, "${vieja.id}.json").delete()
            archivoGps(vieja.id).delete()
        }
    }

    companion object {
        const val CARPETA = "capturas_can"
        const val MAX_CAPTURAS = 200
        private val ID_VALIDO = Regex("^[0-9]+-[a-z0-9_]{1,32}$")

        fun aJson(c: CapturaCan): JSONObject = JSONObject()
            .put("id", c.id)
            .put("etiqueta", c.etiqueta)
            .put("inicioEpochMs", c.inicioEpochMs)
            .put("duracionMs", c.duracionMs)
            .put("protocolo", c.protocolo)
            .put("bits29", c.bits29)
            .put("filtro", c.filtro ?: JSONObject.NULL)
            .put("silencioso", c.silencioso)
            .put("desbordes", c.desbordes)
            .put("errores", c.errores)
            .put("tramas", c.totalTramas)
            .put("huecos", JSONArray().apply {
                c.huecos.forEach { hueco -> put(JSONArray().put(hueco.desdeMs).put(hueco.hastaMs)) }
            })

        fun aJsonConOrigen(c: CapturaCan): JSONObject = aJson(c)
            .put("origen", c.origen)
            .put("muestrasGps", c.muestrasGps)

        fun desdeJson(texto: String): CapturaCan? = runCatching {
            val o = JSONObject(texto)
            CapturaCan(
                id = o.getString("id"),
                etiqueta = o.getString("etiqueta"),
                inicioEpochMs = o.getLong("inicioEpochMs"),
                duracionMs = o.getLong("duracionMs"),
                protocolo = o.getString("protocolo"),
                bits29 = o.getBoolean("bits29"),
                filtro = o.optString("filtro").takeUnless { o.isNull("filtro") },
                silencioso = o.getBoolean("silencioso"),
                desbordes = o.getInt("desbordes"),
                errores = o.getInt("errores"),
                tramas = emptyList(),
                totalTramas = o.optInt("tramas", 0),
                huecos = leerHuecos(o.optJSONArray("huecos")),
                origen = o.optString("origen", CapturaCan.ORIGEN_MCP),
                muestrasGps = o.optInt("muestrasGps", 0),
            )
        }.getOrNull()

        private fun leerHuecos(json: JSONArray?): List<Hueco> =
            (0 until (json?.length() ?: 0)).mapNotNull { indice ->
                val par = json?.optJSONArray(indice) ?: return@mapNotNull null
                if (par.length() != 2) return@mapNotNull null
                Hueco(par.optLong(0), par.optLong(1))
            }
    }
}
