package com.revscope.core.obd.taller.multimetro

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.CableModelo
import com.revscope.core.obd.taller.modelo.CableadoSensor

// La plantilla del modelo solo aporta los colores (y su fuente): lo esperado de cada cable sigue siendo típico
// o la banda que el modelo o el técnico hayan editado, que llega por las bandas resueltas.
object ResolutorPlantilla {

    const val FUENTE_EDITADA = "Editado por ti"

    fun para(sensor: SensorMultimetro, tipo: VehicleType, cableadoModelo: List<CableadoSensor>): PlantillaCableado {
        val generica = PlantillasGenericas.para(sensor, tipo)
        val delModelo = cableadoModelo.firstOrNull { it.sensor == sensor.name } ?: return generica
        return conColores(generica, delModelo.cables.associate { it.funcion to it.color }, delModelo.fuente)
    }

    fun conColores(plantilla: PlantillaCableado, colores: Map<FuncionCable, String?>, fuente: String?): PlantillaCableado =
        plantilla.copy(
            cables = plantilla.cables.map { it.copy(color = colores[it.funcion]?.trim()?.takeIf(String::isNotEmpty)) },
            fuenteColores = fuente,
        )

    fun aCableado(plantilla: PlantillaCableado, colores: Map<FuncionCable, String?>): CableadoSensor = CableadoSensor(
        sensor = plantilla.sensor.name,
        titulo = plantilla.titulo,
        cables = plantilla.cables.map { CableModelo(it.funcion, colores[it.funcion]?.trim()?.takeIf(String::isNotEmpty)) },
        fuente = FUENTE_EDITADA,
    )

    fun reemplazar(cableado: List<CableadoSensor>, nuevo: CableadoSensor): List<CableadoSensor> =
        cableado.filterNot { it.sensor == nuevo.sensor } + nuevo
}
