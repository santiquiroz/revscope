package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import kotlin.math.abs

// Mínimo y retorno con el motor encendido (§2.4.3 del diseño del Taller): estabilidad del mínimo sin tocar el
// acelerador, TPS de ralentí contra la banda de cerrado y cómo vuelve (o se apaga) en tres aceleradas.
object AnalizadorMinimo {

    const val PID_RPM = "0C"
    const val PID_TPS = "11"
    const val PID_ECT = "05"
    const val APAGADO_MAX_RPM = 1.0
    const val OSCILACION_AMPLITUD_RPM = 100.0
    const val OSCILACION_MIN_LOBULOS = 3
    const val RETORNO_TOLERANCIA = 0.10
    const val ACELERADA_MIN_RPM = 500.0
    const val CLAVE_SIN_OSCILACION = "MINIMO_SIN_OSCILACION"
    const val CLAVE_SIN_APAGONES = "MINIMO_SIN_APAGONES"

    object Pasos {
        const val MINIMO = "MINIMO"
        const val RETORNO_1 = "RETORNO_1"
        const val RETORNO_2 = "RETORNO_2"
        const val RETORNO_3 = "RETORNO_3"
        val RETORNOS = listOf(RETORNO_1, RETORNO_2, RETORNO_3)
    }

    fun analizar(datos: DatosPrueba, bandas: Map<String, BandaReferencia>): ResultadoPrueba {
        val minimo = estadistica(datos.serie(PID_RPM, Pasos.MINIMO), bandas)
        val referencia = minimo?.finRpm
        val base = AnalisisMinimo(
            minimo = minimo,
            referenciaRpm = referencia,
            ectMediaC = datos.serie(PID_ECT, Pasos.MINIMO).map { it.valor }.takeIf { it.isNotEmpty() }?.average(),
            tpsRalenti = tpsRalenti(datos, bandas),
            retornos = referencia?.let { ref -> Pasos.RETORNOS.mapNotNull { retorno(datos, it, ref) } }.orEmpty(),
            comprobaciones = emptyList(),
            patron = PatronMinimo.NORMAL,
        )
        val comprobado = base.copy(comprobaciones = comprobaciones(base, bandas))
        return TextosMinimo.resultado(comprobado.copy(patron = patron(comprobado)))
    }

    // ── Mínimo ──────────────────────────────────────────────────────────────

    private fun estadistica(serie: List<Punto>, bandas: Map<String, BandaReferencia>): EstadisticaMinimo? {
        val recta = SerieNumerica.recta(serie) ?: return null
        val valores = serie.map { it.valor }
        val desviacion = SerieNumerica.desviacion(valores)
        val oscilacion = oscilacion(serie, recta)
        return EstadisticaMinimo(
            n = serie.size,
            duracionS = (serie.last().tMs - serie.first().tMs) / 1_000.0,
            mediaRpm = valores.average(),
            desviacionRpm = desviacion,
            minRpm = valores.min(),
            maxRpm = valores.max(),
            derivaRpmS = recta.pendientePorS,
            inicioRpm = recta.en(serie.first().tMs),
            finRpm = recta.en(serie.last().tMs),
            oscilacion = oscilacion,
            apagones = SerieNumerica.caidas(serie) { girando(it.valor) },
            inestable = excede(bandas, ClavesBanda.MINIMO_DERIVA_MAX, abs(recta.pendientePorS)) ||
                excede(bandas, ClavesBanda.MINIMO_DESVIACION_MAX, desviacion) ||
                oscilacion != null,
        )
    }

    // Sobre lo que queda al quitar la deriva: cada lóbulo es un tramo del mismo lado de la recta.
    private fun oscilacion(serie: List<Punto>, recta: Recta): Oscilacion? {
        val residuos = serie.map { Punto(it.tMs, it.valor - recta.en(it.tMs)) }
        val lobulos = lobulos(residuos)
        val grandes = lobulos.filter { amplitud(residuos, it) > OSCILACION_AMPLITUD_RPM }
        if (grandes.size < OSCILACION_MIN_LOBULOS) return null
        // El primer y el último lóbulo pueden estar cortados por el inicio o el fin del paso.
        val duraciones = lobulos.zipWithNext().drop(1)
            .filter { (lobulo, _) -> lobulo in grandes }
            .map { (lobulo, siguiente) -> residuos[siguiente.first].tMs - residuos[lobulo.first].tMs }
        return Oscilacion(
            lobulos = grandes.size,
            amplitudRpm = grandes.map { amplitud(residuos, it) }.average(),
            periodoS = duraciones.takeIf { it.isNotEmpty() }?.let { 2 * it.average() / 1_000.0 },
        )
    }

    private fun lobulos(residuos: List<Punto>): List<IntRange> {
        val arriba = SerieNumerica.tramos(residuos) { it.valor >= 0 }
        val abajo = SerieNumerica.tramos(residuos) { it.valor < 0 }
        return (arriba + abajo).sortedBy { it.first }
    }

    private fun amplitud(residuos: List<Punto>, r: IntRange): Double = r.maxOf { abs(residuos[it].valor) }

    private fun tpsRalenti(datos: DatosPrueba, bandas: Map<String, BandaReferencia>): TpsRalenti? {
        val voltios = datos.serie(PID_TPS, Pasos.MINIMO).map { datos.vref.aVoltios(it.valor) }
        if (voltios.isEmpty()) return null
        val banda = bandas[ClavesBanda.TPS_CERRADO_V]
        val media = voltios.average()
        return TpsRalenti(voltios.min(), voltios.max(), media, banda, banda?.clasificar(media))
    }

    // ── Retornos ────────────────────────────────────────────────────────────

    private fun retorno(datos: DatosPrueba, clave: String, referencia: Double): RetornoMedido? {
        val serie = datos.serie(PID_RPM, clave)
        if (serie.isEmpty()) return null
        val iPico = serie.indices.maxBy { serie[it].valor }
        val pico = serie[iPico].valor
        if (pico - referencia < ACELERADA_MIN_RPM) return RetornoMedido(clave, false, pico, null, null, null, seApago = false)
        val despues = serie.subList(iPico + 1, serie.size)
        val valle = despues.minOfOrNull { it.valor }
        val seApago = valle != null && !girando(valle)
        return RetornoMedido(
            clave = clave,
            acelerada = true,
            picoRpm = pico,
            valleRpm = valle,
            valleFraccion = valle?.let { it / referencia },
            tiempoS = if (seApago) null else tiempoDeAsiento(serie[iPico], despues, referencia),
            seApago = seApago,
        )
    }

    // Desde que suelta (el pico) hasta que entra en ±10 % del mínimo y ya no sale; null si no se asienta.
    private fun tiempoDeAsiento(pico: Punto, despues: List<Punto>, referencia: Double): Double? {
        val dentro = { p: Punto -> abs(p.valor - referencia) <= RETORNO_TOLERANCIA * referencia }
        if (despues.isEmpty() || !dentro(despues.last())) return null
        val desde = despues.indexOfLast { !dentro(it) } + 1
        return (despues[desde].tMs - pico.tMs) / 1_000.0
    }

    // ── Comprobaciones y patrón ─────────────────────────────────────────────

    private fun comprobaciones(a: AnalisisMinimo, bandas: Map<String, BandaReferencia>): List<Comprobacion> {
        val m = a.minimo ?: return emptyList()
        return listOfNotNull(
            Comprobacion.contra(ClavesBanda.MINIMO_RPM, "Mínimo (media)", m.mediaRpm, "rpm", bandas),
            Comprobacion.contra(ClavesBanda.MINIMO_DERIVA_MAX, "Deriva del mínimo (sin signo)", abs(m.derivaRpmS), "rpm/s", bandas),
            Comprobacion.contra(ClavesBanda.MINIMO_DESVIACION_MAX, "Desviación del mínimo", m.desviacionRpm, "rpm", bandas),
            Comprobacion.siNo(CLAVE_SIN_OSCILACION, "Sin oscilación de más de ${OSCILACION_AMPLITUD_RPM.toInt()} rpm", m.oscilacion == null),
            Comprobacion.siNo(CLAVE_SIN_APAGONES, "No se apagó", m.apagones + a.apagonesAlSoltar == 0),
            valleMasBajo(a)?.let { Comprobacion.contra(ClavesBanda.RETORNO_VALLE_MIN, "Valle más bajo al soltar", it, "% del mínimo", bandas) },
            a.tpsRalenti?.let { Comprobacion.contra(ClavesBanda.TPS_CERRADO_V, "TPS en ralentí", it.mediaV, "V", bandas) },
        )
    }

    private fun valleMasBajo(a: AnalisisMinimo): Double? = a.retornosValidos.mapNotNull { it.valleFraccion }.minOrNull()?.times(100)

    private fun patron(a: AnalisisMinimo): PatronMinimo {
        val m = a.minimo ?: return PatronMinimo.SIN_DATOS
        return when {
            m.apagones + a.apagonesAlSoltar > 0 -> PatronMinimo.SE_APAGA
            m.inestable -> PatronMinimo.INESTABLE
            fuera(a, ClavesBanda.RETORNO_VALLE_MIN) -> PatronMinimo.RETORNO_BAJO
            fuera(a, ClavesBanda.MINIMO_RPM) -> PatronMinimo.FUERA_DE_BANDA
            fuera(a, ClavesBanda.TPS_CERRADO_V) -> PatronMinimo.TPS_RALENTI_FUERA
            a.retornosValidos.isEmpty() -> PatronMinimo.SIN_RETORNOS
            else -> PatronMinimo.NORMAL
        }
    }

    private fun fuera(a: AnalisisMinimo, clave: String): Boolean {
        val posicion = a.comprobacion(clave)?.posicion ?: return false
        return posicion != PosicionEnBanda.DENTRO
    }

    private fun excede(bandas: Map<String, BandaReferencia>, clave: String, valor: Double): Boolean =
        bandas[clave]?.clasificar(valor) == PosicionEnBanda.ALTO

    private fun girando(rpm: Double): Boolean = rpm >= APAGADO_MAX_RPM
}
