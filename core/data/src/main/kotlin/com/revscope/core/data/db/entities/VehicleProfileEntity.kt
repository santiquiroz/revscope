package com.revscope.core.data.db.entities

import androidx.room.ColumnInfo
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
    /**
     * Odometer reading, in km, at the moment this profile started tracking sessions —
     * total km = this + SUM(sessions.distanceKm) for the profile. Drives "Mantenimiento".
     * `defaultValue` must match MIGRATION_12_13's `DEFAULT 0` — Room validates both at
     * startup and throws if the additive-migration default doesn't match the entity's.
     */
    @ColumnInfo(defaultValue = "0")
    val odometerBaseKm: Double = 0.0,
    /**
     * "CORRIENTE" | "EXTRA" | "DIESEL" — selects which FUEL_PRICE_* DataStore price
     * estimates this vehicle's trip cost (SessionAggregator). `defaultValue` must match
     * MIGRATION_13_14's `DEFAULT 'CORRIENTE'` (same reasoning as `odometerBaseKm` above).
     */
    @ColumnInfo(defaultValue = "CORRIENTE")
    val fuelType: String = "CORRIENTE",
    /**
     * Número de marchas — gobierna el gear learner. Default 6 (auto típico).
     * `defaultValue` must match MIGRATION_17_18's `DEFAULT 6` (same reasoning as
     * `odometerBaseKm` above).
     */
    @ColumnInfo(defaultValue = "6")
    val gearCount: Int = 6,
)
