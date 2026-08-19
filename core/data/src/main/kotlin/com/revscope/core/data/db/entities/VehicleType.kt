package com.revscope.core.data.db.entities

/** Tipo de vehículo del perfil. El String crudo vive en la DB desde v1; esto lo vuelve seguro. */
enum class VehicleType {
    CAR,
    MOTORCYCLE;

    companion object {
        fun from(raw: String?): VehicleType = if (raw == "MOTORCYCLE") MOTORCYCLE else CAR
    }
}

val VehicleProfileEntity.vehicleType: VehicleType
    get() = VehicleType.from(type)
