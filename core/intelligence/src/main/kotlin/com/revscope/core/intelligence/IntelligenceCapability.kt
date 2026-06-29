package com.revscope.core.intelligence

import android.app.ActivityManager
import android.content.Context

private const val MIN_RAM_MB_FOR_ML = 2_048L

/**
 * Determines the intelligence tier based on device hardware and user configuration.
 *
 * Tiers are additive: FULL ⊃ ON_DEVICE ⊃ MINIMAL.
 * The user can always downgrade their tier in Settings; upgrading above device capability
 * is blocked.
 */
object IntelligenceCapability {

    /** Hardware-based maximum tier for this device. */
    fun deviceTier(context: Context): IntelligenceTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamMb = info.totalMem / 1_048_576L
        return if (totalRamMb >= MIN_RAM_MB_FOR_ML) IntelligenceTier.ON_DEVICE
               else IntelligenceTier.MINIMAL
    }

    /**
     * Resolves the actual active tier from the device ceiling and user settings.
     *
     * @param deviceTier      result of [deviceTier]
     * @param claudeApiKey    Claude API key from user settings (null/blank = no cloud)
     * @param userOverride    explicit user preference from Settings, or null for auto
     */
    fun effectiveTier(
        deviceTier: IntelligenceTier,
        claudeApiKey: String?,
        userOverride: IntelligenceTier?,
    ): IntelligenceTier {
        // User can only downgrade, never upgrade beyond device capability
        val ceiling = if (!claudeApiKey.isNullOrBlank() && deviceTier >= IntelligenceTier.ON_DEVICE)
            IntelligenceTier.FULL else deviceTier

        return if (userOverride != null && userOverride <= ceiling) userOverride else ceiling
    }
}
