package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.ContextoPrueba
import com.revscope.core.obd.taller.pruebas.DatosPrueba
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ResultadoPrecondicion
import com.revscope.core.obd.taller.pruebas.SegmentoPaso
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// El mínimo y el arranque de la Benelli TNT 150i (25-sep-2026) como los capturaría la prueba guiada a 10 Hz:
// el mínimo cae de 2 010 a 1 454 rpm en 9 s con el TPS en 2,7-3,1 % y se apaga 2 de 3 veces al soltar; el
// arranque es tibio (motor 32 °C, aire 27 °C) y prende al segundo intento.
internal object PruebasMotorBenelli {

    private const val HZ = 10
    val bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())

    // ── Mínimo y retorno ────────────────────────────────────────────────────

    fun minimo(): DatosPrueba {
        val minimo = SegmentoPaso(AnalizadorMinimo.Pasos.MINIMO, 0, 9_000)
        val retornos = AnalizadorMinimo.Pasos.RETORNOS.mapIndexed { i, clave ->
            val desde = 9_500L + i * 8_500L
            SegmentoPaso(clave, desde, desde + 8_000)
        }
        val muestras = serie(minimo) { s -> 2_010.0 - 556.0 * s / 9.0 } +
            retornos.flatMapIndexed { i, seg -> serie(seg) { s -> retorno(s, seApaga = i != 1) } }
        val tps = (0..90).map { i -> muestra(i * 100L, AnalizadorMinimo.PID_TPS, if (i % 2 == 0) 2.75 else 3.14) }
        val ect = (0..90).map { i -> muestra(i * 100L, AnalizadorMinimo.PID_ECT, 32.0) }
        return DatosPrueba(TipoPrueba.MINIMO_RETORNO, ordenar(muestras + tps + ect), listOf(minimo) + retornos)
    }

    fun minimoTerminado(eventoId: Long? = 22): EstadoPrueba.Terminada {
        val datos = minimo()
        return EstadoPrueba.Terminada(AnalizadorMinimo.analizar(datos, bandas), eventoId, datos)
    }

    // Paso 1 de 4 grabando, a 20 s de terminar, con el mínimo en 1 454 rpm y la banda típica sombreada.
    fun enMinimo() = enPaso(TipoPrueba.MINIMO_RETORNO, 0, FasePaso.GRABANDO, 20_000)

    fun serieMinimo(hastaMs: Long = 25_000): SerieVivo = (0 until 100).map { i ->
        val t = hastaMs - (99 - i) * 100L
        t to 1_560.0 - i * 1.1 + if (i % 3 == 0) 12.0 else 0.0
    }

    private fun retorno(s: Double, seApaga: Boolean): Double = when {
        s < 0.5 -> 1_454.0
        s < 1.5 -> 1_454.0 + (3_000.0 - 1_454.0) * (s - 0.5)
        s < 2.2 -> 3_000.0 - (3_000.0 - if (seApaga) 900.0 else 1_250.0) * (s - 1.5) / 0.7
        seApaga && s < 2.6 -> 900.0 * (2.6 - s) / 0.4
        seApaga -> 0.0
        s < 3.0 -> 1_250.0 + 204.0 * (s - 2.2) / 0.8
        else -> 1_454.0
    }

    // ── Arranque en frío ────────────────────────────────────────────────────

    fun arranque(): DatosPrueba {
        val contacto = SegmentoPaso(AnalizadorArranqueFrio.Pasos.CONTACTO, 0, 10_000, 1_000)
        val arranque = SegmentoPaso(AnalizadorArranqueFrio.Pasos.ARRANQUE, 10_000, 15_600)
        val calentamiento = SegmentoPaso(AnalizadorArranqueFrio.Pasos.CALENTAMIENTO, 15_600, 195_600)
        val rpm = serie(contacto) { 0.0 } + serie(arranque) { s -> intentos(s) } + serie(calentamiento) { s -> 1_850.0 - 300.0 * s / 180.0 }
        val ect = serie(contacto, AnalizadorArranqueFrio.PID_ECT) { 32.0 } + serie(arranque, AnalizadorArranqueFrio.PID_ECT) { 32.0 } +
            serie(calentamiento, AnalizadorArranqueFrio.PID_ECT) { s -> 32.0 + 30.0 * s / 180.0 }
        val iat = listOf(contacto, arranque, calentamiento).flatMap { serie(it, AnalizadorArranqueFrio.PID_IAT) { 27.0 } }
        return DatosPrueba(TipoPrueba.ARRANQUE_FRIO, ordenar(rpm + ect + iat), listOf(contacto, arranque, calentamiento))
    }

    fun arranqueTerminado(eventoId: Long? = 23): EstadoPrueba.Terminada {
        val datos = arranque()
        return EstadoPrueba.Terminada(AnalizadorArranqueFrio.analizar(datos, bandas), eventoId, datos)
    }

    // Calentando: paso 3 de 3, graba hasta 60 °C o «Terminar»; la temperatura va en 41 °C.
    fun enCalentamiento() = enPaso(TipoPrueba.ARRANQUE_FRIO, 2, FasePaso.GRABANDO, 480_000)

    fun serieCalentamiento(hastaMs: Long = 140_000): SerieVivo = (0 until 100).map { i ->
        val t = hastaMs - (99 - i) * 100L
        t to 40.0 + if (i > 60) 1.0 else 0.0
    }

    // Lo que evalúan de verdad las precondiciones con el contacto puesto: motor 32 °C, aire 27 °C y 0 rpm.
    val precondicionesTibio: List<ResultadoPrecondicion> = run {
        val ahora = 1_758_848_000_000L
        val lecturas = listOf("05" to 32.0, "0F" to 27.0, "0C" to 0.0, "0D" to 0.0).associate { (pid, v) -> pid to ObdReading(pid, v, "", ahora) }
        val ctx = ContextoPrueba(conectado = true, lecturas = lecturas, soportado = { true }, ahoraMs = ahora)
        CatalogoPruebas.arranqueFrio.precondiciones.map { it.evaluar(ctx) }
    }

    private fun intentos(s: Double): Double = when {
        s < 1.0 -> 0.0
        s < 1.8 -> 700.0
        s < 3.4 -> 0.0
        else -> 1_850.0
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private fun enPaso(tipo: TipoPrueba, indice: Int, fase: FasePaso, restanteMs: Long?): EstadoPrueba.EnPaso {
        val pasos = checkNotNull(CatalogoPruebas.definicion(tipo)).pasos
        return EstadoPrueba.EnPaso(tipo, pasos[indice], indice, pasos.size, fase, restanteMs)
    }

    private fun serie(seg: SegmentoPaso, pid: String = AnalizadorMinimo.PID_RPM, valor: (Double) -> Double): List<MuestraCaptura> =
        (0..((seg.finMs - seg.inicioMs) * HZ / 1_000).toInt()).map { i ->
            muestra(seg.inicioMs + i * 1_000L / HZ, pid, valor(i.toDouble() / HZ))
        }

    private fun muestra(tMs: Long, pid: String, valor: Double) =
        MuestraCaptura(seq = 0, tMicros = tMs * 1_000, pid = pid, valor = valor, lote = tMs, latenciaMs = 60)

    private fun ordenar(muestras: List<MuestraCaptura>) = muestras.sortedBy { it.tMicros }.mapIndexed { i, m -> m.copy(seq = i.toLong()) }
}
