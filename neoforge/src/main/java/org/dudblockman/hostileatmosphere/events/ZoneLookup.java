package org.dudblockman.hostileatmosphere.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the server-side zone cache and provides zone lookup for any caller.
 * The cache is rebuilt on server start and datapack reload via {@link #rebuildZoneCache}.
 */
public final class ZoneLookup {

    private record ZonePair(String id, ZoneDefinition def) {}

    /**
     * Zones per dimension, each list sorted ascending by yCeiling (lowest = most severe first).
     * Rebuilt on datapack reload.
     */
    private record ZoneCache(
            Map<ResourceLocation, List<ZoneDefinition>> defs,
            Map<ResourceLocation, List<String>> ids) {
        static final ZoneCache EMPTY = new ZoneCache(Map.of(), Map.of());
    }

    private static volatile ZoneCache zoneCache = ZoneCache.EMPTY;
    private static volatile Map<ResourceLocation, Integer> leastSevereSecs = Map.of();

    private ZoneLookup() {}

    public static Map<ResourceLocation, List<ZoneDefinition>> getCachedZones()  { return zoneCache.defs(); }
    public static Map<ResourceLocation, List<String>>         getCachedZoneIds() { return zoneCache.ids(); }

    public static List<ZoneDefinition> getZonesForDim(ResourceLocation dim) {
        return zoneCache.defs().getOrDefault(dim, List.of());
    }

    public static List<String> getIdsForDim(ResourceLocation dim) {
        return zoneCache.ids().getOrDefault(dim, List.of());
    }

    public static int getLeastSevereSecsForDim(ResourceLocation dim, int fallback) {
        return leastSevereSecs.getOrDefault(dim, fallback);
    }

    /**
     * Zone check usable on both sides.
     * <ul>
     *   <li><b>Server</b> ({@link ServerLevel}): full Perlin progression data — ceiling varies by X, Z, and time.</li>
     *   <li><b>Client</b>: base zone ceilings from the data-pack registry (no Perlin offset available client-side).</li>
     * </ul>
     * Always pass world-space coordinates; callers are responsible for transforming sub-level-local positions first.
     */
    public static ZoneDefinition findZoneAt(Level level, double x, double y, double z) {
        if (level instanceof ServerLevel sl) return findZoneAt(sl, x, y, z);
        // Client: no Perlin data — evaluate each zone's ceiling pipeline at current game tick.
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

    /** Convenience lookup for non-player callers (e.g. block entity mixins). */
    public static ZoneDefinition findZoneAt(ServerLevel level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        long gameTick = level.getGameTime();
        return AtmosphereEngine.findZone(getZonesForDim(dim), getIdsForDim(dim), gameTick, x, y, z,
                (zoneId, base) -> prog.getEffectiveCeiling(gameTick, x, z, zoneId, base));
    }

    /**
     * Returns the fully effective ceiling for a zone at the player's position:
     * zone ceiling pipeline + per-zone progression offset.
     */
    public static double getEffectiveCeiling(ServerLevel level, ZoneDefinition zone,
                                             double x, double z) {
        ResourceLocation dim = level.dimension().location();
        List<ZoneDefinition> zones = getZonesForDim(dim);
        List<String> ids = getIdsForDim(dim);
        int idx = zones.indexOf(zone);
        String zoneId = (idx >= 0 && idx < ids.size()) ? ids.get(idx) : "all";
        long gameTick = level.getGameTime();
        double base = zone.evalCeiling(gameTick, x, z);
        return AtmosphereProgressionData.get(level.getServer()).getEffectiveCeiling(gameTick, x, z, zoneId, base);
    }

    /** Returns the zone definition for {@code zoneId} in {@code dim}, or null if not found. */
    public static ZoneDefinition findZoneByIdForDim(ResourceLocation dim, String zoneId) {
        List<String> ids = getIdsForDim(dim);
        List<ZoneDefinition> zones = getZonesForDim(dim);
        int idx = ids.indexOf(zoneId);
        return idx >= 0 ? zones.get(idx) : null;
    }

    static void rebuildZoneCache(MinecraftServer server) {
        var registry = server.registryAccess().registryOrThrow(ModRegistries.ZONES);

        Map<ResourceLocation, List<ZonePair>> byDim = new LinkedHashMap<>();
        registry.entrySet().forEach(e ->
                byDim.computeIfAbsent(e.getValue().dimension(), k -> new ArrayList<>())
                        .add(new ZonePair(e.getKey().location().getPath(), e.getValue())));
        byDim.values().forEach(list -> list.sort(Comparator.comparingDouble(p -> p.def().evalCeiling(0, 0, 0))));

        Map<ResourceLocation, List<ZoneDefinition>> newDefs = new LinkedHashMap<>();
        Map<ResourceLocation, List<String>>         newIds  = new LinkedHashMap<>();
        byDim.forEach((dim, list) -> {
            newDefs.put(dim, list.stream().map(ZonePair::def).toList());
            newIds.put(dim,  list.stream().map(ZonePair::id).toList());
        });
        zoneCache = new ZoneCache(Map.copyOf(newDefs), Map.copyOf(newIds));

        Map<ResourceLocation, Integer> newLeastSevere = new LinkedHashMap<>();
        byDim.forEach((dim, list) ->
                newLeastSevere.put(dim, list.stream().mapToInt(p -> p.def().hazardTimeSecs()).max().orElse(1)));
        leastSevereSecs = Map.copyOf(newLeastSevere);
    }
}
