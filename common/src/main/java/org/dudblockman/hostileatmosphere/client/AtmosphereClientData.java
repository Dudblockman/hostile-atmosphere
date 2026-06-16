package org.dudblockman.hostileatmosphere.client;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AtmosphereClientData {

    public static volatile boolean forceHeartWiggle;

    private static final ConcurrentHashMap<UUID, ClientState> STATES = new ConcurrentHashMap<>();

    static final class ClientState {
        int     airDebt;
        int     toxin;
        boolean divingActive;
        int     miningFatigueAmp = -1;
        float   hazardIntensity;
        float   ceilingOffset;
        float   floorCeilingOffset;
    }

    private AtmosphereClientData() {}

    public static void setAirDebt(UUID id, int v) {
        if (v <= 0) { ClientState s = STATES.get(id); if (s != null) s.airDebt = 0; }
        else STATES.computeIfAbsent(id, k -> new ClientState()).airDebt = v;
    }
    public static int getAirDebt(UUID id) {
        ClientState s = STATES.get(id); return s != null ? s.airDebt : 0;
    }

    public static void setToxin(UUID id, int v) {
        if (v <= 0) { ClientState s = STATES.get(id); if (s != null) s.toxin = 0; }
        else STATES.computeIfAbsent(id, k -> new ClientState()).toxin = v;
    }
    public static int getToxin(UUID id) {
        ClientState s = STATES.get(id); return s != null ? s.toxin : 0;
    }

    public static void setDivingActive(UUID id, boolean v) {
        if (!v) { ClientState s = STATES.get(id); if (s != null) s.divingActive = false; }
        else STATES.computeIfAbsent(id, k -> new ClientState()).divingActive = true;
    }
    public static boolean isDivingActive(UUID id) {
        ClientState s = STATES.get(id); return s != null && s.divingActive;
    }

    public static void setMiningFatigueAmp(UUID id, int v) {
        if (v < 0) { ClientState s = STATES.get(id); if (s != null) s.miningFatigueAmp = -1; }
        else STATES.computeIfAbsent(id, k -> new ClientState()).miningFatigueAmp = v;
    }
    /** Returns -1 if no fatigue applies, 0–2 for levels I–III. */
    public static int getMiningFatigueAmp(UUID id) {
        ClientState s = STATES.get(id); return s != null ? s.miningFatigueAmp : -1;
    }

    /**
     * Data-driven intensity: {@code leastSevereTimeSecs / thisZoneTimeSecs}.
     * 0.0 = safe; 1.0 = mildest registered zone; higher = more severe.
     */
    public static void setHazardIntensity(UUID id, float v) {
        if (v <= 0.0f) { ClientState s = STATES.get(id); if (s != null) s.hazardIntensity = 0.0f; }
        else STATES.computeIfAbsent(id, k -> new ClientState()).hazardIntensity = v;
    }
    public static float getHazardIntensity(UUID id) {
        ClientState s = STATES.get(id); return s != null ? s.hazardIntensity : 0.0f;
    }

    /** Progression delta for the player's active zone ceiling; 0 when safe. */
    public static void setCeilingOffset(UUID id, float v) {
        if (Math.abs(v) < 1e-4f) { ClientState s = STATES.get(id); if (s != null) s.ceilingOffset = 0.0f; }
        else STATES.computeIfAbsent(id, k -> new ClientState()).ceilingOffset = v;
    }
    public static float getCeilingOffset(UUID id) {
        ClientState s = STATES.get(id); return s != null ? s.ceilingOffset : 0.0f;
    }

    /** Progression delta for the floor zone's ceiling; 0 when safe or no floor zone. */
    public static void setFloorCeilingOffset(UUID id, float v) {
        if (Math.abs(v) < 1e-4f) { ClientState s = STATES.get(id); if (s != null) s.floorCeilingOffset = 0.0f; }
        else STATES.computeIfAbsent(id, k -> new ClientState()).floorCeilingOffset = v;
    }
    public static float getFloorCeilingOffset(UUID id) {
        ClientState s = STATES.get(id); return s != null ? s.floorCeilingOffset : 0.0f;
    }
}
