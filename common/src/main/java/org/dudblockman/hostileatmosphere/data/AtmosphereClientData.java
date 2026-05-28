package org.dudblockman.hostileatmosphere.data;

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
}
