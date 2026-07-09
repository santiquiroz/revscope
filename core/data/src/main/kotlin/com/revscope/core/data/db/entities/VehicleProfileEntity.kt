package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // "Mazda CX-30"
    val type: String,           // "CAR" | "MOTORCYCLE"
    val vin: String?,
    /** JSON array of active PID strings: ["0C","0D","05"] */
    val enabledPids: String,
    /** JSON array of gear ratio floats: [3.5,2.1,1.4,1.0,0.8,0.6] — null if unknown */
    val gearRatios: String?,
    val createdAt: Long,        // epoch ms
    /** RPM gauge full-scale (e.g. 8000 car, 12000 bike) */
    val maxRpm: Int = 8_000,
    /** Redline — drives the shift light and the RPM audio alert */
    val redlineRpm: Int = 6_500,
    /** Bluetooth MAC of the adapter last linked to this vehicle — drives auto-activation on connect */
    val adapterAddress: String? = null,
    /** License plate, e.g. "ABC123" or moto "NZO28H" — drives PicoYPlacaEngine */
    val plate: String? = null,
    /** Pico y placa city rules id (e.g. "medellin"); null = no pico y placa tracking */
    val picoPlacaCity: String? = null,
    /** SOAT expiration, epoch ms; null = not configured */
    val soatExpiresAt: Long? = null,
    /** Tecnomecánica (RTM) expiration, epoch ms; null = not configured */
    val rtmExpiresAt: Long? = null,
    /** Todo riesgo insurance expiration, epoch ms; null = not configured */
    val insuranceExpiresAt: Long? = null,
)
