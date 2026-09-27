package com.revscope.core.obd.taller.multimetro

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.TipoEvento
import org.json.JSONObject
import kotlin.math.abs

// Lo que la ECU leyó del mismo cable en una prueba guiada de la sesión, en voltios por condición.
data class LecturasEcu(val origen: String, val voltiosPorCondicion: Map<String, Double>)

enum class ResultadoEcu { COINCIDE, ECU_RECIBE_MENOS, ECU_RECIBE_MAS, DIFIEREN }

data class FilaComparacionEcu(val condicion: String, val multimetroV: Double, val ecuV: Double) {
    val diferenciaV: Double get() = ecuV - multimetroV
    val coincide: Boolean get() = abs(diferenciaV) <= ComparadorEcu.TOLERANCIA_V + EPSILON

    private companion object {
        const val EPSILON = 1e-9
    }
}

data class ComparacionEcu(val origen: String, val filas: List<FilaComparacionEcu>, val resultado: ResultadoEcu, val texto: String)

object ComparadorEcu {

    // Típico: el multímetro y la conversión de % a V de la ECU difieren unas centésimas; más de 0,1 V ya es otra señal.
    const val TOLERANCIA_V = 0.1

    fun comparar(plantilla: PlantillaCableado, senal: Map<String, Double>, ecu: LecturasEcu?): ComparacionEcu? {
        if (ecu == null) return null
        if (!hayCondicionesRequeridas(plantilla.condicionesEcu, senal, ecu.voltiosPorCondicion)) return null
        val filas = plantilla.condiciones.map { it.clave }
            .filter { it in plantilla.condicionesEcu }
            .mapNotNull { c -> fila(c, senal[c], ecu.voltiosPorCondicion[c]) }
        if (filas.isEmpty()) return null
        val resultado = resultado(filas)
        return ComparacionEcu(ecu.origen, filas, resultado, texto(resultado, filas, plantilla))
    }

    private fun hayCondicionesRequeridas(
        condiciones: Set<String>,
        senal: Map<String, Double>,
        ecu: Map<String, Double>,
    ): Boolean = senal.keys.containsAll(condiciones) && ecu.keys.containsAll(condiciones)

    private fun fila(condicion: String, multimetro: Double?, ecu: Double?): FilaComparacionEcu? {
        if (multimetro == null || ecu == null) return null
        return FilaComparacionEcu(condicion, multimetro, ecu)
    }

    private fun resultado(filas: List<FilaComparacionEcu>): ResultadoEcu {
        val distintas = filas.filterNot { it.coincide }
        return when {
            distintas.isEmpty() -> ResultadoEcu.COINCIDE
            distintas.all { it.diferenciaV < 0 } -> ResultadoEcu.ECU_RECIBE_MENOS
            distintas.all { it.diferenciaV > 0 } -> ResultadoEcu.ECU_RECIBE_MAS
            else -> ResultadoEcu.DIFIEREN
        }
    }

    private fun texto(resultado: ResultadoEcu, filas: List<FilaComparacionEcu>, plantilla: PlantillaCableado): String {
        val todas = condiciones(filas, plantilla)
        val distintas = condiciones(filas.filterNot { it.coincide }, plantilla)
        val tolerancia = "±${FormatoTaller.numero(TOLERANCIA_V, 1)} V, típico"
        return when (resultado) {
            ResultadoEcu.COINCIDE ->
                "Coincide con la prueba guiada: la ECU recibe lo mismo que mide el multímetro ($tolerancia, en $todas), " +
                    "así que el cableado entre el sensor y la ECU no cambia la señal."
            ResultadoEcu.ECU_RECIBE_MENOS ->
                "La ECU recibe menos de lo que mide el multímetro en $distintas: apunta al cableado o al conector del " +
                    "lado de la ECU."
            ResultadoEcu.ECU_RECIBE_MAS ->
                "La ECU recibe más de lo que mide el multímetro en $distintas: revisa la masa del sensor del lado de la " +
                    "ECU o un cruce en el cableado."
            ResultadoEcu.DIFIEREN ->
                "La ECU y el multímetro difieren más de $tolerancia en $distintas: repite las dos medidas en la " +
                    "misma posición."
        }
    }

    private fun condiciones(filas: List<FilaComparacionEcu>, plantilla: PlantillaCableado): String =
        filas.map { plantilla.condicion(it.condicion)?.etiqueta?.lowercase() ?: it.condicion }.let(::enumerar)

    private fun enumerar(partes: List<String>): String = when (partes.size) {
        0 -> ""
        1 -> partes.single()
        else -> partes.dropLast(1).joinToString(", ") + " y " + partes.last()
    }
}

// La última prueba guiada terminada de la sesión que lee el mismo sensor: por ahora solo el barrido del TPS.
object LecturasEcuSesion {

    fun desde(eventos: List<EventoTaller>, sensor: SensorMultimetro): LecturasEcu? {
        if (sensor != SensorMultimetro.TPS) return null
        return eventos
            .filter { it.tipo == TipoEvento.PRUEBA_GUIADA }
            .sortedByDescending { it.instante }
            .firstNotNullOfOrNull(::barridoTps)
    }

    private fun barridoTps(evento: EventoTaller): LecturasEcu? {
        val payload = runCatching { JSONObject(evento.payloadJson) }.getOrNull() ?: return null
        if (payload.optString("prueba") != TipoPrueba.TPS_BARRIDO.name || payload.optString("estado") != "TERMINADA") return null
        val detalle = payload.optJSONObject("detalle") ?: return null
        val voltios = mapOf(
            CondicionesMultimetro.CERRADO to detalle.numero("cerradoV"),
            CondicionesMultimetro.MEDIO to detalle.numero("medioV"),
            CondicionesMultimetro.FONDO to detalle.numero("fondoV"),
        ).filterValues { it != null }.mapValues { it.value!! }
        return voltios.takeIf { it.isNotEmpty() }?.let { LecturasEcu("Prueba guiada «${TipoPrueba.TPS_BARRIDO.titulo}» de esta sesión", it) }
    }

    private fun JSONObject.numero(nombre: String): Double? = optDouble(nombre).takeUnless(Double::isNaN)
}
