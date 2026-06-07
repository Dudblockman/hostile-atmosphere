package org.dudblockman.hostileatmosphere.events;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.client.AtmosphereClientData;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.compat.ProtectionLevel;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.network.SyncAirDebtPayload;
import org.dudblockman.hostileatmosphere.network.SyncDivingActivePayload;
import org.dudblockman.hostileatmosphere.network.SyncToxinPayload;
import org.dudblockman.hostileatmosphere.network.SyncZoneSeverityPayload;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModAttachments;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereEventHandler {

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

    /**
     * Tracks last sent zone state per player to avoid redundant packets.
     * int[0] = severity*2 + (approaching?1:0), int[1] = zoneCeilingY, int[2] = zoneFloorY.
     * Ceiling and floor are included so Perlin-noise-driven boundary shifts trigger a resend.
     */
    private static final Map<UUID, int[]>     lastZoneState      = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean>   lastDivingState    = new ConcurrentHashMap<>();
    /** Fractional backtank units owed from retroactive debt-recovery drain, pending consumption. */
    private static final Map<UUID, Float>     backtankDebtDrain  = new ConcurrentHashMap<>();
    /** Players with ceiling-grid debug particles enabled; value = radius in blocks. */
    private static final Map<UUID, Integer>   particleGridRadii  = new ConcurrentHashMap<>();

    public static Map<ResourceLocation, List<ZoneDefinition>> getCachedZones()  { return zoneCache.defs(); }
    public static Map<ResourceLocation, List<String>>         getCachedZoneIds() { return zoneCache.ids(); }

    // ------------------------------------------------------------------------------------------
    // Ceiling-grid debug particles
    // ------------------------------------------------------------------------------------------

    /** Enables (radius > 0) or disables (radius == 0) the ceiling particle grid for the source player. */
    public static int toggleParticleGrid(CommandSourceStack src, int radius) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[HA] Ceiling grid requires a player source."));
            return 0;
        }
        if (radius <= 0) {
            particleGridRadii.remove(player.getUUID());
            src.sendSuccess(() -> Component.literal("[HA] Ceiling particle grid disabled."), false);
            return 0;
        }
        particleGridRadii.put(player.getUUID(), radius);
        src.sendSuccess(() -> Component.literal("[HA] Ceiling particle grid enabled (radius " + radius + "). Use radius 0 to disable."), false);
        return radius;
    }

    private static void renderCeilingGrid(ServerPlayer player, List<ZoneDefinition> zones,
            List<String> ids, long tick, double px, double pz,
            AtmosphereProgressionData progression, int radius) {
        ServerLevel level = (ServerLevel) player.level();
        int cx = (int) Math.floor(px);
        int cz = (int) Math.floor(pz);
        int n = zones.size();
        if (n == 0) return;

        for (int i = 0; i < n; i++) {
            // Evenly-spaced hue across the colour wheel, full saturation & brightness.
            int packed = hsbToRgb((float) i / n);
            float r = ((packed >> 16) & 0xFF) / 255.0f;
            float g = ((packed >>  8) & 0xFF) / 255.0f;
            float b = ( packed        & 0xFF) / 255.0f;
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.0f);

            String zoneId = i < ids.size() ? ids.get(i) : "all";
            ZoneDefinition zone = zones.get(i);

            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    double wx = cx + dx + 0.5;
                    double wz = cz + dz + 0.5;
                    double base    = zone.evalCeiling(tick, wx, wz);
                    double ceiling = progression.getEffectiveCeiling(tick, wx, wz, zoneId, base);
                    level.sendParticles(player, dust, false, wx, ceiling, wz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /** Pure HSB→RGB for hue∈[0,1], s=1, b=1 — avoids java.awt.Color in server-side code. */
    private static int hsbToRgb(float hue) {
        int h = (int) (hue * 6);
        float f = hue * 6 - h;
        float q = 1 - f, t = f;
        float rv, gv, bv;
        switch (h % 6) {
            case 0 -> { rv = 1; gv = t; bv = 0; }
            case 1 -> { rv = q; gv = 1; bv = 0; }
            case 2 -> { rv = 0; gv = 1; bv = t; }
            case 3 -> { rv = 0; gv = q; bv = 1; }
            case 4 -> { rv = t; gv = 0; bv = 1; }
            default -> { rv = 1; gv = 0; bv = q; }
        }
        return ((int)(rv * 255) << 16) | ((int)(gv * 255) << 8) | (int)(bv * 255);
    }

    // ------------------------------------------------------------------------------------------

    private static List<ZoneDefinition> dimZones(ResourceLocation dim) {
        return zoneCache.defs().getOrDefault(dim, List.of());
    }

    private static List<String> dimIds(ResourceLocation dim) {
        return zoneCache.ids().getOrDefault(dim, List.of());
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
        return AtmosphereEngine.findZone(dimZones(dim), dimIds(dim), gameTick, x, y, z,
                (zoneId, base) -> prog.getEffectiveCeiling(gameTick, x, z, zoneId, base));
    }

    /**
     * Returns the fully effective ceiling for a zone at the player's position:
     * zone ceiling pipeline + per-zone progression offset.
     */
    public static double getEffectiveCeiling(ServerLevel level, ZoneDefinition zone,
                                             double x, double z) {
        ResourceLocation dim = level.dimension().location();
        List<ZoneDefinition> zones = dimZones(dim);
        List<String> ids = dimIds(dim);
        int idx = zones.indexOf(zone);
        String zoneId = (idx >= 0 && idx < ids.size()) ? ids.get(idx) : "all";
        long gameTick = level.getGameTime();
        double base = zone.evalCeiling(gameTick, x, z);
        return AtmosphereProgressionData.get(level.getServer()).getEffectiveCeiling(gameTick, x, z, zoneId, base);
    }

    private static int computeFatigueAmp(int toxinLevel, AtmosphereSettings cfg) {
        return Math.max(-1, AtmosphereEngine.getToxinAmplifier(toxinLevel, cfg) - 1);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long tick = overworld.getGameTime();
        // TODO: PredicateSource evaluates predicates against the overworld regardless of zone dimension.
        // Fixing requires zone-to-dimension mapping in the tick dispatch; see AtmosphereProgressionData.serverTick.
        AtmosphereProgressionData.get(server).serverTick(overworld, tick);

        // Tick ValueSource instances embedded in datapack zone ceiling pipelines.
        // These are not stored in AtmosphereProgressionData so they must be ticked separately.
        // Without this, PredicateSource instances in zone ceiling layers stay at multiplier=0.0 permanently.
        for (Map.Entry<ResourceLocation, List<ZoneDefinition>> entry : zoneCache.defs().entrySet()) {
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, entry.getKey());
            ServerLevel dimLevel = server.getLevel(dimKey);
            if (dimLevel == null) dimLevel = overworld;
            for (ZoneDefinition zone : entry.getValue()) {
                for (ZoneDefinition.CeilingLayer layer : zone.ceiling()) {
                    layer.source().serverTick(dimLevel, tick);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuildZoneCache(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        rebuildZoneCache(event.getPlayerList().getServer());
    }

    private static void rebuildZoneCache(MinecraftServer server) {
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

    /** Returns the zone definition for {@code zoneId} in {@code dim}, or null if not found. */
    public static ZoneDefinition findZoneByIdForDim(ResourceLocation dim, String zoneId) {
        List<String> ids = dimIds(dim);
        List<ZoneDefinition> zones = dimZones(dim);
        int idx = ids.indexOf(zoneId);
        return idx >= 0 ? zones.get(idx) : null;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;

        ResourceLocation dim = player.level().dimension().location();
        List<ZoneDefinition> zones = dimZones(dim);
        List<String>         ids   = dimIds(dim);

        long   gameTick  = player.level().getGameTime();
        double px = player.getX(), pz = player.getZ();
        AtmosphereProgressionData progression = AtmosphereProgressionData.get(player.getServer());

        ZoneDefinition activeZone = AtmosphereEngine.findZone(
                zones, ids, gameTick, px, player.getEyeY(), pz,
                (zoneId, base) -> progression.getEffectiveCeiling(gameTick, px, pz, zoneId, base));

        // Ceiling-grid debug particles — rendered in all game modes.
        Integer gridRadius = particleGridRadii.get(player.getUUID());
        if (gridRadius != null && gameTick % 10 == 0) {
            renderCeilingGrid(player, zones, ids, gameTick, px, pz, progression, gridRadius);
        }

        // Creative and spectator skip hazard logic but still get zone sync so particles are visible.
        if (player.isCreative() || player.isSpectator()) {
            syncZoneSeverity(player, activeZone, progression, gameTick, px, pz, zones, ids);
            return;
        }

        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        int maxAir   = player.getMaxAirSupply();
        int oldDebt  = data.getAirDebt();
        int oldToxin = data.getToxinLevel();

        var cfg        = AtmosphereConfig.getSettings();
        if (cfg == null) return;
        var protection = CreateCompat.getProtection(player);

        AtmosphereEngine.tick(player, data, cfg, protection, activeZone);

        boolean divingActive = (protection == ProtectionLevel.SEALED || protection == ProtectionLevel.RESPIRATOR)
                && activeZone != null;

        if (divingActive != lastDivingState.getOrDefault(player.getUUID(), false)) {
            lastDivingState.put(player.getUUID(), divingActive);
            PacketDistributor.sendToPlayer(player, new SyncDivingActivePayload(divingActive));
        }

        if (divingActive) {
            // Retroactive debt compensation: when the suit recovers in-zone debt, drain the
            // backtank proportional to the zone's hazard time. Full debt recovery costs
            // hazardTimeSecs seconds of backtank air — i.e. equipping gear cannot recover
            // debt for free; it costs the same tank time it would have taken to incur it.
            int debtRecovered = oldDebt - data.getAirDebt();
            if (debtRecovered > 0 && activeZone != null && maxAir > 0) {
                float drain = (float) debtRecovered / maxAir * activeZone.hazardTimeSecs();
                float acc = backtankDebtDrain.getOrDefault(player.getUUID(), 0f) + drain;
                int whole = (int) acc;
                backtankDebtDrain.put(player.getUUID(), acc - whole);
                if (whole > 0) CreateCompat.drainBacktank(player, whole);
            }
            // Baseline drain: 1 unit/second for maintaining the seal while in the zone.
            if (player.getEyeInFluidType().isAir() && gameTick % 20 == 0) {
                CreateCompat.drainBacktank(player);
            }
        }

        int newDebt  = data.getAirDebt();
        int newToxin = data.getToxinLevel();

        if (newDebt != oldDebt)   PacketDistributor.sendToPlayer(player, new SyncAirDebtPayload(newDebt));
        if (newToxin != oldToxin) PacketDistributor.sendToPlayer(player,
                new SyncToxinPayload(newToxin, computeFatigueAmp(newToxin, cfg)));

        syncZoneSeverity(player, activeZone, progression, gameTick, px, pz, zones, ids);
    }

    private static void syncZoneSeverity(ServerPlayer player, ZoneDefinition activeZone,
            AtmosphereProgressionData progression, long gameTick, double px, double pz,
            List<ZoneDefinition> zones, List<String> ids) {
        // hazardIntensity = leastSevereTimeSecs / thisZoneTimeSecs.
        // Mildest registered zone → 1.0; more severe zones → proportionally higher.
        // 0.0 when safe or approaching (approaching handled separately below).
        float hazardIntensity = 0.0f;
        int zoneCeilingY = Integer.MAX_VALUE;
        int zoneFloorY   = Integer.MAX_VALUE;

        if (activeZone != null) {
            int idx = zones.indexOf(activeZone);
            if (idx >= 0) {
                int leastSevereTimeSecs = leastSevereSecs.getOrDefault(
                        player.level().dimension().location(), activeZone.hazardTimeSecs());
                hazardIntensity = (float) leastSevereTimeSecs / activeZone.hazardTimeSecs();

                zoneCeilingY = (int) Math.round(
                        progression.getEffectiveCeiling(gameTick, px, pz, ids.get(idx), activeZone.evalCeiling(gameTick, px, pz)));
                // Floor = effective ceiling of the next more-severe zone, or 32 blocks below for the deepest.
                if (idx > 0) {
                    zoneFloorY = (int) Math.round(progression.getEffectiveCeiling(
                            gameTick, px, pz, ids.get(idx - 1), zones.get(idx - 1).evalCeiling(gameTick, px, pz)));
                } else {
                    zoneFloorY = Integer.MIN_VALUE;
                }
            }
        }

        // Approaching: safe but within 15 blocks above the nearest zone ceiling.
        boolean approaching = false;
        if (hazardIntensity == 0.0f) {
            double eyeY = player.getEyeY();
            double bestGap = Double.MAX_VALUE;
            double bestCeiling = Double.NaN;
            for (int i = 0; i < zones.size(); i++) {
                double effectiveCeiling = progression.getEffectiveCeiling(
                        gameTick, px, pz, ids.get(i), zones.get(i).evalCeiling(gameTick, px, pz));
                if (Double.isNaN(effectiveCeiling)) continue;
                double gap = eyeY - effectiveCeiling;
                if (gap > 0 && gap <= 15.0 && gap < bestGap) {
                    bestGap = gap;
                    bestCeiling = effectiveCeiling;
                }
            }
            if (!Double.isNaN(bestCeiling)) {
                approaching = true;
                zoneCeilingY = (int) Math.round(bestCeiling);
                zoneFloorY   = zoneCeilingY;
            }
        }

        // State key encodes intensity at 0.01 precision + approaching flag.
        int stateKey = (int)(hazardIntensity * 100) * 2 + (approaching ? 1 : 0);
        int[] last = lastZoneState.get(player.getUUID());
        boolean changed = last == null
                || last[0] != stateKey
                || Math.abs(last[1] - zoneCeilingY) > 1
                || Math.abs(last[2] - zoneFloorY)   > 1;
        if (changed) {
            lastZoneState.put(player.getUUID(), new int[]{stateKey, zoneCeilingY, zoneFloorY});
            PacketDistributor.sendToPlayer(player, new SyncZoneSeverityPayload(hazardIntensity, approaching, zoneCeilingY, zoneFloorY));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        lastZoneState.remove(id);
        lastDivingState.remove(id);
        backtankDebtDrain.remove(id);
        particleGridRadii.remove(id);
    }

    @SubscribeEvent
    public static void onLivingBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        int debt;
        if (player instanceof ServerPlayer sp) {
            debt = sp.getData(ModAttachments.ATMOSPHERE_DATA.get()).getAirDebt();
        } else {
            debt = AtmosphereClientData.getAirDebt(player.getUUID());
        }

        if (debt > 0) {
            int ceiling  = player.getMaxAirSupply() - debt;
            int headroom = Math.max(0, ceiling - player.getAirSupply());
            event.setRefillAirAmount(Math.min(event.getRefillAirAmount(), headroom));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        lastZoneState.remove(player.getUUID()); // force re-sync on join
        syncAll(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        data.setAirDebt(0);
        data.setDrainAccumulator(0f);
        data.setRecoveryAccumulator(0f);
        data.setSuffocationTicks(0);
        AtmosphereSettings cfg = AtmosphereConfig.getSettings();
        if (cfg == null) { syncAll(player); return; }
        int retainedToxin = Math.min(data.getToxinLevel(), cfg.toxinDeathCap());
        data.setToxinLevel(retainedToxin);
        data.setToxinAccumulator(0f);
        data.setToxinRecoveryAccumulator(0f);
        syncAll(player);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;

        int fatigueAmp;
        if (!player.level().isClientSide()) {
            if (!(player instanceof ServerPlayer sp)) return;
            PlayerAtmosphereData data = sp.getData(ModAttachments.ATMOSPHERE_DATA.get());
            AtmosphereSettings cfg = AtmosphereConfig.getSettings();
            if (cfg == null) return;
            fatigueAmp = computeFatigueAmp(data.getToxinLevel(), cfg);
        } else {
            fatigueAmp = AtmosphereClientData.getMiningFatigueAmp(player.getUUID());
        }

        if (fatigueAmp >= 0) {
            event.setNewSpeed(event.getNewSpeed() * (float) Math.pow(0.3, fatigueAmp + 1));
        }
    }

    private static void syncAll(ServerPlayer player) {
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        AtmosphereSettings cfg = AtmosphereConfig.getSettings();
        int toxin = data.getToxinLevel();
        int fatigueAmp = (cfg != null) ? computeFatigueAmp(toxin, cfg) : -1;
        PacketDistributor.sendToPlayer(player, new SyncAirDebtPayload(data.getAirDebt()));
        PacketDistributor.sendToPlayer(player, new SyncToxinPayload(toxin, fatigueAmp));
    }
}
