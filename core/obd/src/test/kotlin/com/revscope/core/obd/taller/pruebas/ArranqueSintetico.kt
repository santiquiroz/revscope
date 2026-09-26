package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio.Pasos
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// Arranque en frío armado a mano a 10 Hz: contacto 10 s, arranque (6 s) y calentamiento (120 s por defecto).
// Cada serie es una función del tiempo del paso en segundos.
object ArranqueSintetico {

    const val HZ = 10
    const val CONTACTO_MS = 10_000L
    const val ARRANQUE_MS = 6_000L
    const val CALENTAMIENTO_MS = 120_000L

    data class Perfil(
        val ectContacto: Double? = 24.0,
        val iatContacto: Double? = 23.0,
        val ambiente: Double? = null,
        val rpmArranque: (Double) -> Double = unIntento(),
        val rpmCalentamiento: (Double) -> Double = minimoRapido(),
        val ectCalentamiento: (Double) -> Double = calentar(desde = 24.0),
        val arranqueMs: Long = ARRANQUE_MS,
        val calentamientoMs: Long = CALENTAMIENTO_MS,
    )

    fun datos(p: Perfil = Perfil()): DatosPrueba {
        val muestras = mutableListOf<MuestraCaptura>()
        val contacto = SegmentoPaso(Pasos.CONTACTO, 0, CONTACTO_MS, 1_000)
        val arranque = SegmentoPaso(Pasos.ARRANQUE, CONTACTO_MS, CONTACTO_MS + p.arranqueMs, 0)
        val calentamiento = SegmentoPaso(Pasos.CALENTAMIENTO, arranque.finMs, arranque.finMs + p.calentamientoMs, 0)
        muestras += paso(contacto, rpm = { 0.0 }, ect = { p.ectContacto }, iat = { p.iatContacto }, ambiente = p.ambiente)
        muestras += paso(arranque, rpm = p.rpmArranque, ect = { p.ectContacto }, iat = { p.iatContacto }, ambiente = p.ambiente)
        muestras += paso(calentamiento, rpm = p.rpmCalentamiento, ect = p.ectCalentamiento, iat = { p.iatContacto }, ambiente = p.ambiente)
        val ordenadas = muestras.sortedBy { it.tMicros }.mapIndexed { i, m -> m.copy(seq = i.toLong()) }
        return DatosPrueba(TipoPrueba.ARRANQUE_FRIO, ordenadas, listOf(contacto, arranque, calentamiento))
    }

    // Enciende a los 1,0 s y se queda en 1 800 rpm.
    fun unIntento(): (Double) -> Double = { s -> if (s < 1.0) 0.0 else 1_800.0 }

    // Prende a 1,0 s, se apaga a 2,0 s y vuelve a prender a los 3,5 s para quedarse.
    fun dosIntentos(): (Double) -> Double = { s ->
        when {
            s < 1.0 -> 0.0
            s < 2.0 -> 800.0
            s < 3.5 -> 0.0
            else -> 1_800.0
        }
    }

    // Solo el motor de arranque: 300 rpm y nunca prende.
    fun soloMotorDeArranque(): (Double) -> Double = { s -> if (s in 1.0..5.0) 300.0 else 0.0 }

    fun minimoRapido(desde: Double = 1_800.0, hasta: Double = 1_500.0): (Double) -> Double =
        { s -> desde + (hasta - desde) * s / (CALENTAMIENTO_MS / 1_000.0) }

    fun seApagaA(s0: Double, base: (Double) -> Double = minimoRapido()): (Double) -> Double =
        { s -> if (s in s0..s0 + 3.0) 0.0 else base(s) }

    fun calentar(desde: Double, hasta: Double = 62.0): (Double) -> Double =
        { s -> desde + (hasta - desde) * s / (CALENTAMIENTO_MS / 1_000.0) }

    // Sube 8 °C repartidos en 0,5 s a los 40 s y se queda arriba.
    fun conSalto(base: (Double) -> Double, enS: Double = 40.0, saltoC: Double = 8.0): (Double) -> Double = { s ->
        base(s) + saltoC * ((s - enS) / 0.5).coerceIn(0.0, 1.0)
    }

    private fun paso(
        seg: SegmentoPaso,
        rpm: (Double) -> Double,
        ect: (Double) -> Double?,
        iat: (Double) -> Double?,
        ambiente: Double?,
    ): List<MuestraCaptura> {
        val n = ((seg.finMs - seg.inicioMs) * HZ / 1_000).toInt()
        return (0 until n).flatMap { i ->
            val s = i.toDouble() / HZ
            val tMs = seg.inicioMs + i * 1_000L / HZ
            listOfNotNull(
                muestra(tMs, AnalizadorArranqueFrio.PID_RPM, rpm(s)),
                ect(s)?.let { muestra(tMs, AnalizadorArranqueFrio.PID_ECT, it) },
                iat(s)?.let { muestra(tMs, AnalizadorArranqueFrio.PID_IAT, it) },
                ambiente?.let { muestra(tMs, AnalizadorArranqueFrio.PID_AMBIENTE, it) },
            )
        }
    }

    private fun muestra(tMs: Long, pid: String, valor: Double) =
        MuestraCaptura(seq = 0, tMicros = tMs * 1_000, pid = pid, valor = valor, lote = tMs, latenciaMs = 60)
}
