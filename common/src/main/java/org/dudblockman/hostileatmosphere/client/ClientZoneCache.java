package org.dudblockman.hostileatmosphere.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;

import java.util.Map;

public final class ClientZoneCache {

    private ClientZoneCache() {}

    /**
     * Finds the zone at (x, y, z) using only the data-pack registry.
     * Intended for client-side use where server progression data is unavailable.
     */
    public static ZoneDefinition findZoneAt(Level level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        long tick = level.getGameTime();
        return level.registryAccess().registry(ModRegistries.ZONES)
                .flatMap(reg -> reg.stream()
                        .filter(zone -> zone.dimension().equals(dim))
                        .map(zone -> Map.entry(zone.evalCeiling(tick, x, z), zone))
                        .sorted(Map.Entry.comparingByKey())
                        .filter(e -> y <= e.getKey())
                        .map(Map.Entry::getValue)
                        .findFirst())
                .orElse(null);
    }
}
