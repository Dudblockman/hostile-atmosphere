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
) {}
