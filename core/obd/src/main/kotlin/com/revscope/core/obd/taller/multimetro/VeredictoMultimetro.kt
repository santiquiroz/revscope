package com.revscope.core.obd.taller.multimetro

import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.sesion.Veredicto

data class VeredictoCombinado(
    val veredicto: Veredicto,
    val titulo: String,
    val interpretacion: String?,
    val celdas: List<EvaluacionCelda>,
    val ecu: ComparacionEcu?,
)

// Cómo quedó un grupo de celdas (la referencia, la masa o la señal) con todo lo medido en él.
internal enum class EstadoGrupo { SIN_MEDIR, CORRECTO, BAJO, ALTO, MIXTO, SIN_REFERENCIA }

// Veredictos redactados como «apunta a…»: el multímetro dice qué parte del circuito falla, no qué repuesto cambiar.
object VeredictoMultimetro {

    fun combinar(
        plantilla: PlantillaCableado,
        lecturas: List<LecturaMultimetro>,
        bandas: Map<String, BandaReferencia>,
        ecu: LecturasEcu? = null,
    ): VeredictoCombinado {
        val celdas = EvaluadorCelda.evaluarTodas(plantilla, lecturas, bandas)
        val sensor = plantilla.cable(FuncionCable.SENAL) != null
        return VeredictoCombinado(
            veredicto = veredicto(celdas),
            titulo = if (sensor) TextosMultimetro.tituloSensor(plantilla, celdas) else TextosMultimetro.tituloGeneral(plantilla, celdas),
            interpretacion = if (sensor) TextosMultimetro.interpretacionSensor(celdas) else TextosMultimetro.interpretacionGeneral(plantilla, celdas),
            celdas = celdas,
            ecu = ComparadorEcu.comparar(plantilla, EvaluadorCelda.filaDe(lecturas, FuncionCable.SENAL), ecu),
        )
    }

    private fun veredicto(celdas: List<EvaluacionCelda>): Veredicto = when {
        celdas.any { it.fueraDeBanda } -> Veredicto.FALLA
        celdas.any { it.estado == EstadoCelda.DENTRO } -> Veredicto.OK
        else -> Veredicto.INFO
    }

    internal fun grupo(celdas: List<EvaluacionCelda>): EstadoGrupo {
        val estados = celdas.map { it.estado }.toSet()
        val bajo = EstadoCelda.BAJO in estados
        val alto = EstadoCelda.ALTO in estados
        return when {
            celdas.isEmpty() -> EstadoGrupo.SIN_MEDIR
            bajo && alto -> EstadoGrupo.MIXTO
            bajo -> EstadoGrupo.BAJO
            alto -> EstadoGrupo.ALTO
            EstadoCelda.DENTRO in estados -> EstadoGrupo.CORRECTO
            else -> EstadoGrupo.SIN_REFERENCIA
        }
    }
}
