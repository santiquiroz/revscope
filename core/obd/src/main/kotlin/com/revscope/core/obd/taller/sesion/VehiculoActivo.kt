package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.data.db.entities.vehicleType
import com.revscope.core.obd.session.ObdSessionManager
import javax.inject.Inject

data class VehiculoTaller(val id: Long, val nombre: String, val claveModelo: String?, val tipo: VehicleType)

fun interface VehiculoActivo {
    fun actual(): VehiculoTaller?
}

class VehiculoActivoObd @Inject constructor(private val sessionManager: ObdSessionManager) : VehiculoActivo {
    override fun actual(): VehiculoTaller? = sessionManager.activeProfile.value?.aVehiculoTaller()
}

fun VehicleProfileEntity.aVehiculoTaller() =
    VehiculoTaller(id = id, nombre = name, claveModelo = knowledgeKey, tipo = vehicleType)
