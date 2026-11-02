package com.revscope.core.obd.session

import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.perfilcan.ClavesSenal
import com.revscope.core.obd.perfilcan.FiltroFuentes
import com.revscope.core.obd.perfilcan.FuenteSenal
import com.revscope.core.obd.perfilcan.LectorBusCan
import com.revscope.core.obd.perfilcan.ModoFuente
import com.revscope.core.obd.perfilcan.PerfilCan
import com.revscope.core.obd.perfilcan.PerfilesCanRepositorio
import com.revscope.core.obd.perfilcan.ResolutorFuentes
import com.revscope.core.obd.perfilcan.efectivo
import com.revscope.core.obd.perfilcan.fuentesPorSenalDe
import com.revscope.core.obd.perfilcan.modoFuenteDe
import com.revscope.core.obd.telemetry.PidScheduler
import com.revscope.core.obd.telemetry.PollingGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections

internal data class ConfigBus(
    val perfil: PerfilCan?,
    val modo: ModoFuente,
    val preferencias: Map<String, FuenteSenal>,
) {
    companion object {
        fun de(profile: VehicleProfileEntity?, perfiles: PerfilesCanRepositorio): ConfigBus {
            val perfil = profile?.perfilCanId?.let(perfiles::leer)
            return ConfigBus(
                perfil,
                modoFuenteDe(profile?.fuenteDatos).efectivo(perfil),
                fuentesPorSenalDe(profile?.fuentesPorSenal),
            )
        }
    }
}

/** Lecturas del bus de un enlace: una instancia por arranque de telemetría (avisos únicos por sesión). */
internal class BusSesion(
    private val bt: Transport,
    private val gate: PollingGate,
    private val scheduler: PidScheduler,
    private val avisar: (String) -> Unit,
    private val ahora: () -> Long = System::currentTimeMillis,
) {

    private val resolutor = MutableStateFlow<ResolutorFuentes?>(null)
    private val avisados = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var desactivado = false

    fun aceptaPid(r: ObdReading): Boolean = resolutor.value?.let { FiltroFuentes.aceptaPid(r, it) } ?: true

    fun aceptaBus(r: ObdReading): Boolean = resolutor.value?.let { FiltroFuentes.aceptaBus(r, it) } ?: false

    fun aceptaDerivada(r: ObdReading): Boolean = resolutor.value?.let { FiltroFuentes.aceptaDerivada(r, it) } ?: true

    @OptIn(ExperimentalCoroutinesApi::class)
    fun lecturas(configuraciones: Flow<ConfigBus>): Flow<ObdReading> =
        configuraciones.distinctUntilChanged().flatMapLatest(::leer)

    private fun leer(config: ConfigBus): Flow<ObdReading> = channelFlow {
        val perfil = config.perfil
        if (desactivado || perfil == null || config.modo == ModoFuente.ESTANDAR) return@channelFlow
        val res = ResolutorFuentes(
            perfil, config.modo, config.preferencias, esCan = true, LectorBusCan.intervaloVentanaMs(config.modo), ahora,
        )
        val lector = LectorBusCan(bt, gate, perfil, config.modo, res, ahora)
        resolutor.value = res
        val vigilancia = launch { vigilarCaducidad(res, perfil) }
        if (config.modo == ModoFuente.SOLO_BUS) scheduler.setPaused(true)
        try {
            lector.lecturas().collect { send(it) }
            if (lector.sinMonitor.value) {
                desactivado = true
                avisarUnaVez(AVISO_SIN_MONITOR)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "BusSesion: la escucha del bus falló; se sigue con el OBD estándar")
            desactivado = true
        } finally {
            resolutor.value = null
            vigilancia.cancel()
            if (config.modo == ModoFuente.SOLO_BUS) scheduler.setPaused(false)
        }
    }

    private suspend fun vigilarCaducidad(res: ResolutorFuentes, perfil: PerfilCan) {
        while (true) {
            delay(VIGILANCIA_MS)
            res.caducadas().forEach { clave -> avisarUnaVez(avisoCaducada(perfil, clave)) }
        }
    }

    private fun avisarUnaVez(texto: String) {
        if (avisados.add(texto)) avisar(texto)
    }

    private fun avisoCaducada(perfil: PerfilCan, clave: String): String {
        val nombre = perfil.senales.firstOrNull { it.clave == clave }?.nombre ?: clave
        val respaldo = if (clave == ClavesSenal.GEAR) "la estimación" else "el OBD estándar"
        return "No llegan datos del bus para $nombre; usando $respaldo"
    }

    private companion object {
        const val VIGILANCIA_MS = 1_000L
        const val AVISO_SIN_MONITOR = "Tu adaptador no permite escuchar el bus; usando el OBD estándar"
    }
}
