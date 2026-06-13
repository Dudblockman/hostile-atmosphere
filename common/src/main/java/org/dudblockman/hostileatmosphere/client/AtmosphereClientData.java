package org.dudblockman.hostileatmosphere.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AtmosphereClientData {

    private static final Map<UUID, Integer> AIR_DEBT     = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> TOXIN        = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> DIVING_ACTIVE = new ConcurrentHashMap<>();

    private AtmosphereClientData() {}

    // ---- Air debt ----------------------------------------------------------------------------

    public static void setAirDebt(UUID id, int debt) {
        if (debt <= 0) AIR_DEBT.remove(id);
        else AIR_DEBT.put(id, debt);
    }

    public static int getAirDebt(UUID id) {
        return AIR_DEBT.getOrDefault(id, 0);
    }

    // ---- Toxin level -------------------------------------------------------------------------

    public static void setToxin(UUID id, int toxin) {
        if (toxin <= 0) TOXIN.remove(id);
        else TOXIN.put(id, toxin);
    }

    public static int getToxin(UUID id) {
        return TOXIN.getOrDefault(id, 0);
    }

    // ---- Diving active state -----------------------------------------------------------------

    public static void setDivingActive(UUID id, boolean active) {
        if (!active) DIVING_ACTIVE.remove(id);
        else DIVING_ACTIVE.put(id, true);
    }

    public static boolean isDivingActive(UUID id) {
        return DIVING_ACTIVE.getOrDefault(id, false);
    }

    // ---- Mining fatigue amplifier (synced from server) --------------------------------------

    private static final Map<UUID, Integer> MINING_FATIGUE_AMP = new ConcurrentHashMap<>();

    public static void setMiningFatigueAmp(UUID id, int amp) {
        if (amp < 0) MINING_FATIGUE_AMP.remove(id);
        else MINING_FATIGUE_AMP.put(id, amp);
    }

    /** Returns -1 if no fatigue applies, 0-2 for levels I-III. */
    public static int getMiningFatigueAmp(UUID id) {
        return MINING_FATIGUE_AMP.getOrDefault(id, -1);
    }

    // ---- Zone hazard intensity and boundary data --------------------------------------------

    /**
     * Data-driven intensity: {@code leastSevereTimeSecs / thisZoneTimeSecs}.
     * 0.0 = safe; 1.0 = mildest registered zone; higher = more severe.
     * Scales automatically when data packs add or modify zones.
     */
    private static final Map<UUID, Float> HAZARD_INTENSITY = new ConcurrentHashMap<>();

    public static void setHazardIntensity(UUID id, float intensity) {
        if (intensity <= 0.0f) HAZARD_INTENSITY.remove(id);
        else HAZARD_INTENSITY.put(id, intensity);
    }

    public static float getHazardIntensity(UUID id) {
        return HAZARD_INTENSITY.getOrDefault(id, 0.0f);
    }

    // -----------------------------------------------------------------------------------------

    private static volatile boolean forceHeartWiggle;

    public static boolean isForceHeartWiggle() { return forceHeartWiggle; }
    public static void setForceHeartWiggle(boolean v) { forceHeartWiggle = v; }
}
