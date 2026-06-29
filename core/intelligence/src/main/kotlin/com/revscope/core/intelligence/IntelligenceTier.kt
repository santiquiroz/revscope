package com.revscope.core.intelligence

/**
 * Capability tier that determines which intelligence features are active.
 *
 * MINIMAL  — rule-based only; works on any device, no ML, no network
 * ON_DEVICE — adaptive gear learning + anomaly detection; no network required
 * FULL     — all ON_DEVICE features + Claude AI for DTC explanations
 *
 * The tier is determined by [IntelligenceCapability.effectiveTier] and can be
 * overridden by the user in Settings.
 */
enum class IntelligenceTier {
    MINIMAL,
    ON_DEVICE,
    FULL,
}
