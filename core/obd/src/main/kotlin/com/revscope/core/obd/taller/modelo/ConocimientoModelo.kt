package com.revscope.core.obd.taller.modelo

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.referencia.BandaReferencia

data class ConocimientoModelo(
    val clave: String,
    val nombre: String,
    val tipo: VehicleType,
    val ecu: String?,
    val fuenteEcu: String?,
    val notasProtocolo: String,
    val notas: List<NotaModelo>,
    val cableado: List<CableadoSensor>,
    val repuestos: List<RepuestoModelo>,
    val bandas: List<BandaReferencia>,
    val editadoPorUsuario: Boolean = false,
)

data class ResumenModelo(val clave: String, val nombre: String, val tipo: VehicleType)

enum class TipoNota { NOTA, EJEMPLO, PENDIENTE, FUENTE }

data class NotaModelo(val tipo: TipoNota, val texto: String, val fuente: String, val url: String? = null) {
    init {
        require(texto.isNotBlank()) { "Nota sin texto" } // i18n-ignore
        require(fuente.isNotBlank()) { "La nota «$texto» necesita citar la fuente" } // i18n-ignore
    }
}

enum class TipoRepuesto { OEM, NO_EQUIVALENTE, ALTERNATIVO }

data class RepuestoModelo(
    val rol: String,
    val referencia: String,
    val descripcion: String,
    val tipo: TipoRepuesto,
    val nota: String,
    val fuente: String,
) {
    init {
        require(referencia.isNotBlank()) { "Repuesto $rol sin referencia" } // i18n-ignore
        require(fuente.isNotBlank()) { "El repuesto $referencia necesita citar la fuente" } // i18n-ignore
    }
}

data class CableadoSensor(
    val sensor: String,
    val titulo: String,
    val cables: List<CableModelo>,
    val fuente: String,
) {
    init {
        require(cables.isNotEmpty()) { "Cableado de $sensor sin cables" } // i18n-ignore
        require(fuente.isNotBlank()) { "El cableado de $sensor necesita citar la fuente" } // i18n-ignore
    }
}

data class CableModelo(val funcion: FuncionCable, val color: String?)
