package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria
import com.revscope.core.obd.taller.pruebas.AnalizadorMapBaro
import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.ContextoAnalisis
import com.revscope.core.obd.taller.pruebas.ContextoPrueba
import com.revscope.core.obd.taller.pruebas.DatosPrueba
import com.revscope.core.obd.taller.pruebas.DesfaseVoltaje
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.LecturasAmbiente
import com.revscope.core.obd.taller.pruebas.Precondicion
import com.revscope.core.obd.taller.pruebas.ResultadoPrecondicion
import com.revscope.core.obd.taller.pruebas.SegmentoPaso
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos as PasosBateria

// Batería y MAP de la Benelli TNT 150i como los capturaría la prueba guiada: 12,2 V con el contacto y la
// farola encendida, carga de 14,1-14,3 V a rpm altas y el MAP en 70 kPa con el motor apagado (a unos 1 500 m
// la barométrica estimada ronda 84,6 kPa). El valle del arranque no se midió en el caso: aquí es sintético.
internal object PruebasElectricasBenelli {

    private const val HZ = 20
    val bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())

    // ── Batería y carga ─────────────────────────────────────────────────────

    fun bateria(desfase: DesfaseVoltaje = DesfaseVoltaje.SIN_CALIBRAR): DatosPrueba {
        val contacto = SegmentoPaso(PasosBateria.CONTACTO, 0, 10_000, 1_000)
        val arranque = SegmentoPaso(PasosBateria.ARRANQUE, 10_000, 18_000)
        val minimo = SegmentoPaso(PasosBateria.MINIMO, 18_000, 33_000, 1_000)
        val altas = SegmentoPaso(PasosBateria.RPM_ALTAS, 34_500, 44_500, 1_000)
        val muestras = serie(contacto, valor = ::reposo) + serie(arranque, valor = ::arranque) +
            serie(minimo) { s -> alternar(s, 13.4, 13.3) } + serie(altas) { s -> alternar(s, 14.1, 14.3) }
        return DatosPrueba(TipoPrueba.BATERIA_CARGA, ordenar(muestras), listOf(contacto, arranque, minimo, altas), contexto = ContextoAnalisis(desfaseVoltaje = desfase))
    }

    fun bateriaTerminada(eventoId: Long? = 24): EstadoPrueba.Terminada {
        val datos = bateria()
        return EstadoPrueba.Terminada(AnalizadorBateria.analizar(datos, bandas), eventoId, datos)
    }

    // El adaptador deja de responder al empezar a girar el motor de arranque y el enlace se pierde.
    fun bateriaConReinicio(eventoId: Long? = 25): EstadoPrueba.Terminada {
        val contacto = SegmentoPaso(PasosBateria.CONTACTO, 0, 10_000, 1_000)
        val arranque = SegmentoPaso(PasosBateria.ARRANQUE, 10_000, 14_100)
        val muestras = serie(contacto, valor = ::reposo) + serie(arranque) { s -> if (s < 1.0) 12.2 else 10.1 }
            .filter { it.tMicros / 1_000 <= 11_050 }
        val datos = DatosPrueba(
            TipoPrueba.BATERIA_CARGA,
            ordenar(muestras),
            listOf(contacto, arranque),
            contexto = ContextoAnalisis(enlacePerdidoEn = PasosBateria.ARRANQUE),
        )
        return EstadoPrueba.Terminada(AnalizadorBateria.analizar(datos, bandas), eventoId, datos)
    }

    // Paso 2 de 4 grabando, a 4,5 s de terminar: el voltaje cayó al girar el motor de arranque y ya carga.
    fun enArranque() = enPaso(TipoPrueba.BATERIA_CARGA, 1, FasePaso.GRABANDO, 4_500)

    fun serieArranque(hastaMs: Long = 13_500): SerieVivo = (0 until 200).map { i ->
        val t = hastaMs - (199 - i) * 50L
        t to if (t >= 10_000) arranque((t - 10_000) / 1_000.0) else reposo(t / 1_000.0)
    }

    // Paso 3 de 4 grabando el mínimo, con la banda típica de carga sombreada.
    fun enMinimo() = enPaso(TipoPrueba.BATERIA_CARGA, 2, FasePaso.GRABANDO, 9_000)

    fun serieMinimo(hastaMs: Long = 24_000): SerieVivo = (0 until 200).map { i ->
        val t = hastaMs - (199 - i) * 50L
        t to alternar(t / 1_000.0, 13.4, 13.3)
    }

    // Lo que evalúan de verdad las precondiciones con el contacto puesto y el motor apagado.
    val precondicionesBateria: List<ResultadoPrecondicion> = evaluar(CatalogoPruebas.bateriaCarga.precondiciones)

    // 12,2 V con la farola y, de vez en cuando, 12,3 V: la resolución de AT RV es de 0,1 V.
    private fun reposo(s: Double): Double = if ((s * 4).toInt() % 4 == 0) 12.3 else 12.2

    private fun arranque(s: Double): Double = when {
        s < 1.0 -> 12.2
        s < 1.3 -> 10.6
        s < 2.4 -> 11.2
        s < 3.0 -> 13.0
        else -> 13.4
    }

    // ── MAP contra la barométrica ───────────────────────────────────────────

    fun map(): DatosPrueba {
        val contacto = SegmentoPaso(AnalizadorMapBaro.Pasos.CONTACTO, 0, 10_000, 1_000)
        val muestras = serie(contacto, AnalizadorMapBaro.PID_MAP) { s -> alternar(s, 70.0, 71.0) }
        return DatosPrueba(
            TipoPrueba.MAP_BARO,
            ordenar(muestras),
            listOf(contacto),
            contexto = ContextoAnalisis(ambiente = LecturasAmbiente(altitudGpsM = 1_500.0)),
        )
    }

    fun mapTerminado(eventoId: Long? = 26): EstadoPrueba.Terminada {
        val datos = map()
        return EstadoPrueba.Terminada(AnalizadorMapBaro.analizar(datos, bandas), eventoId, datos)
    }

    fun enMap() = enPaso(TipoPrueba.MAP_BARO, 0, FasePaso.GRABANDO, 6_000)

    fun serieMap(hastaMs: Long = 4_000): SerieVivo = (0 until 40).map { i ->
        val t = hastaMs - (39 - i) * 100L
        t to alternar(t / 1_000.0, 70.0, 71.0)
    }

    val precondicionesMapSinBaro: List<ResultadoPrecondicion> = evaluar(CatalogoPruebas.mapBaro.precondiciones, soportados = setOf("0B", "0C", "0D"))

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private fun evaluar(reglas: List<Precondicion>, soportados: Set<String> = setOf("0B", "0C", "0D", "33")): List<ResultadoPrecondicion> {
        val ahora = 1_758_848_000_000L
        val lecturas = listOf("0C" to 0.0, "0D" to 0.0).associate { (pid, v) -> pid to ObdReading(pid, v, "", ahora) }
        val ctx = ContextoPrueba(conectado = true, lecturas = lecturas, soportado = { it in soportados }, ahoraMs = ahora)
        return reglas.map { it.evaluar(ctx) }
    }

    private fun alternar(s: Double, a: Double, b: Double) = if ((s * 4).toInt() % 2 == 0) a else b

    private fun enPaso(tipo: TipoPrueba, indice: Int, fase: FasePaso, restanteMs: Long?): EstadoPrueba.EnPaso {
        val pasos = checkNotNull(CatalogoPruebas.definicion(tipo)).pasos
        return EstadoPrueba.EnPaso(tipo, pasos[indice], indice, pasos.size, fase, restanteMs)
    }

    private fun serie(seg: SegmentoPaso, pid: String = AnalizadorBateria.PID_VOLTAJE, valor: (Double) -> Double): List<MuestraCaptura> =
        (0 until ((seg.finMs - seg.inicioMs) * HZ / 1_000).toInt()).map { i ->
            muestra(seg.inicioMs + i * 1_000L / HZ, pid, valor(i.toDouble() / HZ))
        }

    private fun muestra(tMs: Long, pid: String, valor: Double) =
        MuestraCaptura(seq = 0, tMicros = tMs * 1_000, pid = pid, valor = valor, lote = tMs, latenciaMs = 40)

    private fun ordenar(muestras: List<MuestraCaptura>) = muestras.sortedBy { it.tMicros }.mapIndexed { i, m -> m.copy(seq = i.toLong()) }
}
