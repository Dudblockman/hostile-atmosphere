package org.dudblockman.hostileatmosphere.config;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

public record AtmosphereSettings(
        // --- Air depletion ---
        int dangerYThreshold,
        int hazardTimeSecs,
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
        // --- Toxin buildup ---
        int toxinBuildupSecs,
        int toxinRecoverySecs,
        int toxinThreshold1,
        int toxinThreshold2,
        int toxinThreshold3,
        int toxinThreshold4,
        float toxinRetainOnDeath,
        Holder<MobEffect> toxicityEffect
) {}
