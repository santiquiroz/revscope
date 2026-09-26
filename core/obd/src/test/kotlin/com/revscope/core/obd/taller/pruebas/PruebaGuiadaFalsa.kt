package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.taller.sesion.AdjuntosTaller
import com.revscope.core.obd.taller.sesion.BENELLI
import com.revscope.core.obd.taller.sesion.HistorialChequeosEnMemoria
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import com.revscope.core.obd.taller.sesion.TallerRepositoryEnMemoria
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.telemetry.captura.ConfigCaptura
import com.revscope.core.obd.telemetry.captura.InicioCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import java.io.File

// Captura con el reloj virtual del test: genera muestras del PID principal a [hz] con el valor que diga el guion.
class CapturaFalsa(
    private val reloj: () -> Long,
    private val valorEn: (Long) -> Double = { 0.0 },
    private val hz: Double = 10.0,
) : CapturaPrueba {
    private val _ultimo = MutableStateFlow<ResumenCaptura?>(null)
    override val ultimoResumen: StateFlow<ResumenCaptura?> = _ultimo

    val configs = mutableListOf<ConfigCaptura>()
    val motivosDetener = mutableListOf<String>()
    var fallarInicio: String? = null
    var rutaCsv: String? = null
    var manualActiva = false
    private var inicio: Long? = null
    private var fin: Long? = null

    override suspend fun iniciar(config: ConfigCaptura): Result<InicioCaptura> {
        fallarInicio?.let { return Result.failure(IllegalStateException(it)) }
        configs += config
        inicio = reloj()
        fin = null
        return Result.success(InicioCaptura("cap-$inicio", config.pids, emptyList(), listOf(config.pids), emptySet(), config.duracionMaxMs))
    }

    override suspend fun detener(motivo: String): ResumenCaptura? {
        if (!propiaActiva()) return null
        motivosDetener += motivo
        terminar(motivo)
        return _ultimo.value
    }

    fun terminarSola(motivo: String) = terminar(motivo)

    override fun activa(): Boolean = manualActiva || propiaActiva()

    override fun transcurridoMs(): Long? = inicio?.takeIf { propiaActiva() }?.let { reloj() - it }

    override fun muestrasActuales(): List<MuestraCaptura> {
        val desde = inicio ?: return emptyList()
        val hasta = (fin ?: reloj()) - desde
        val periodo = (1_000 / hz).toLong()
        return (0..hasta / periodo).map { i ->
            val t = i * periodo
            MuestraCaptura(i, t * 1_000, configs.last().pids.first(), valorEn(t), i, 60)
        }
    }

    private fun propiaActiva() = inicio != null && fin == null

    private fun terminar(motivo: String) {
        val desde = inicio ?: return
        fin = reloj()
        _ultimo.value = ResumenCaptura("cap-$desde", reloj() - desde, emptyList(), null, null, motivo, rutaCsv, configs.last().guiada)
    }
}

class EnlaceFalso(
    var conectado: Boolean = true,
    var lecturas: Map<String, ObdReading> = emptyMap(),
    var soportados: Set<String> = setOf("0C", "0D", "11"),
) : EnlacePrueba {
    override fun conectado() = conectado
    override fun lecturas() = lecturas
    override fun soportado(pid: String) = pid in soportados
}

class AnunciadorFalso : AnunciadorTaller {
    val dichos = mutableListOf<String>()
    override fun anunciar(texto: String) {
        dichos += texto
    }
}

// El controlador real con todo falso menos el registro de la sesión, que es el de verdad sobre memoria.
class MontajePrueba(scope: TestScope, carpeta: File, valorEn: (Long) -> Double = GuionBenelli::pct) {
    val epoch = 1_758_848_000_000L
    private val virtual = scope.testScheduler
    val reloj: () -> Long = { epoch + virtual.currentTime }
    val captura = CapturaFalsa({ virtual.currentTime }, valorEn)
    val enlace = EnlaceFalso()
    val voz = AnunciadorFalso()
    val repositorio = TallerRepositoryEnMemoria()
    var vehiculo: VehiculoTaller? = BENELLI
    val registro = RegistroTaller(
        repositorio, { vehiculo }, HistorialChequeosEnMemoria(), AdjuntosTaller(File(carpeta, "taller")),
        PidRegistry(TestPids.load()), reloj,
    )
    val controlador = ControladorPruebaGuiada(
        captura, enlace, registro, repositorio, { vehiculo }, voz, scope.backgroundScope, reloj,
    )

    suspend fun abrirSesion() = registro.abrirSesion(SolicitudSesion()).getOrThrow()

    // Cada sostenido: «Listo», 5 s sosteniendo y 1 s para posicionar el siguiente.
    suspend fun sostenerLosCuatroPasos(test: TestScope) = repeat(4) {
        controlador.avanzar().getOrThrow()
        test.advanceTimeBy(6_000)
    }
}

// Los valores del caso Benelli en el tiempo que marcan los pasos del montaje (0-5 s cerrado, 6-11 s medio…).
object GuionBenelli {
    fun pct(tMs: Long): Double = BarridoSintetico.pct(byteEn(tMs))

    private fun byteEn(tMs: Long): Int = when {
        tMs < 5_500 -> 6
        tMs < 11_500 -> if ((tMs / 100) % 2 == 0L) 23 else 24
        tMs < 17_500 -> if ((tMs / 100) % 2 == 0L) 45 else 46
        tMs < 23_000 -> 7
        else -> barrido(tMs - 23_000)
    }

    private fun barrido(msDesdeInicio: Long): Int {
        val sweep = BarridoSintetico.subidaYBajada(6, 45)
        return sweep[(msDesdeInicio * sweep.size / 8_000).toInt().coerceIn(0, sweep.lastIndex)]
    }
}
