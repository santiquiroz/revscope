package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import kotlin.math.abs

// Barrido del TPS con motor apagado (§2.4.2 del diseño del Taller): estadística por paso, bandas, orden,
// linealidad, saltos, zonas muertas y cortes, y el primer patrón que coincide.
object AnalizadorBarridoTps {

    const val PID_TPS = "11"
    const val TASA_CONFIABLE_HZ = 8.0
    const val FRACCION_RECORRIDO_BAJA = 0.5
    const val CLAVE_ORDEN = "TPS_ORDEN"
    const val CLAVE_SIN_ZONAS_MUERTAS = "TPS_SIN_ZONAS_MUERTAS"

    // Con menos muestras del barrido lento no hay recorrido que revisar: saltos y zonas muertas quedarían sin buscar.
    const val MIN_MUESTRAS_BARRIDO = 10

    object Pasos {
        const val CERRADO_1 = "CERRADO_1"
        const val MEDIO = "MEDIO"
        const val A_FONDO = "A_FONDO"
        const val CERRADO_2 = "CERRADO_2"
        const val BARRIDO_LENTO = "BARRIDO_LENTO"
        val SOSTENIDOS = listOf(CERRADO_1, MEDIO, A_FONDO, CERRADO_2)
        val TODOS = SOSTENIDOS + BARRIDO_LENTO
    }

    fun analizar(datos: DatosPrueba, bandas: Map<String, BandaReferencia>): ResultadoPrueba {
        val estadisticas = Pasos.TODOS.mapNotNull { estadistica(datos, it) }
        val faltan = Pasos.SOSTENIDOS.filter { clave -> estadisticas.none { it.clave == clave } }
        if (faltan.isNotEmpty()) return TextosBarridoTps.sinDatos(faltan)
        return TextosBarridoTps.resultado(analisis(datos, bandas, estadisticas))
    }

    fun recorridoTipicoV(bandas: Map<String, BandaReferencia>): Double? {
        val cerrado = bandas[ClavesBanda.TPS_CERRADO_V]?.centro()
        val fondo = bandas[ClavesBanda.TPS_FONDO_V]?.centro()
        if (cerrado != null && fondo != null) return fondo - cerrado
        return bandas[ClavesBanda.TPS_RECORRIDO_MIN_V]?.min
    }

    private fun analisis(datos: DatosPrueba, bandas: Map<String, BandaReferencia>, pasos: List<EstadisticaPaso>): AnalisisBarridoTps {
        fun media(clave: String) = pasos.first { it.clave == clave }.mediaV
        val cerrado = media(Pasos.CERRADO_1)
        val fondo = media(Pasos.A_FONDO)
        val barrido = serieV(datos, Pasos.BARRIDO_LENTO)
        val base = AnalisisBarridoTps(
            vref = datos.vref,
            pasos = pasos,
            cerradoV = cerrado,
            medioV = media(Pasos.MEDIO),
            fondoV = fondo,
            recorridoTipicoV = recorridoTipicoV(bandas),
            repetibilidadV = abs(media(Pasos.CERRADO_2) - cerrado),
            ruidoMaxV = pasos.filter { it.clave in Pasos.SOSTENIDOS }.maxOf { it.ppV },
            comprobaciones = emptyList(),
            irregularidades = irregularidades(datos, barrido, cerrado, fondo),
            tasaHz = tasaHz(datos),
            patron = PatronTps.NORMAL,
            barridoEvaluado = barrido.size >= MIN_MUESTRAS_BARRIDO,
        )
        val comprobado = base.copy(comprobaciones = comprobaciones(base, bandas))
        return comprobado.copy(patron = patron(comprobado))
    }

    private fun estadistica(datos: DatosPrueba, clave: String): EstadisticaPaso? {
        val segmento = datos.segmento(clave) ?: return null
        return EstadisticaPaso.de(clave, datos.serie(PID_TPS, clave), segmento.utilMs, datos.vref)
    }

    private fun serieV(datos: DatosPrueba, clave: String): List<Punto> =
        datos.serie(PID_TPS, clave).map { it.copy(valor = datos.vref.aVoltios(it.valor)) }

    // Los cortes se buscan fuera de los pasos en cerrado; saltos y zonas muertas, en el barrido lento.
    private fun irregularidades(datos: DatosPrueba, barrido: List<Punto>, cerradoV: Double, fondoV: Double): List<Irregularidad> {
        val umbral = DetectorSenal.umbralCorte(cerradoV)
        val cortes = listOf(Pasos.MEDIO, Pasos.A_FONDO, Pasos.BARRIDO_LENTO)
            .flatMap { DetectorSenal.cortes(serieV(datos, it), umbral) }
        return (cortes + DetectorSenal.saltos(barrido) + DetectorSenal.zonasMuertas(barrido, cerradoV, fondoV))
            .sortedBy { it.tMs }
    }

    private fun tasaHz(datos: DatosPrueba): Double {
        val segmentos = datos.segmentos.filter { it.clave in Pasos.TODOS }
        val ms = segmentos.sumOf { it.utilMs }
        if (ms <= 0) return 0.0
        return segmentos.sumOf { datos.serie(PID_TPS, it.clave).size } * 1_000.0 / ms
    }

    private fun comprobaciones(a: AnalisisBarridoTps, bandas: Map<String, BandaReferencia>): List<Comprobacion> = listOfNotNull(
        Comprobacion.contra(ClavesBanda.TPS_CERRADO_V, "Cerrado", a.cerradoV, "V", bandas),
        Comprobacion.contra(ClavesBanda.TPS_FONDO_V, "A fondo", a.fondoV, "V", bandas),
        Comprobacion.contra(ClavesBanda.TPS_RECORRIDO_MIN_V, "Recorrido", a.recorridoV, "V", bandas),
        Comprobacion.contra(ClavesBanda.TPS_RUIDO_MAX_V, "Ruido al sostener (pico a pico)", a.ruidoMaxV, "V", bandas),
        Comprobacion.contra(ClavesBanda.TPS_REPETIBILIDAD_V, "Repetibilidad del cerrado", a.repetibilidadV, "V", bandas),
        Comprobacion.contra(ClavesBanda.TPS_LINEALIDAD, "Linealidad (medio)", a.linealidad, "", bandas),
        Comprobacion.siNo(CLAVE_ORDEN, "Orden cerrado < medio < fondo", a.ordenCorrecto),
        Comprobacion.siNo(CLAVE_SIN_ZONAS_MUERTAS, "Sin zonas muertas", a.cuenta(TipoIrregularidad.ZONA_MUERTA) == 0)
            .takeIf { a.barridoEvaluado },
    )

    private fun patron(a: AnalisisBarridoTps): PatronTps = when {
        a.cuenta(TipoIrregularidad.CORTE) + a.cuenta(TipoIrregularidad.SALTO) > 0 -> PatronTps.CORTES_O_SALTOS
        esSenalBaja(a) -> PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO
        posicion(a, ClavesBanda.TPS_CERRADO_V) == PosicionEnBanda.ALTO -> PatronTps.SENAL_ALTA
        a.comprobaciones.any { !it.cumple } -> PatronTps.RANGO_DESEMPENO
        !a.barridoEvaluado -> PatronTps.SIN_BARRIDO
        else -> PatronTps.NORMAL
    }

    // El patrón del P0122 de la Benelli: baja en ambos extremos, recorrido corto, pero ordenada y estable.
    private fun esSenalBaja(a: AnalisisBarridoTps): Boolean {
        val fraccion = a.fraccionRecorrido ?: return false
        return posicion(a, ClavesBanda.TPS_CERRADO_V) != PosicionEnBanda.ALTO &&
            posicion(a, ClavesBanda.TPS_FONDO_V) == PosicionEnBanda.BAJO &&
            fraccion < FRACCION_RECORRIDO_BAJA &&
            a.ordenCorrecto &&
            cumple(a, ClavesBanda.TPS_RUIDO_MAX_V) &&
            cumple(a, ClavesBanda.TPS_REPETIBILIDAD_V)
    }

    private fun posicion(a: AnalisisBarridoTps, clave: String): PosicionEnBanda? = a.comprobacion(clave)?.posicion

    private fun cumple(a: AnalisisBarridoTps, clave: String): Boolean = a.comprobacion(clave)?.cumple ?: true

    private fun BandaReferencia.centro(): Double? {
        val desde = min ?: return null
        val hasta = max ?: return null
        return (desde + hasta) / 2
    }
}
