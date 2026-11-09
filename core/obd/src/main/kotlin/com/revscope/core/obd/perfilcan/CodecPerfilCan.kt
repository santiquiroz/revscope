package com.revscope.core.obd.perfilcan

import org.json.JSONArray
import org.json.JSONObject

object CodecPerfilCan {

    const val VERSION = 1
    const val TAMANO_MAXIMO_BYTES = 64 * 1024

    val ID_PERFIL = Regex("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*")

    private val ID_CAN = Regex("[0-9A-Fa-f]{3}|[0-9A-Fa-f]{8}")
    private val CAMPOS_PERSONALES = listOf("vin", "placa")

    fun leer(json: String): Result<PerfilCan> = runCatching {
        valida(json.toByteArray(Charsets.UTF_8).size <= TAMANO_MAXIMO_BYTES) {
            "tamaño: el perfil pasa de ${TAMANO_MAXIMO_BYTES / 1024} KB" // i18n-ignore
        }
        val raiz = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("json: no es un objeto JSON válido") } // i18n-ignore
        raiz.aPerfil()
    }

    fun escribir(p: PerfilCan): String = JSONObject()
        .put("version", p.version)
        .put("id", p.id)
        .put("vehiculo", aJson(p.vehiculo))
        .put("calibracionesEcu", JSONArray(p.calibracionesEcu))
        .put("protocolo", p.protocolo)
        .put("licencia", p.licencia)
        .put("autor", p.autor)
        .put("atribucion", p.atribucion)
        .put("senales", JSONArray(p.senales.map(::aJson)))
        .toString(2)

    private fun aJson(v: VehiculoPerfil): JSONObject = JSONObject()
        .put("marca", v.marca)
        .put("modelo", v.modelo)
        .put("anios", v.anios)
        .put("tipo", v.tipo)

    private fun aJson(s: SenalCan): JSONObject = JSONObject()
        .put("clave", s.clave)
        .put("nombre", s.nombre)
        .put("id", s.id)
        .put("bitInicio", s.bitInicio)
        .put("largo", s.largo)
        .put("orden", s.orden.name.lowercase())
        .put("firmado", s.firmado)
        .put("factor", s.factor)
        .put("offset", s.offset)
        .put("unidad", s.unidad)
        .put("tipo", s.tipo.name.lowercase())
        .put("valores", JSONObject().apply { s.valores.forEach { (crudo, texto) -> put(crudo.toString(), texto) } })
        .put("invalidos", JSONArray(s.invalidos.toList()))
        .put("periodoMs", s.periodoMs)
        .put("estado", s.estado.name.lowercase())
        .put("origen", s.origen)

    private fun JSONObject.aPerfil(): PerfilCan {
        rechazaPersonales(this)
        val version = optInt("version", -1)
        valida(version == VERSION) { "version: se esperaba $VERSION y vino ${opt("version")}" } // i18n-ignore
        val id = requerido("id")
        valida(ID_PERFIL.matches(id)) { "id: '$id' no es un id de perfil válido" } // i18n-ignore
        val vehiculo = optJSONObject("vehiculo") ?: throw IllegalArgumentException("vehiculo: falta") // i18n-ignore
        rechazaPersonales(vehiculo)
        val arreglo = optJSONArray("senales") ?: throw IllegalArgumentException("senales: falta") // i18n-ignore
        val senales = (0 until arreglo.length()).map { i ->
            val s = arreglo.optJSONObject(i) ?: throw IllegalArgumentException("senales[$i]: no es un objeto") // i18n-ignore
            runCatching { s.aSenal() }.getOrElse { throw IllegalArgumentException("senales[$i].${it.message}") }
        }
        valida(senales.map { it.clave }.toSet().size == senales.size) { "clave: hay señales repetidas" } // i18n-ignore
        return PerfilCan(
            version = version,
            id = id,
            vehiculo = vehiculo.aVehiculo(),
            calibracionesEcu = optJSONArray("calibracionesEcu")?.textos().orEmpty(),
            protocolo = optString("protocolo", ""),
            licencia = optString("licencia", ""),
            autor = optString("autor", ""),
            atribucion = optString("atribucion", ""),
            senales = senales,
        )
    }

    private fun JSONObject.aVehiculo() = VehiculoPerfil(
        marca = optString("marca", ""),
        modelo = optString("modelo", ""),
        anios = optString("anios", ""),
        tipo = optString("tipo", ""),
    )

    private fun JSONObject.aSenal(): SenalCan {
        val clave = requerido("clave")
        valida(clave in ClavesSenal.todas) { "clave: '$clave' no es una clave conocida" } // i18n-ignore
        val id = requerido("id")
        valida(ID_CAN.matches(id)) { "id: '$id' no es un ID CAN hexadecimal de 3 u 8 dígitos" } // i18n-ignore
        val bitInicio = optInt("bitInicio", -1)
        val largo = optInt("largo", -1)
        valida(bitInicio in 0..63 && largo in 1..64 && bitInicio + largo <= 64) {
            "bitInicio: el campo $bitInicio+$largo se sale de los bits 0..63" // i18n-ignore
        }
        val tipo = enumDe<TipoSenal>("tipo", requerido("tipo"))
        valida(tipo != TipoSenal.BANDERA || largo == 1) { "largo: una bandera ocupa 1 bit" } // i18n-ignore
        return SenalCan(
            clave = clave,
            nombre = optString("nombre", clave),
            id = id.uppercase(),
            bitInicio = bitInicio,
            largo = largo,
            orden = enumDe("orden", optString("orden", "big")),
            firmado = optBoolean("firmado", false),
            factor = optDouble("factor", 1.0),
            offset = optDouble("offset", 0.0),
            unidad = optString("unidad", ""),
            tipo = tipo,
            valores = optJSONObject("valores")?.aValores().orEmpty(),
            invalidos = optJSONArray("invalidos")?.aLongs().orEmpty(),
            periodoMs = optLong("periodoMs", PERIODO_POR_DEFECTO_MS),
            estado = enumDe("estado", optString("estado", "sin_verificar")),
            origen = optString("origen", ""),
        )
    }

    private inline fun <reified T : Enum<T>> enumDe(campo: String, texto: String): T =
        enumValues<T>().firstOrNull { it.name.equals(texto, ignoreCase = true) }
            ?: throw IllegalArgumentException("$campo: '$texto' no es un valor válido") // i18n-ignore

    private fun JSONObject.aValores(): Map<Long, String> = keys().asSequence().associate { crudo ->
        val numero = crudo.toLongOrNull() ?: throw IllegalArgumentException("valores: '$crudo' no es un número") // i18n-ignore
        numero to optString(crudo)
    }

    private fun JSONArray.aLongs(): Set<Long> = (0 until length()).map { getLong(it) }.toSet()

    private fun JSONArray.textos(): List<String> = (0 until length()).map { optString(it) }

    private fun JSONObject.requerido(campo: String): String =
        optString(campo, "").takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("$campo: falta") // i18n-ignore

    private fun rechazaPersonales(objeto: JSONObject) {
        CAMPOS_PERSONALES.firstOrNull { objeto.has(it) }?.let {
            throw IllegalArgumentException("$it: un perfil compartible no lleva datos del vehículo concreto") // i18n-ignore
        }
    }

    private inline fun valida(condicion: Boolean, mensaje: () -> String) {
        if (!condicion) throw IllegalArgumentException(mensaje())
    }

    private const val PERIODO_POR_DEFECTO_MS = 100L
}
