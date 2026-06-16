package org.dudblockman.hostileatmosphere.progression;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds and rebuilds the server-side zone cache.
 * Lifecycle is driven by the server event handler; query methods are used by {@link ZoneLookup}.
 */
public final class ZoneCacheManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneCacheManager.class);

    private record ZoneCache(
            Map<ResourceLocation, List<ZoneDefinition>> defs,
            Map<ResourceLocation, List<String>> ids,
            Map<ResourceLocation, Integer> leastSevereSecs,
            Map<ResourceLocation, ResourceKey<Level>> dimKeys) {
        static final ZoneCache EMPTY = new ZoneCache(Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static volatile ZoneCache zoneCache = ZoneCache.EMPTY;

    private ZoneCacheManager() {}

    public static List<ZoneDefinition> getZonesForDim(ResourceLocation dim) {
        return zoneCache.defs().getOrDefault(dim, List.of());
    }

    public static List<String> getIdsForDim(ResourceLocation dim) {
        return zoneCache.ids().getOrDefault(dim, List.of());
    }

    public static Iterable<List<String>> getAllZoneIdsByDim() {
        return zoneCache.ids().values();
    }

    public static int getLeastSevereSecsForDim(ResourceLocation dim, int fallback) {
        return zoneCache.leastSevereSecs().getOrDefault(dim, fallback);
    }

    public static ResourceKey<Level> getDimKeyFor(ResourceLocation dim) {
        return zoneCache.dimKeys().getOrDefault(dim, ResourceKey.create(Registries.DIMENSION, dim));
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

        record ZoneEntry(String id, ZoneDefinition def) {}

        Map<ResourceLocation, List<ZoneEntry>> byDim = new LinkedHashMap<>();
        registry.entrySet().forEach(e ->
                byDim.computeIfAbsent(e.getValue().dimension(), k -> new ArrayList<>())
                        // Use toString() to include the namespace, ensuring cross-namespace uniqueness.
                        .add(new ZoneEntry(e.getKey().location().toString(), e.getValue())));
        byDim.values().forEach(list -> list.sort(Comparator.comparingDouble(p -> p.def().evalCeiling(0, 0, 0))));

        Map<ResourceLocation, List<ZoneDefinition>> newDefs        = new LinkedHashMap<>();
        Map<ResourceLocation, List<String>>         newIds         = new LinkedHashMap<>();
        Map<ResourceLocation, Integer>              newLeastSevere = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceKey<Level>>   newDimKeys     = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<ZoneEntry>> dimEntry : byDim.entrySet()) {
            ResourceLocation dim = dimEntry.getKey();
            List<ZoneEntry> list = dimEntry.getValue();

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

            List<ZoneDefinition> defs = new ArrayList<>(list.size());
            List<String> ids = new ArrayList<>(list.size());
            int maxHazardSecs = 1;
            for (ZoneEntry entry : list) {
                defs.add(entry.def());
                ids.add(entry.id());
                if (entry.def().hazardTimeSecs() > maxHazardSecs) maxHazardSecs = entry.def().hazardTimeSecs();
            }
            newDefs.put(dim, List.copyOf(defs));
            newIds.put(dim, List.copyOf(ids));
            newLeastSevere.put(dim, maxHazardSecs);
            newDimKeys.put(dim, ResourceKey.create(Registries.DIMENSION, dim));
        }
        zoneCache = new ZoneCache(Map.copyOf(newDefs), Map.copyOf(newIds),
                Map.copyOf(newLeastSevere), Map.copyOf(newDimKeys));
    }
}
