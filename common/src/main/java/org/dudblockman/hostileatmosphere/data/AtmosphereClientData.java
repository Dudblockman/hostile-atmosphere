package org.dudblockman.hostileatmosphere.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side mirror of per-player air debt; populated by sync packets from the server. */
public final class AtmosphereClientData {
    private static final Map<UUID, Integer> AIR_DEBT = new ConcurrentHashMap<>();

    private AtmosphereClientData() {}

    public static void setAirDebt(UUID id, int debt) {
        if (debt <= 0) AIR_DEBT.remove(id);
        else AIR_DEBT.put(id, debt);
    }

    public static int getAirDebt(UUID id) {
        return AIR_DEBT.getOrDefault(id, 0);
    }
}
