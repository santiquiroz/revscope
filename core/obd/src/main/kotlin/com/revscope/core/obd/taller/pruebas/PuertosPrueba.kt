package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.telemetry.captura.CapturaRapida
import com.revscope.core.obd.telemetry.captura.ConfigCaptura
import com.revscope.core.obd.telemetry.captura.InicioCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

interface CapturaPrueba {
    val ultimoResumen: StateFlow<ResumenCaptura?>
    suspend fun iniciar(config: ConfigCaptura): Result<InicioCaptura>
    suspend fun detener(motivo: String = CapturaRapida.MOTIVO_USUARIO): ResumenCaptura?
    fun activa(): Boolean
    fun transcurridoMs(): Long?
    fun muestrasActuales(): List<MuestraCaptura>
}

interface EnlacePrueba {
    fun conectado(): Boolean
    fun lecturas(): Map<String, ObdReading>
    fun soportado(pid: String): Boolean
}

class EnlacePruebaObd @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val registry: PidRegistry,
) : EnlacePrueba {
    override fun conectado(): Boolean = sessionManager.connectionState.value is ConnectionState.Connected
    override fun lecturas(): Map<String, ObdReading> = sessionManager.readings.value
    override fun soportado(pid: String): Boolean = registry.getDefinition(pid) != null && registry.isSupported(pid)
}

fun interface AnunciadorTaller {
    fun anunciar(texto: String)
}

class AnunciadorTallerVoz @Inject constructor(private val alertas: AlertsEngine) : AnunciadorTaller {
    override fun anunciar(texto: String) = alertas.anunciarPasoTaller(texto)
}
