package com.revscope.core.obd.perfilcan

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class ModoFuente { ESTANDAR, COMBINADO, SOLO_BUS }

enum class FuenteSenal { AUTO, PID, BUS, GPS, CALCULADA }

fun ModoFuente.efectivo(perfil: PerfilCan?): ModoFuente = if (perfil == null) ModoFuente.ESTANDAR else this

fun modoFuenteDe(texto: String?): ModoFuente =
    ModoFuente.entries.firstOrNull { it.name == texto } ?: ModoFuente.ESTANDAR

fun fuentesPorSenalDe(json: String?): Map<String, FuenteSenal> {
    val objeto = runCatching { JSONObject(json ?: "{}") }.getOrNull() ?: return emptyMap()
    return objeto.keys().asSequence().mapNotNull { clave ->
        FuenteSenal.entries.firstOrNull { it.name == objeto.optString(clave) }?.let { clave to it }
    }.toMap()
}

class ResolutorFuentes(
    perfil: PerfilCan?,
    modo: ModoFuente,
    private val preferencias: Map<String, FuenteSenal>,
    private val esCan: Boolean,
    private val intervaloVentanaMs: Long,
    private val ahora: () -> Long,
) {

    private val modoEfectivo = modo.efectivo(perfil)
    private val senales: Map<String, SenalCan> = perfil?.senales.orEmpty().associateBy { it.clave }
    private val ultimaTrama = ConcurrentHashMap<String, Long>()
    private val reportadas = mutableSetOf<String>()

    fun usaBus(clave: String): Boolean = elegible(clave) && fresca(clave)

    fun registrarTrama(clave: String) {
        ultimaTrama[clave] = ahora()
    }

    @Synchronized
    fun caducadas(): Set<String> {
        val nuevas = ultimaTrama.keys.filter { elegible(it) && !fresca(it) && it !in reportadas }.toSet()
        reportadas += nuevas
        return nuevas
    }

    private fun elegible(clave: String): Boolean {
        if (!esCan || modoEfectivo == ModoFuente.ESTANDAR) return false
        val senal = senales[clave] ?: return false
        val preferencia = preferencias[clave] ?: FuenteSenal.AUTO
        return senal.estado == EstadoSenal.VERIFICADA &&
            (preferencia == FuenteSenal.AUTO || preferencia == FuenteSenal.BUS)
    }

    private fun fresca(clave: String): Boolean {
        val ultima = ultimaTrama[clave] ?: return false
        val periodo = senales[clave]?.periodoMs ?: return false
        return ahora() - ultima <= FACTOR_CADUCIDAD * maxOf(periodo, intervaloVentanaMs)
    }

    private companion object {
        const val FACTOR_CADUCIDAD = 3
    }
}
