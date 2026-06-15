package org.dudblockman.hostileatmosphere.progression;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.dudblockman.hostileatmosphere.client.ClientZoneCache;

import java.util.List;
import java.util.OptionalDouble;

/** Zone query API. Cache lifecycle is managed by {@link ZoneCacheManager}. */
public final class ZoneLookup {

    public record Located(String id, ZoneDefinition def) {}

    public record ZoneAndCeiling(Located zone, double ceiling, double floor, double baseFloor) {}

    private ZoneLookup() {}

    /**
     * Zone check usable on both sides.
     * <ul>
     *   <li><b>Server</b> ({@link ServerLevel}): full Perlin progression data.</li>
     *   <li><b>Client</b>: base zone ceilings from the data-pack registry only.</li>
     * </ul>
     * Always pass world-space coordinates; callers are responsible for transforming sub-level-local positions first.
     */
    public static ZoneDefinition findZoneAt(Level level, double x, double y, double z) {
        if (level instanceof ServerLevel sl) return findZoneAt(sl, x, y, z);
        return ClientZoneCache.findZoneAt(level, x, y, z);
    }

    /** Convenience lookup for non-player callers (e.g. block entity mixins). */
    public static ZoneDefinition findZoneAt(ServerLevel level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        List<ZoneDefinition> zones = ZoneCacheManager.getZonesForDim(dim);
        List<String> ids = ZoneCacheManager.getIdsForDim(dim);
        long tick = level.getGameTime();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        for (int i = 0; i < zones.size(); i++) {
            double ceiling = prog.getEffectiveCeiling(tick, x, z, ids.get(i), zones.get(i).evalCeiling(tick, x, z));
            if (y <= ceiling) return zones.get(i);
        }
        return null;
    }

    /** Returns the zone and its ID at the given position, or {@code null} if in safe air. */
    public static Located findLocatedZone(ServerLevel level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        List<ZoneDefinition> zones = ZoneCacheManager.getZonesForDim(dim);
        List<String> ids = ZoneCacheManager.getIdsForDim(dim);
        long tick = level.getGameTime();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        for (int i = 0; i < zones.size(); i++) {
            String id = ids.get(i);
            ZoneDefinition zone = zones.get(i);
            double ceiling = prog.getEffectiveCeiling(tick, x, z, id, zone.evalCeiling(tick, x, z));
            if (y <= ceiling) return new Located(id, zone);
        }
        return null;
    }

    /**
     * Returns the effective ceiling Y (base + progression offset) for the zone at (x, y, z),
     * or empty if the position is in safe air.
     */
    public static OptionalDouble getEffectiveCeilingAt(ServerLevel level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        List<ZoneDefinition> zones = ZoneCacheManager.getZonesForDim(dim);
        List<String> ids = ZoneCacheManager.getIdsForDim(dim);
        long tick = level.getGameTime();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        for (int i = 0; i < zones.size(); i++) {
            double ceiling = prog.getEffectiveCeiling(tick, x, z, ids.get(i), zones.get(i).evalCeiling(tick, x, z));
            if (y <= ceiling) return OptionalDouble.of(ceiling);
        }
        return OptionalDouble.empty();
    }

    /**
     * Returns the located zone and its effective ceiling Y together with the floor Y (ceiling of
     * the next more-severe zone below, or 0 for the deepest zone), or {@code null} if in safe air.
     */
    public static ZoneAndCeiling findZoneAndFloor(ServerLevel level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        List<ZoneDefinition> zones = ZoneCacheManager.getZonesForDim(dim);
        List<String> ids = ZoneCacheManager.getIdsForDim(dim);
        long tick = level.getGameTime();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        for (int i = 0; i < zones.size(); i++) {
            String id = ids.get(i);
            double ceiling = prog.getEffectiveCeiling(tick, x, z, id, zones.get(i).evalCeiling(tick, x, z));
            if (y <= ceiling) {
                double floor = i > 0
                        ? prog.getEffectiveCeiling(tick, x, z, ids.get(i - 1), zones.get(i - 1).evalCeiling(tick, x, z))
                        : 0;
                double baseFloor = i > 0 ? zones.get(i - 1).evalCeiling(tick, x, z) : 0;
                return new ZoneAndCeiling(new Located(id, zones.get(i)), ceiling, floor, baseFloor);
            }
        }
        return null;
    }

    /** Returns the zone definition for {@code zoneId} in {@code dim}, or null if not found. */
    public static ZoneDefinition findZoneByIdForDim(ResourceLocation dim, String zoneId) {
        List<String> ids = ZoneCacheManager.getIdsForDim(dim);
        List<ZoneDefinition> zones = ZoneCacheManager.getZonesForDim(dim);
        int idx = ids.indexOf(zoneId);
        return idx >= 0 ? zones.get(idx) : null;
    }
}
