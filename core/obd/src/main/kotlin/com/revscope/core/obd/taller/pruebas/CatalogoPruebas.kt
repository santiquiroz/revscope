package com.revscope.core.obd.taller.pruebas

import androidx.annotation.StringRes
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio.Pasos as PasosArranque
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos as PasosBateria
import com.revscope.core.obd.taller.pruebas.AnalizadorMapBaro.Pasos as PasosMap
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo.Pasos as PasosMinimo
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// Las pruebas guiadas del Taller, cada una con su analizador.
object CatalogoPruebas {

    private const val SOSTENER_MS = 5_000L
    private const val BARRIDO_LENTO_MS = 8_000L
    private const val PID_TPS_RELATIVO = "45"
    private const val MINIMO_MS = 45_000L
    private const val RETORNO_MS = 8_000L
    private const val CONTACTO_MS = 10_000L
    private const val ARRANQUE_MAX_MS = 15_000L
    private const val CALENTAMIENTO_MAX_MS = 10 * 60_000L
    private const val RAFAGA_ARRANQUE_MS = 8_000L
    private const val CARGA_MINIMO_MS = 15_000L
    private const val RPM_ALTAS_MS = 10_000L

    val barridoTps = DefinicionPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        pids = listOf(AnalizadorBarridoTps.PID_TPS),
        pidsOpcionales = listOf(PID_TPS_RELATIVO),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado(R.string.obd_catalogo_precondicion_tps_motor_apagado_que_hacer),
            Precondicion.MotoDetenida,
            Precondicion.PidDisponible(
                AnalizadorBarridoTps.PID_TPS,
                "TPS",
                R.string.obd_catalogo_precondicion_tps_pid_no_disponible_que_hacer,
            ),
        ),
        pasos = listOf(
            sostener(Pasos.CERRADO_1, R.string.obd_catalogo_paso_tps_cerrado_1_instruccion),
            sostener(Pasos.MEDIO, R.string.obd_catalogo_paso_tps_medio_instruccion),
            sostener(Pasos.A_FONDO, R.string.obd_catalogo_paso_tps_a_fondo_instruccion),
            sostener(Pasos.CERRADO_2, R.string.obd_catalogo_paso_tps_cerrado_2_instruccion),
            PasoPrueba(
                clave = Pasos.BARRIDO_LENTO,
                tituloRes = TextosBarridoTps.TITULOS_PASO.getValue(Pasos.BARRIDO_LENTO),
                instruccionRes = R.string.obd_catalogo_paso_tps_barrido_lento_instruccion,
                modo = ModoPaso.Grabar(BARRIDO_LENTO_MS),
                descartarInicioMs = 0,
            ),
        ),
        analizar = AnalizadorBarridoTps::analizar,
    )

    val minimoRetorno = DefinicionPrueba(
        tipo = TipoPrueba.MINIMO_RETORNO,
        pids = listOf(AnalizadorMinimo.PID_RPM, AnalizadorMinimo.PID_TPS, AnalizadorMinimo.PID_ECT),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorEncendido(),
            Precondicion.MotoDetenida,
            Precondicion.EnNeutro,
            Precondicion.PidDisponible(AnalizadorMinimo.PID_RPM, "RPM", R.string.obd_catalogo_precondicion_minimo_rpm_no_disponible_que_hacer),
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosMinimo.MINIMO,
                tituloRes = TextosMinimo.TITULOS_PASO.getValue(PasosMinimo.MINIMO),
                instruccionRes = R.string.obd_catalogo_paso_minimo_instruccion,
                modo = ModoPaso.Grabar(MINIMO_MS),
            ),
        ) + PasosMinimo.RETORNOS.map(::retorno),
        analizar = AnalizadorMinimo::analizar,
    )

    val arranqueFrio = DefinicionPrueba(
        tipo = TipoPrueba.ARRANQUE_FRIO,
        pids = listOf(AnalizadorArranqueFrio.PID_RPM, AnalizadorArranqueFrio.PID_ECT, AnalizadorArranqueFrio.PID_IAT),
        pidsOpcionales = listOf(AnalizadorArranqueFrio.PID_TPS, AnalizadorArranqueFrio.PID_AMBIENTE),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado(R.string.obd_catalogo_precondicion_arranque_motor_apagado_que_hacer),
            Precondicion.MotoDetenida,
            Precondicion.MotorFrio,
            Precondicion.PidDisponible(
                AnalizadorArranqueFrio.PID_ECT,
                nombre = "ECT",
                alternativaRes = R.string.obd_catalogo_precondicion_ect_no_disponible_que_hacer,
                nombreRes = R.string.obd_catalogo_precondicion_ect_nombre,
            ),
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosArranque.CONTACTO,
                tituloRes = TextosArranqueFrio.TITULOS_PASO.getValue(PasosArranque.CONTACTO),
                instruccionRes = R.string.obd_catalogo_paso_arranque_contacto_instruccion,
                modo = ModoPaso.Grabar(CONTACTO_MS),
                pidPrincipal = AnalizadorArranqueFrio.PID_ECT,
            ),
            PasoPrueba(
                clave = PasosArranque.ARRANQUE,
                tituloRes = TextosArranqueFrio.TITULOS_PASO.getValue(PasosArranque.ARRANQUE),
                instruccionRes = R.string.obd_catalogo_paso_arranque_instruccion,
                modo = ModoPaso.Accion(ARRANQUE_MAX_MS),
                descartarInicioMs = 0,
                terminarCuando = CriterioFin(AnalizadorArranqueFrio::arrancoSostenido),
                pidPrincipal = AnalizadorArranqueFrio.PID_RPM,
            ),
            PasoPrueba(
                clave = PasosArranque.CALENTAMIENTO,
                tituloRes = TextosArranqueFrio.TITULOS_PASO.getValue(PasosArranque.CALENTAMIENTO),
                instruccionRes = R.string.obd_catalogo_paso_calentamiento_instruccion,
                modo = ModoPaso.GrabarHasta(CALENTAMIENTO_MAX_MS),
                descartarInicioMs = 0,
                terminarCuando = CriterioFin(::calentado),
                pidPrincipal = AnalizadorArranqueFrio.PID_ECT,
            ),
        ),
        analizar = AnalizadorArranqueFrio::analizar,
    )

    val bateriaCarga = DefinicionPrueba(
        tipo = TipoPrueba.BATERIA_CARGA,
        pids = listOf(AnalizadorBateria.PID_VOLTAJE),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado(R.string.obd_catalogo_precondicion_bateria_motor_apagado_que_hacer),
            Precondicion.MotoDetenida,
            Precondicion.LucesConContacto,
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosBateria.CONTACTO,
                tituloRes = TextosBateria.TITULOS_PASO.getValue(PasosBateria.CONTACTO),
                instruccionRes = R.string.obd_catalogo_paso_bateria_contacto_instruccion,
                modo = ModoPaso.Grabar(CONTACTO_MS),
            ),
            PasoPrueba(
                clave = PasosBateria.ARRANQUE,
                tituloRes = TextosBateria.TITULOS_PASO.getValue(PasosBateria.ARRANQUE),
                instruccionRes = R.string.obd_catalogo_paso_bateria_arranque_instruccion,
                modo = ModoPaso.Grabar(RAFAGA_ARRANQUE_MS),
                descartarInicioMs = 0,
            ),
            PasoPrueba(
                clave = PasosBateria.MINIMO,
                tituloRes = TextosBateria.TITULOS_PASO.getValue(PasosBateria.MINIMO),
                instruccionRes = R.string.obd_catalogo_paso_bateria_minimo_instruccion,
                modo = ModoPaso.Grabar(CARGA_MINIMO_MS),
            ),
            PasoPrueba(
                clave = PasosBateria.RPM_ALTAS,
                tituloRes = TextosBateria.TITULOS_PASO.getValue(PasosBateria.RPM_ALTAS),
                instruccionRes = R.string.obd_catalogo_paso_bateria_rpm_altas_instruccion,
                modo = ModoPaso.Sostener(RPM_ALTAS_MS),
            ),
        ),
        analizar = AnalizadorBateria::analizar,
        fuente = FuenteMuestras.VOLTAJE_ADAPTADOR,
        analizarSiSeCortaEn = setOf(PasosBateria.ARRANQUE),
    )

    val mapBaro = DefinicionPrueba(
        tipo = TipoPrueba.MAP_BARO,
        pids = listOf(AnalizadorMapBaro.PID_MAP),
        pidsOpcionales = listOf(AnalizadorMapBaro.PID_BARO),
        precondiciones = listOf(
            Precondicion.AdaptadorConectado,
            Precondicion.MotorApagado(R.string.obd_catalogo_precondicion_mapbaro_motor_apagado_que_hacer),
            Precondicion.MotoDetenida,
            Precondicion.PidDisponible(
                AnalizadorMapBaro.PID_MAP,
                "MAP",
                R.string.obd_catalogo_precondicion_mapbaro_pid_no_disponible_que_hacer,
            ),
            Precondicion.BarometricaDisponible,
        ),
        pasos = listOf(
            PasoPrueba(
                clave = PasosMap.CONTACTO,
                tituloRes = TextosMapBaro.TITULOS_PASO.getValue(PasosMap.CONTACTO),
                instruccionRes = R.string.obd_catalogo_paso_mapbaro_contacto_instruccion,
                modo = ModoPaso.Grabar(CONTACTO_MS),
            ),
        ),
        analizar = AnalizadorMapBaro::analizar,
        usaAmbiente = true,
    )

    private val definiciones = listOf(barridoTps, minimoRetorno, arranqueFrio, bateriaCarga, mapBaro).associateBy { it.tipo }

    val disponibles: List<TipoPrueba> get() = TipoPrueba.entries.filter { it in definiciones }

    fun definicion(tipo: TipoPrueba): DefinicionPrueba? = definiciones[tipo]

    private fun retorno(clave: String) = PasoPrueba(
        clave = clave,
        tituloRes = TextosMinimo.TITULOS_PASO.getValue(clave),
        instruccionRes = R.string.obd_catalogo_paso_retorno_instruccion,
        modo = ModoPaso.Grabar(RETORNO_MS),
        descartarInicioMs = 0,
    )

    private fun calentado(muestras: List<MuestraCaptura>): Boolean =
        muestras.lastOrNull { it.pid == AnalizadorArranqueFrio.PID_ECT }?.let { it.valor >= AnalizadorArranqueFrio.CALENTADO_C } == true

    private fun sostener(clave: String, @StringRes instruccionRes: Int) = PasoPrueba(
        clave = clave,
        tituloRes = TextosBarridoTps.TITULOS_PASO.getValue(clave),
        instruccionRes = instruccionRes,
        modo = ModoPaso.Sostener(SOSTENER_MS),
    )
}
