package org.dudblockman.hostileatmosphere.config;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;

public record AtmosphereSettings(
        // --- Air depletion ---
        int safeZoneRecoverySecs,
        int gracePeriodDays,
        // --- Miasma damage ramp ---
        int rampTier2Secs,
        int rampTier3Secs,
        float rampDamageTier1,
        float rampDamageTier2,
        float rampDamageTier3,
        float rampIntervalTier1Secs,
        float rampIntervalTier2Secs,
        float rampIntervalTier3Secs,
        // --- Toxin ---
        int toxinRecoverySecs,
        int toxinThreshold1,
        int toxinThreshold2,
        int toxinThreshold3,
        int toxinThreshold4,
        int toxinDeathCap,
        boolean rainToxinMultiplierEnabled,
        float rainToxinMultiplier,
        // --- Protection ---
        float underwaterAirDebtMultiplier,
        float underwaterToxinMultiplier,
        boolean conduitPurification,
        float conduitPurificationAirDebtMultiplier,
        float conduitPurificationToxinMultiplier,
        float expeditionToxinMultiplier,
        Holder<MobEffect> toxicityEffect,
        Holder<Attribute> airDrainRate,
        Holder<Attribute> toxinRate
) {
    /** Default settings mirroring the shipped config file values, for use in tests. */
    public static AtmosphereSettings defaults() {
        return new AtmosphereSettings(
                30, 3, 10, 30, 1f, 2f, 4f, 1f, 0.75f, 0.5f,
                14400, 250, 500, 750, 950, 500,
                false, 1.5f,
                0.6f, 0.6f, false, 0f, 0f, 0.5f,
                null, null, null);
    }
}
