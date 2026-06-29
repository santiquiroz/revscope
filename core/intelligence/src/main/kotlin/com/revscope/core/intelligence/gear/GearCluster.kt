package com.revscope.core.intelligence.gear

/**
 * One gear's centroid in the adaptive gear table.
 *
 * @property gear             Gear number (1–6)
 * @property centerRatio      Current centroid: speed_kmh * 1000 / rpm
 * @property observationCount Number of observations that have updated this cluster
 */
data class GearCluster(
    val gear: Int,
    val centerRatio: Double,
    val observationCount: Int = 0,
)
