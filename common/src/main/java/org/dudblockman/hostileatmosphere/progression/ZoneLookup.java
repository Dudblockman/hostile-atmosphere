package org.dudblockman.hostileatmosphere.progression;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneLookup.class);

    public record Located(String id, ZoneDefinition def) {}

    /**
     * Zones per dimension, each list sorted ascending by yCeiling (lowest = most severe first).
     * Rebuilt on datapack reload.
     */
    private record ZoneCache(
            Map<ResourceLocation, List<ZoneDefinition>> defs,
            Map<ResourceLocation, List<String>> ids,
            Map<ResourceLocation, Integer> leastSevereSecs,
            Map<ResourceLocation, ResourceKey<Level>> dimKeys) {
        static final ZoneCache EMPTY = new ZoneCache(Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static volatile ZoneCache zoneCache = ZoneCache.EMPTY;

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
        return zoneCache.leastSevereSecs().getOrDefault(dim, fallback);
    }

    public static ResourceKey<Level> getDimKeyFor(ResourceLocation dim) {
        return zoneCache.dimKeys().getOrDefault(dim, ResourceKey.create(Registries.DIMENSION, dim));
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
        List<ZoneDefinition> zones = getZonesForDim(dim);
        List<String> ids = getIdsForDim(dim);
        long tick = level.getGameTime();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        return AtmosphereEngine.findZone(zones, ids, tick, x, y, z,
                (id, base) -> prog.getEffectiveCeiling(tick, x, z, id, base));
    }

    /** Returns the zone and its ID at the given position, or {@code null} if in safe air. */
    public static Located findLocatedZone(ServerLevel level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        List<ZoneDefinition> zones = getZonesForDim(dim);
        List<String> ids = getIdsForDim(dim);
        long tick = level.getGameTime();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        ZoneDefinition found = AtmosphereEngine.findZone(zones, ids, tick, x, y, z,
                (id, base) -> prog.getEffectiveCeiling(tick, x, z, id, base));
        if (found == null) return null;
        int idx = zones.indexOf(found);
        return idx >= 0 ? new Located(ids.get(idx), found) : null;
    }

    /** Returns the zone definition for {@code zoneId} in {@code dim}, or null if not found. */
    public static ZoneDefinition findZoneByIdForDim(ResourceLocation dim, String zoneId) {
        List<String> ids = getIdsForDim(dim);
        List<ZoneDefinition> zones = getZonesForDim(dim);
        int idx = ids.indexOf(zoneId);
        return idx >= 0 ? zones.get(idx) : null;
    }

    public static void tickAllZoneSources(MinecraftServer server, long tick) {
        ZoneCache cache = zoneCache;
        ServerLevel overworld = server.overworld();
        for (Map.Entry<ResourceLocation, List<ZoneDefinition>> entry : cache.defs().entrySet()) {
            ServerLevel dimLevel = server.getLevel(cache.dimKeys().get(entry.getKey()));
            if (dimLevel == null) dimLevel = overworld;
            for (ZoneDefinition zone : entry.getValue()) {
                for (ZoneDefinition.CeilingLayer layer : zone.ceiling()) {
                    layer.source().serverTick(dimLevel, tick);
                }
            }
        }
    }

    public static void rebuildZoneCache(MinecraftServer server) {
        var registry = server.registryAccess().registryOrThrow(ModRegistries.ZONES);

        Map<ResourceLocation, List<Located>> byDim = new LinkedHashMap<>();
        registry.entrySet().forEach(e ->
                byDim.computeIfAbsent(e.getValue().dimension(), k -> new ArrayList<>())
                        // Use toString() to include the namespace, ensuring cross-namespace uniqueness.
                        .add(new Located(e.getKey().location().toString(), e.getValue())));
        // Sort ascending by ceiling at origin, tick 0. Zones with animated ceilings may cross at
        // runtime — see the crossing-ceiling warning loop below.
        byDim.values().forEach(list -> list.sort(Comparator.comparingDouble(p -> p.def().evalCeiling(0, 0, 0))));

        byDim.forEach((dim, list) -> {
            for (int i = 0; i + 1 < list.size(); i++) {
                ZoneDefinition a = list.get(i).def();
                ZoneDefinition b = list.get(i + 1).def();
                for (long t : new long[]{0L, 6000L, 12000L, 18000L}) {
                    if (a.evalCeiling(t, 0, 0) > b.evalCeiling(t, 0, 0)) {
                        LOGGER.warn("[HostileAtmosphere] Zones '{}' and '{}' in dimension '{}' may have crossing ceilings at runtime.",
                                list.get(i).id(), list.get(i + 1).id(), dim);
                        break;
                    }
                }
            }
        });

        Map<ResourceLocation, List<ZoneDefinition>> newDefs = new LinkedHashMap<>();
        Map<ResourceLocation, List<String>>         newIds  = new LinkedHashMap<>();
        Map<ResourceLocation, Integer>              newLeastSevere = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceKey<Level>>   newDimKeys = new LinkedHashMap<>();
        byDim.forEach((dim, list) -> {
            newDefs.put(dim, list.stream().map(Located::def).toList());
            newIds.put(dim,  list.stream().map(Located::id).toList());
            newLeastSevere.put(dim, list.stream().mapToInt(p -> p.def().hazardTimeSecs()).max().orElse(1));
            newDimKeys.put(dim, ResourceKey.create(Registries.DIMENSION, dim));
        });
        zoneCache = new ZoneCache(Map.copyOf(newDefs), Map.copyOf(newIds),
                Map.copyOf(newLeastSevere), Map.copyOf(newDimKeys));
    }
}
