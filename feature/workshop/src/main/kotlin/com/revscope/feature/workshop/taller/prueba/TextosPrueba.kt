package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ModoPaso
import com.revscope.core.obd.taller.pruebas.PasoPrueba
import com.revscope.core.obd.taller.pruebas.PatronTps
import com.revscope.core.obd.taller.pruebas.TipoPrueba

internal object TextosPrueba {

    const val ACCION_LISTO = "Listo, lo estoy sosteniendo"
    const val ACCION_TERMINAR = "Terminar"
    const val CANCELAR = "Cancelar prueba"

    fun descripcion(tipo: TipoPrueba): String = when (tipo) {
        TipoPrueba.TPS_BARRIDO ->
            "Con el motor apagado y el contacto puesto, lleva el acelerador por cinco posiciones mientras la app lee " +
                "el TPS a la tasa máxima. Cada posición se compara con las bandas de referencia y el barrido lento " +
                "busca cortes, saltos y zonas muertas."
        TipoPrueba.MINIMO_RETORNO ->
            "Con el motor encendido y en neutro, graba 45 s de mínimo sin tocar el acelerador y luego 3 aceleradas " +
                "a unas 3 000 rpm soltando de golpe: mide si el mínimo se sostiene, cuánto cae al soltar y si se apaga."
        TipoPrueba.ARRANQUE_FRIO ->
            "Tras el reposo de la noche: compara la temperatura del motor con la del aire en contacto, cuenta los " +
                "intentos de arranque y sigue el calentamiento hasta 60 °C o hasta que toques «Terminar»."
        TipoPrueba.BATERIA_CARGA -> "Voltaje con el contacto puesto, la caída al arrancar y la carga con el motor encendido."
        TipoPrueba.MAP_BARO -> "Con el motor apagado, el MAP debe marcar lo mismo que la presión barométrica."
    }

    fun paso(p: PasoPrueba): String = "${p.titulo}: ${modo(p.modo, conMeta = p.terminarCuando != null)}"

    private fun modo(m: ModoPaso, conMeta: Boolean): String = when (m) {
        is ModoPaso.Sostener -> "sostener ${segundos(m.ms)} s"
        is ModoPaso.Grabar -> "se graba solo ${segundos(m.ms)} s"
        is ModoPaso.GrabarHasta -> "graba hasta ${if (conMeta) "cumplir la meta o hasta " else ""}que toques «Terminar» (máx. ${duracion(m.maxMs)})"
        is ModoPaso.Accion -> "graba hasta detectar el evento o hasta que toques «Terminar» (máx. ${duracion(m.maxMs)})"
    }

    // Espacio duro antes del «0.»: con letra grande no queda solo en el último renglón.
    fun subtexto(fase: FasePaso, modo: ModoPaso): String = when {
        fase == FasePaso.POSICIONANDO -> "Cuando esté en posición, toca «Listo»."
        fase == FasePaso.SOSTENIENDO -> "Sostenlo quieto hasta que la cuenta llegue a 0."
        modo is ModoPaso.Accion -> "Termina solo al detectarlo; si no, toca «Terminar»."
        terminaConToque(modo) -> "Toca «Terminar» cuando acabes."
        else -> "Se graba solo: la cuenta marca cuánto falta."
    }

    fun accionPrincipal(fase: FasePaso, modo: ModoPaso): String? = when {
        fase == FasePaso.POSICIONANDO -> ACCION_LISTO
        fase == FasePaso.GRABANDO && terminaConToque(modo) -> ACCION_TERMINAR
        else -> null
    }

    fun estadoFase(fase: FasePaso): String = when (fase) {
        FasePaso.POSICIONANDO -> "Posiciona el acelerador y toca Listo"
        FasePaso.SOSTENIENDO -> "Sosteniendo"
        FasePaso.GRABANDO -> "Grabando"
    }

    // El código SAE al que apunta cada patrón, para abrir su guía en el centro DTC.
    fun codigoGuia(patron: PatronTps): String? = when (patron) {
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> "P0122"
        PatronTps.SENAL_ALTA -> "P0123"
        PatronTps.CORTES_O_SALTOS -> "P0124"
        PatronTps.RANGO_DESEMPENO -> "P0121"
        PatronTps.NORMAL -> null
    }

    fun hz(valor: Double): String = "${FormatoTaller.numero(valor, 1)} Hz"

    private fun terminaConToque(modo: ModoPaso) = modo is ModoPaso.GrabarHasta || modo is ModoPaso.Accion

    private fun segundos(ms: Long): String = FormatoTaller.compacto(ms / 1_000.0)

    // Los topes largos (el calentamiento) se leen mejor en minutos: «máx. 10 min», no «máx. 600 s».
    private fun duracion(ms: Long): String =
        if (ms >= MINUTOS_DESDE_MS) "${FormatoTaller.compacto(ms / 60_000.0)} min" else "${segundos(ms)} s"

    private const val MINUTOS_DESDE_MS = 2 * 60_000L
}
