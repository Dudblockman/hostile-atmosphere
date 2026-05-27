package org.dudblockman.hostileatmosphere.config;

/**
 * Immutable snapshot of config values passed into AtmosphereEngine each tick.
 * All time values are in seconds; the engine converts to tick rates at runtime using
 * player.getMaxAirSupply() so the maths stays accurate if maxAirSupply is ever modified.
 */
public record AtmosphereSettings(
        int dangerYThreshold,
        int hazardTimeSecs,
        int safeZoneRecoverySecs,
        int gracePeriodDays,
        int rampTier2Secs,
        int rampTier3Secs,
        float rampDamageTier1,
        float rampDamageTier2,
        float rampDamageTier3,
        float rampIntervalTier1Secs,
        float rampIntervalTier2Secs,
        float rampIntervalTier3Secs
) {}
