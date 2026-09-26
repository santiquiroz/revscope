package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// Arranque en frío (§2.4.4 del diseño del Taller): plausibilidad de las temperaturas en reposo, intentos y
// tiempo de arranque, perfil del mínimo rápido, apagones y una ECT que sube sin saltos. Umbrales típicos.
object AnalizadorArranqueFrio {

    const val PID_ECT = "05"
    const val PID_IAT = "0F"
    const val PID_RPM = "0C"
    const val PID_TPS = "11"
    const val PID_AMBIENTE = "46"
    const val APAGADO_MAX_RPM = 1.0

    // El motor de arranque gira por debajo de esto; un motor que prendió, por encima (típico).
    const val ARRANCADO_MIN_RPM = 600.0
    const val ARRANQUE_SOSTENIDO_MS = 2_000L
    const val CALENTADO_C = 60.0
    const val SALTO_ECT_C = 5.0
    const val SALTO_ECT_MAX_MS = 1_000L
    const val CAIDA_ECT_TOLERADA_C = 2.0
    const val PERFIL_VENTANA_MS = 10_000L
    const val PERFIL_SUBIDA_MIN_C = 5.0
    const val PERFIL_TOLERANCIA_RPM = 100.0
    const val DIFERENCIA_FRIO_TIPICA_C = 5.0
    const val CLAVE_ARRANCO = "ARRANQUE_ARRANCO"
    const val CLAVE_PRIMER_INTENTO = "ARRANQUE_PRIMER_INTENTO"
    const val CLAVE_SIN_APAGONES = "ARRANQUE_SIN_APAGONES"
    const val CLAVE_ECT_SIN_SALTOS = "ARRANQUE_ECT_SIN_SALTOS"
    const val CLAVE_PERFIL = "ARRANQUE_MINIMO_RAPIDO_BAJA"
    const val CLAVE_SENSORES = "ARRANQUE_SENSORES_COHERENTES"

    object Pasos {
        const val CONTACTO = "CONTACTO"
        const val ARRANQUE = "ARRANQUE"
        const val CALENTAMIENTO = "CALENTAMIENTO"
        val TRAS_CONTACTO = listOf(ARRANQUE, CALENTAMIENTO)
        val TODOS = listOf(CONTACTO) + TRAS_CONTACTO
    }

    // Criterio del paso «Arranca ahora»: el último tramo girando supera el umbral y ya dura 2 s.
    fun arrancoSostenido(muestrasDelPaso: List<MuestraCaptura>): Boolean {
        val rpm = muestrasDelPaso.filter { it.pid == PID_RPM }.map { Punto(it.tMicros / 1_000, it.valor) }.sortedBy { it.tMs }
        val ultimo = SerieNumerica.tramos(rpm) { girando(it.valor) }.lastOrNull()?.takeIf { it.last == rpm.lastIndex } ?: return false
        return esArranque(rpm, ultimo)
    }

    // Arrancar se decide con que el motor prenda (umbral y 2 s), no con que el paso haya terminado solo.
    fun esArranque(rpm: List<Punto>, tramo: IntRange): Boolean =
        SerieNumerica.duracionMs(rpm, tramo) >= ARRANQUE_SOSTENIDO_MS && tramo.any { rpm[it].valor >= ARRANCADO_MIN_RPM }

    fun analizar(datos: DatosPrueba, bandas: Map<String, BandaReferencia>): ResultadoPrueba {
        val rpm = datos.serie(PID_RPM, Pasos.TRAS_CONTACTO)
        val ect = datos.serie(PID_ECT, Pasos.TODOS)
        val arranque = arranque(datos, rpm)
        val base = AnalisisArranqueFrio(
            plausibilidad = plausibilidad(datos, bandas),
            arranque = arranque,
            apagones = apagones(rpm, arranque),
            perfil = perfil(datos, arranque),
            tiempoHasta60S = tiempoHasta60(datos, arranque),
            ectMaxC = ect.maxOfOrNull { it.valor },
            saltosEct = DetectorEscalones.detectar(ect, SALTO_ECT_C, SALTO_ECT_MAX_MS),
            caidaEctMaxC = caidaMaxima(ect),
            comprobaciones = emptyList(),
            patron = PatronArranque.NORMAL,
        )
        val comprobado = base.copy(comprobaciones = comprobaciones(base))
        return TextosArranqueFrio.resultado(comprobado.copy(patron = patron(comprobado, rpm)))
    }

    // ── Contacto ────────────────────────────────────────────────────────────

    private fun plausibilidad(datos: DatosPrueba, bandas: Map<String, BandaReferencia>): PlausibilidadFrio {
        val banda = bandas[ClavesBanda.MOTOR_FRIO_ECT_IAT_DELTA_C]
        val limite = banda?.max ?: DIFERENCIA_FRIO_TIPICA_C
        val ect = mediaEnContacto(datos, PID_ECT)
        val iat = mediaEnContacto(datos, PID_IAT)
        return PlausibilidadFrio(ect, iat, mediaEnContacto(datos, PID_AMBIENTE), limite, banda, condicion(ect, iat, limite))
    }

    // Frío si el motor y el aire difieren menos que el límite: 32 °C contra 27 °C ya es tibio.
    private fun condicion(ect: Double?, iat: Double?, limite: Double): CondicionMotor = when {
        ect == null || iat == null -> CondicionMotor.SIN_DATO
        ect - iat < limite -> CondicionMotor.FRIO
        else -> CondicionMotor.TIBIO
    }

    private fun mediaEnContacto(datos: DatosPrueba, pid: String): Double? =
        datos.serie(pid, Pasos.CONTACTO).map { it.valor }.takeIf { it.isNotEmpty() }?.average()

    // ── Arranque y después ──────────────────────────────────────────────────

    private fun arranque(datos: DatosPrueba, rpm: List<Punto>): ArranqueMedido {
        val tramos = SerieNumerica.tramos(rpm) { girando(it.valor) }
        val indice = tramos.indexOfFirst { esArranque(rpm, it) }
        if (indice < 0) return ArranqueMedido(arranco = false, intentos = tramos.size, tiempoS = null, tInicioMs = null)
        val tInicio = rpm[tramos[indice].first].tMs
        val desde = datos.segmento(Pasos.ARRANQUE)?.inicioMs ?: rpm.first().tMs
        return ArranqueMedido(arranco = true, intentos = indice + 1, tiempoS = (tInicio - desde) / 1_000.0, tInicioMs = tInicio)
    }

    private fun apagones(rpm: List<Punto>, arranque: ArranqueMedido): Int {
        val desde = arranque.tInicioMs ?: return 0
        return SerieNumerica.caidas(rpm.filter { it.tMs >= desde }) { girando(it.valor) }
    }

    private fun perfil(datos: DatosPrueba, arranque: ArranqueMedido): PerfilMinimoRapido? {
        val desde = arranque.tInicioMs ?: return null
        val rpm = datos.serie(PID_RPM, Pasos.CALENTAMIENTO).filter { it.tMs >= desde && girando(it.valor) }
        val ect = datos.serie(PID_ECT, Pasos.CALENTAMIENTO).filter { it.tMs >= desde }
        if (rpm.size < 2 || ect.isEmpty()) return null
        val inicio = rpm.first().tMs
        val fin = rpm.last().tMs
        if (fin - inicio < 2 * PERFIL_VENTANA_MS) return null
        val rpmInicio = mediaEntre(rpm, inicio, inicio + PERFIL_VENTANA_MS) ?: return null
        val rpmFin = mediaEntre(rpm, fin - PERFIL_VENTANA_MS, fin) ?: return null
        val ectInicio = mediaEntre(ect, inicio, inicio + PERFIL_VENTANA_MS) ?: return null
        val ectFin = mediaEntre(ect, fin - PERFIL_VENTANA_MS, fin) ?: return null
        val baja = if (ectFin - ectInicio >= PERFIL_SUBIDA_MIN_C) rpmFin <= rpmInicio + PERFIL_TOLERANCIA_RPM else null
        return PerfilMinimoRapido(rpmInicio, ectInicio, rpmFin, ectFin, baja)
    }

    private fun mediaEntre(serie: List<Punto>, desdeMs: Long, hastaMs: Long): Double? =
        SerieNumerica.ventana(serie, desdeMs, hastaMs).map { it.valor }.takeIf { it.isNotEmpty() }?.average()

    // Solo si arrancó por debajo de 60 °C: con el motor ya caliente no hay calentamiento que medir.
    private fun tiempoHasta60(datos: DatosPrueba, arranque: ArranqueMedido): Double? {
        val desde = arranque.tInicioMs ?: return null
        if ((mediaEnContacto(datos, PID_ECT) ?: return null) >= CALENTADO_C) return null
        val llego = datos.serie(PID_ECT, Pasos.TRAS_CONTACTO).firstOrNull { it.tMs >= desde && it.valor >= CALENTADO_C } ?: return null
        return (llego.tMs - desde) / 1_000.0
    }

    private fun caidaMaxima(ect: List<Punto>): Double {
        var maximo = Double.NEGATIVE_INFINITY
        return ect.maxOfOrNull { p ->
            maximo = maxOf(maximo, p.valor)
            maximo - p.valor
        } ?: 0.0
    }

    // ── Comprobaciones y patrón ─────────────────────────────────────────────

    private fun comprobaciones(a: AnalisisArranqueFrio): List<Comprobacion> = listOfNotNull(
        Comprobacion.siNo(CLAVE_ARRANCO, "Arrancó", a.arranque.arranco),
        Comprobacion.siNo(CLAVE_PRIMER_INTENTO, "Al primer intento", a.arranque.arranco && a.arranque.intentos == 1),
        Comprobacion.siNo(CLAVE_SIN_APAGONES, "Sin apagones después de arrancar", a.apagones == 0),
        Comprobacion.siNo(CLAVE_ECT_SIN_SALTOS, "Temperatura del motor sin saltos", a.saltosEct.isEmpty()),
        a.perfil?.baja?.let { Comprobacion.siNo(CLAVE_PERFIL, "El mínimo rápido baja al calentar", it) },
        a.plausibilidad.takeIf { it.condicion != CondicionMotor.SIN_DATO }
            ?.let { Comprobacion.siNo(CLAVE_SENSORES, "Temperaturas coherentes en reposo", it.coherente) },
    )

    private fun patron(a: AnalisisArranqueFrio, rpm: List<Punto>): PatronArranque = when {
        rpm.isEmpty() -> PatronArranque.SIN_DATOS
        !a.arranque.arranco -> PatronArranque.NO_ARRANCO
        a.apagones > 0 -> PatronArranque.SE_APAGA
        a.saltosEct.isNotEmpty() -> PatronArranque.SALTOS_ECT
        a.arranque.intentos > 1 -> PatronArranque.VARIOS_INTENTOS
        irregular(a) -> PatronArranque.IRREGULAR
        else -> PatronArranque.NORMAL
    }

    private fun irregular(a: AnalisisArranqueFrio): Boolean =
        a.perfil?.baja == false || a.ectBajaMientrasCalienta || !a.plausibilidad.coherente

    private fun girando(rpm: Double): Boolean = rpm >= APAGADO_MAX_RPM
}
