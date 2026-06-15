package org.dudblockman.hostileatmosphere.engine;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityAirStateCache {

    private final ConcurrentHashMap<UUID, EntityHazardEngine.EntityAirState> map = new ConcurrentHashMap<>();

    public EntityHazardEngine.EntityAirState get(UUID id) {
        return map.getOrDefault(id, EntityHazardEngine.EntityAirState.ZERO);
    }

    public void put(UUID id, EntityHazardEngine.EntityAirState state) {
        map.put(id, state);
    }

    public void remove(UUID id) {
        map.remove(id);
    }

    public int getAirDebt(UUID id) {
        return get(id).airDebt();
    }

    public void clear() {
        map.clear();
    }
}
