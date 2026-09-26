package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.DatosPrueba
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ResultadoPrecondicion
import com.revscope.core.obd.taller.pruebas.SegmentoPaso
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.telemetry.captura.MuestraCaptura

// El barrido del TPS de la Benelli TNT 150i (25-sep-2026) como lo capturaría la prueba guiada: bytes crudos
// del PID 11 a 10 Hz: 6 cerrado (2,35 %), 23-24 medio (9,0-9,4 %), 45-46 a fondo (17,6-18,0 %) y 7 al final.
internal object BarridoBenelli {

    private const val SOSTENIDO_MS = 5_000L
    private const val PAUSA_MS = 1_500L
    private const val BARRIDO_MS = 8_000L
    private const val HZ = 10

    val bandas = ResolutorBandas.resolverTodas(VehicleType.MOTORCYCLE, emptyList())
    private val pasos = CatalogoPruebas.barridoTps.pasos

    fun pct(byte: Int): Double = byte * 100.0 / 255.0

    fun datos(): DatosPrueba {
        val segmentos = mutableListOf<SegmentoPaso>()
        val muestras = mutableListOf<MuestraCaptura>()
        var t = 0L
        var anterior = 6
        listOf(Pasos.CERRADO_1 to listOf(6), Pasos.MEDIO to listOf(23, 24), Pasos.A_FONDO to listOf(45, 46), Pasos.CERRADO_2 to listOf(7))
            .forEach { (clave, bytes) ->
                val desde = anterior
                val n = (PAUSA_MS * HZ / 1_000).toInt()
                muestras += muestrear(t, PAUSA_MS) { i -> desde + (bytes.first() - desde) * i / n }
                anterior = bytes.last()
                t += PAUSA_MS
                segmentos += SegmentoPaso(clave, t, t + SOSTENIDO_MS, 1_000)
                muestras += muestrear(t, SOSTENIDO_MS) { i -> bytes[i % bytes.size] }
                t += SOSTENIDO_MS
            }
        segmentos += SegmentoPaso(Pasos.BARRIDO_LENTO, t, t + BARRIDO_MS, 0)
        muestras += muestrear(t, BARRIDO_MS) { i -> barrido(i) }
        return DatosPrueba(TipoPrueba.TPS_BARRIDO, muestras.mapIndexed { i, m -> m.copy(seq = i.toLong()) }, segmentos)
    }

    fun terminada(eventoId: Long? = 21): EstadoPrueba.Terminada {
        val datos = datos()
        return EstadoPrueba.Terminada(AnalizadorBarridoTps.analizar(datos, bandas), eventoId, datos)
    }

    fun enPaso(indice: Int, fase: FasePaso, restanteMs: Long?) = EstadoPrueba.EnPaso(
        tipo = TipoPrueba.TPS_BARRIDO,
        paso = pasos[indice],
        indice = indice,
        total = pasos.size,
        fase = fase,
        restanteMs = restanteMs,
    )

    // Los últimos 10 s del paso «Medio»: 3 s aún en cerrado, la subida y el sostenido en 9,0-9,4 %.
    fun serieMedio(hastaMs: Long = 30_000): SerieVivo = (0 until 100).map { i ->
        val t = hastaMs - (99 - i) * 100L
        val byte = when {
            i < 30 -> 6
            i < 38 -> 6 + (i - 29) * 2
            else -> if (i % 2 == 0) 23 else 24
        }
        t to pct(byte)
    }

    val precondicionesUnaFallando = listOf(
        ResultadoPrecondicion("Adaptador conectado", cumple = true),
        ResultadoPrecondicion(
            "Motor encendido (1454 rpm)",
            cumple = false,
            "Apaga el motor y deja el contacto puesto: a fondo con el motor encendido no es un barrido seguro",
        ),
        ResultadoPrecondicion("Moto detenida (0 km/h)", cumple = true),
        ResultadoPrecondicion("TPS disponible en esta ECU (PID 11)", cumple = true),
    )

    private fun barrido(i: Int): Int {
        val mitad = (BARRIDO_MS / 100 / 2).toInt()
        val subida = if (i <= mitad) i else 2 * mitad - i
        return 6 + (46 - 6) * subida / mitad
    }

    private fun muestrear(desdeMs: Long, duracionMs: Long, valor: (Int) -> Int): List<MuestraCaptura> =
        (0 until (duracionMs * HZ / 1_000).toInt()).map { i ->
            MuestraCaptura(seq = 0, tMicros = (desdeMs + i * 1_000L / HZ) * 1_000, pid = "11", valor = pct(valor(i)), lote = i.toLong(), latenciaMs = 60)
        }
}
