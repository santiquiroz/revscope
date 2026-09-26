package com.revscope.core.obd.telemetry.captura

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Destino del CSV mientras la captura corre: [escribir] solo acumula en memoria; [volcar] escribe al
 * disco (se llama cada 1 s desde el hilo de IO) para que una captura más larga que el anillo no se pierda.
 */
interface SumideroCaptura {
    fun abrir(lineasIniciales: List<String>)
    fun escribir(lineas: List<String>)
    fun volcar()
    fun cerrar(): String?
}

/** CSV en cache/exports (la carpeta del FileProvider); conserva las últimas [conservar] capturas. */
class ArchivoCaptura(
    private val dir: File,
    private val epochMs: () -> Long = System::currentTimeMillis,
    private val conservar: Int = CAPTURAS_CONSERVADAS,
) : SumideroCaptura {

    private val pendientes = mutableListOf<String>()
    private var archivo: File? = null
    private var writer: BufferedWriter? = null

    override fun abrir(lineasIniciales: List<String>) {
        dir.mkdirs()
        podarViejas()
        val nuevo = File(dir, "$PREFIJO${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(epochMs()))}.csv")
        archivo = nuevo
        escribir(lineasIniciales)
    }

    @Synchronized
    override fun escribir(lineas: List<String>) {
        pendientes += lineas
    }

    override fun volcar() {
        val lote = synchronized(this) { pendientes.toList().also { pendientes.clear() } }
        if (lote.isEmpty()) return
        val salida = writer ?: archivo?.bufferedWriter()?.also { writer = it } ?: return
        runCatching {
            lote.forEach { salida.appendLine(it) }
            salida.flush()
        }.onFailure { Timber.e(it, "ArchivoCaptura: no se pudo escribir ${archivo?.name}") }
    }

    override fun cerrar(): String? {
        volcar()
        runCatching { writer?.close() }
        writer = null
        return archivo?.takeIf { it.exists() }?.absolutePath
    }

    private fun podarViejas() {
        val viejas = dir.listFiles { f -> f.name.startsWith(PREFIJO) && f.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        viejas.drop(conservar - 1).forEach { runCatching { it.delete() } }
    }

    companion object {
        const val PREFIJO = "revscope-captura-"
        private const val CAPTURAS_CONSERVADAS = 5
    }
}

data class LecturaDispositivo(val bateriaPct: Int?, val cargando: Boolean, val termico: Int?)

fun interface LectorDispositivo {
    fun leer(): LecturaDispositivo
}

/** Batería por el broadcast sticky y estado térmico de PowerManager (API 29+; null antes). */
class AndroidLectorDispositivo(private val context: Context) : LectorDispositivo {

    override fun leer(): LecturaDispositivo {
        val bateria = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        return LecturaDispositivo(
            bateriaPct = bateria?.let(::porcentaje),
            cargando = bateria?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } ?: false,
            termico = estadoTermico(),
        )
    }

    private fun porcentaje(intent: Intent): Int? {
        val nivel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val escala = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (nivel < 0 || escala <= 0) null else nivel * 100 / escala
    }

    private fun estadoTermico(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return power.currentThermalStatus
    }
}
