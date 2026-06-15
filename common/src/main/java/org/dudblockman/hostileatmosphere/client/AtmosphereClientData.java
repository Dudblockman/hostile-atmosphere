package org.dudblockman.hostileatmosphere.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AtmosphereClientData {

    private static final Map<UUID, Integer> AIR_DEBT             = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> TOXIN                = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> DIVING_ACTIVE        = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> MINING_FATIGUE_AMP   = new ConcurrentHashMap<>();
    private static final Map<UUID, Float>   HAZARD_INTENSITY     = new ConcurrentHashMap<>();
    private static final Map<UUID, Float>   CEILING_OFFSET       = new ConcurrentHashMap<>();
    private static final Map<UUID, Float>   FLOOR_CEILING_OFFSET = new ConcurrentHashMap<>();

    private AtmosphereClientData() {}

    private static <T> void setOrRemove(Map<UUID, T> map, UUID id, T value, boolean isDefault) {
        if (isDefault) map.remove(id);
        else map.put(id, value);
    }

    public static void setAirDebt(UUID id, int v)           { setOrRemove(AIR_DEBT,             id, v, v <= 0); }
    public static int  getAirDebt(UUID id)                   { return AIR_DEBT.getOrDefault(id, 0); }

    public static void setToxin(UUID id, int v)             { setOrRemove(TOXIN,                id, v, v <= 0); }
    public static int  getToxin(UUID id)                     { return TOXIN.getOrDefault(id, 0); }

    public static void setDivingActive(UUID id, boolean v)  { setOrRemove(DIVING_ACTIVE,        id, v, !v); }
    public static boolean isDivingActive(UUID id)            { return DIVING_ACTIVE.getOrDefault(id, false); }

    public static void setMiningFatigueAmp(UUID id, int v)  { setOrRemove(MINING_FATIGUE_AMP,   id, v, v < 0); }
    /** Returns -1 if no fatigue applies, 0–2 for levels I–III. */
    public static int getMiningFatigueAmp(UUID id)           { return MINING_FATIGUE_AMP.getOrDefault(id, -1); }

    /**
     * Data-driven intensity: {@code leastSevereTimeSecs / thisZoneTimeSecs}.
     * 0.0 = safe; 1.0 = mildest registered zone; higher = more severe.
     */
    public static void setHazardIntensity(UUID id, float v) { setOrRemove(HAZARD_INTENSITY,     id, v, v <= 0.0f); }
    public static float getHazardIntensity(UUID id)          { return HAZARD_INTENSITY.getOrDefault(id, 0.0f); }

    /** Progression delta for the player's active zone ceiling; 0 when safe. */
    public static void setCeilingOffset(UUID id, float v)   { setOrRemove(CEILING_OFFSET,       id, v, Math.abs(v) < 1e-4f); }
    public static float getCeilingOffset(UUID id)            { return CEILING_OFFSET.getOrDefault(id, 0.0f); }

    /** Progression delta for the floor zone's ceiling; 0 when safe or no floor zone. */
    public static void setFloorCeilingOffset(UUID id, float v) { setOrRemove(FLOOR_CEILING_OFFSET, id, v, Math.abs(v) < 1e-4f); }
    public static float getFloorCeilingOffset(UUID id)          { return FLOOR_CEILING_OFFSET.getOrDefault(id, 0.0f); }
}
