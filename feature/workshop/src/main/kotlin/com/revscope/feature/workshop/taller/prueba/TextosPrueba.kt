package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ModoPaso
import com.revscope.core.obd.taller.pruebas.PasoPrueba
import com.revscope.core.obd.taller.pruebas.PatronTps
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.feature.workshop.R

internal object TextosPrueba {

    val ACCION_LISTO = R.string.workshop_prueba_accion_listo
    val ACCION_TERMINAR = R.string.workshop_prueba_accion_terminar
    val CANCELAR = R.string.workshop_prueba_cancelar

    fun descripcion(tipo: TipoPrueba, resolver: StringResolver): String = resolver.get(
        when (tipo) {
            TipoPrueba.TPS_BARRIDO -> R.string.workshop_prueba_descripcion_tps_barrido
            TipoPrueba.MINIMO_RETORNO -> R.string.workshop_prueba_descripcion_minimo_retorno
            TipoPrueba.ARRANQUE_FRIO -> R.string.workshop_prueba_descripcion_arranque_frio
            TipoPrueba.BATERIA_CARGA -> R.string.workshop_prueba_descripcion_bateria_carga
            TipoPrueba.MAP_BARO -> R.string.workshop_prueba_descripcion_map_baro
        },
    )

    fun paso(p: PasoPrueba, resolver: StringResolver): String =
        "${p.titulo}: ${modo(p.modo, conMeta = p.terminarCuando != null, resolver)}"

    private fun modo(m: ModoPaso, conMeta: Boolean, resolver: StringResolver): String = when (m) {
        is ModoPaso.Sostener -> resolver.get(R.string.workshop_prueba_modo_sostener, segundos(m.ms, resolver))
        is ModoPaso.Grabar -> resolver.get(R.string.workshop_prueba_modo_grabar, segundos(m.ms, resolver))
        is ModoPaso.GrabarHasta -> resolver.get(
            if (conMeta) R.string.workshop_prueba_modo_grabar_hasta_meta else R.string.workshop_prueba_modo_grabar_hasta,
            duracion(m.maxMs, resolver),
        )
        is ModoPaso.Accion -> resolver.get(R.string.workshop_prueba_modo_accion, duracion(m.maxMs, resolver))
    }

    // Espacio duro antes del «0.»: con letra grande no queda solo en el último renglón.
    fun subtexto(fase: FasePaso, modo: ModoPaso, resolver: StringResolver): String = resolver.get(
        when {
            fase == FasePaso.POSICIONANDO -> R.string.workshop_prueba_subtexto_posicionando
            fase == FasePaso.SOSTENIENDO -> R.string.workshop_prueba_subtexto_sosteniendo
            modo is ModoPaso.Accion -> R.string.workshop_prueba_subtexto_accion
            terminaConToque(modo) -> R.string.workshop_prueba_subtexto_toque
            else -> R.string.workshop_prueba_subtexto_automatico
        },
    )

    fun accionPrincipal(fase: FasePaso, modo: ModoPaso, resolver: StringResolver): String? = when {
        fase == FasePaso.POSICIONANDO -> resolver.get(ACCION_LISTO)
        fase == FasePaso.GRABANDO && terminaConToque(modo) -> resolver.get(ACCION_TERMINAR)
        else -> null
    }

    fun estadoFase(fase: FasePaso, resolver: StringResolver): String = resolver.get(
        when (fase) {
            FasePaso.POSICIONANDO -> R.string.workshop_prueba_fase_posicionando
            FasePaso.SOSTENIENDO -> R.string.workshop_prueba_fase_sosteniendo
            FasePaso.GRABANDO -> R.string.workshop_prueba_fase_grabando
        },
    )

    // El código SAE al que apunta cada patrón, para abrir su guía en el centro DTC.
    fun codigoGuia(patron: PatronTps): String? = when (patron) {
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> "P0122"
        PatronTps.SENAL_ALTA -> "P0123"
        PatronTps.CORTES_O_SALTOS -> "P0124"
        PatronTps.RANGO_DESEMPENO -> "P0121"
        PatronTps.SIN_BARRIDO, PatronTps.NORMAL -> null
    }

    fun hz(valor: Double, resolver: StringResolver): String =
        resolver.get(R.string.workshop_prueba_hz, LocaleNumbers.decimal(valor, 1, resolver.locale))

    private fun terminaConToque(modo: ModoPaso) = modo is ModoPaso.GrabarHasta || modo is ModoPaso.Accion

    private fun segundos(ms: Long, resolver: StringResolver): String = LocaleNumbers.compact(ms / 1_000.0, resolver.locale)

    // Los topes largos (el calentamiento) se leen mejor en minutos: «máx. 10 min», no «máx. 600 s».
    private fun duracion(ms: Long, resolver: StringResolver): String =
        if (ms >= MINUTOS_DESDE_MS) {
            resolver.get(R.string.workshop_prueba_duracion_min, LocaleNumbers.compact(ms / 60_000.0, resolver.locale))
        } else {
            resolver.get(R.string.workshop_prueba_duracion_s, segundos(ms, resolver))
        }

    private const val MINUTOS_DESDE_MS = 2 * 60_000L
}
