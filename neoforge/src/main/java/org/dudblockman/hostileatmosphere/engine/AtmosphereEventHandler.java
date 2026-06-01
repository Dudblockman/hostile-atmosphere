package org.dudblockman.hostileatmosphere.engine;

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
import net.neoforged.neoforge.network.PacketDistributor;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.compat.ProtectionLevel;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.data.AtmosphereClientData;
import org.dudblockman.hostileatmosphere.data.ModAttachments;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.network.SyncAirDebtPayload;
import org.dudblockman.hostileatmosphere.network.SyncDivingActivePayload;
import org.dudblockman.hostileatmosphere.network.SyncToxinPayload;
import org.dudblockman.hostileatmosphere.network.SyncZoneSeverityPayload;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereEventHandler {

    private record ZonePair(String id, ZoneDefinition def) {}

    /**
     * Zones per dimension, each list sorted ascending by yCeiling (lowest = most severe first).
     * Rebuilt on datapack reload.
     */
    private static volatile Map<ResourceLocation, List<ZoneDefinition>> zonesByDim = Map.of();
    /** Zone IDs per dimension, parallel to {@link #zonesByDim} values. */
    private static volatile Map<ResourceLocation, List<String>> zoneIdsByDim = Map.of();

    /**
     * Tracks last sent zone state per player to avoid redundant packets.
     * int[0] = severity*2 + (approaching?1:0), int[1] = zoneCeilingY, int[2] = zoneFloorY.
     * Ceiling and floor are included so Perlin-noise-driven boundary shifts trigger a resend.
     */
    private static final Map<UUID, int[]> lastZoneState = new HashMap<>();

    public static Map<ResourceLocation, List<ZoneDefinition>> getCachedZones()  { return zonesByDim; }
    public static Map<ResourceLocation, List<String>>         getCachedZoneIds() { return zoneIdsByDim; }

    private static List<ZoneDefinition> dimZones(ResourceLocation dim) {
        return zonesByDim.getOrDefault(dim, List.of());
    }

    private static List<String> dimIds(ResourceLocation dim) {
        return zoneIdsByDim.getOrDefault(dim, List.of());
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
                        .sorted(Comparator.comparingDouble(zone -> zone.evalCeiling(tick, x, z)))
                        .filter(zone -> y <= zone.evalCeiling(tick, x, z))
                        .findFirst())
                .orElse(null);
    }

    /** Convenience lookup for non-player callers (e.g. block entity mixins). */
    public static ZoneDefinition findZoneAt(ServerLevel level, double x, double y, double z) {
        ResourceLocation dim = level.dimension().location();
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        long gameTick = level.getGameTime();
        return AtmosphereEngine.findZone(dimZones(dim), dimIds(dim), gameTick, x, y, z,
                zoneId -> prog.getLevelForZone(gameTick, x, z, zoneId));
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
        return zone.evalCeiling(gameTick, x, z)
                + AtmosphereProgressionData.get(level.getServer()).getLevelForZone(gameTick, x, z, zoneId);
    }

    private static int computeFatigueAmp(int toxinLevel, AtmosphereSettings cfg) {
        return Math.max(-1, AtmosphereEngine.getToxinAmplifier(toxinLevel, cfg) - 1);
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
        zonesByDim  = Map.copyOf(newDefs);
        zoneIdsByDim = Map.copyOf(newIds);

        AtmosphereProgressionData.get(server).ensureKey0();
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
                zoneId -> progression.getLevelForZone(gameTick, px, pz, zoneId));

        // Creative and spectator skip hazard logic but still get zone sync so particles are visible.
        if (player.isCreative() || player.isSpectator()) {
            syncZoneSeverity(player, activeZone, progression, gameTick, px, pz, zones, ids);
            return;
        }

        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        int oldDebt  = data.getAirDebt();
        int oldToxin = data.getToxinLevel();

        var cfg        = AtmosphereConfig.getSettings();
        var protection = CreateCompat.getProtection(player);

        AtmosphereEngine.tick(player, data, cfg, protection, activeZone);

        boolean divingActive = (protection == ProtectionLevel.SEALED || protection == ProtectionLevel.RESPIRATOR)
                && activeZone != null;

        if (divingActive != data.isDivingActive()) {
            data.setDivingActive(divingActive);
            PacketDistributor.sendToPlayer(player, new SyncDivingActivePayload(divingActive));
        }

        if (divingActive && player.getEyeInFluidType().isAir() && player.tickCount % 20 == 0) {
            CreateCompat.drainBacktank(player);
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
                int leastSevereTimeSecs = zones.stream()
                        .mapToInt(ZoneDefinition::hazardTimeSecs)
                        .max()
                        .orElse(activeZone.hazardTimeSecs());
                hazardIntensity = (float) leastSevereTimeSecs / activeZone.hazardTimeSecs();

                zoneCeilingY = (int) Math.round(
                        activeZone.evalCeiling(gameTick, px, pz) + progression.getLevelForZone(gameTick, px, pz, ids.get(idx)));
                // Floor = effective ceiling of the next more-severe zone, or 32 blocks below for the deepest.
                if (idx > 0) {
                    zoneFloorY = (int) Math.round(zones.get(idx - 1).evalCeiling(gameTick, px, pz)
                            + progression.getLevelForZone(gameTick, px, pz, ids.get(idx - 1)));
                } else {
                    zoneFloorY = zoneCeilingY - 32;
                }
            }
        }

        // Approaching: safe but within 15 blocks above the nearest zone ceiling.
        boolean approaching = false;
        if (hazardIntensity == 0.0f) {
            double eyeY = player.getEyeY();
            for (int i = 0; i < zones.size(); i++) {
                double effectiveCeiling = zones.get(i).evalCeiling(gameTick, px, pz)
                        + progression.getLevelForZone(gameTick, px, pz, ids.get(i));
                if (eyeY > effectiveCeiling && eyeY - effectiveCeiling <= 15.0) {
                    approaching = true;
                    zoneCeilingY = (int) Math.round(effectiveCeiling);
                    zoneFloorY   = zoneCeilingY;
                    break;
                }
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
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastZoneState.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        data.setAirDebt(0);
        data.setDrainAccumulator(0f);
        data.setRecoveryAccumulator(0f);
        data.setSuffocationTicks(0);
        int retainedToxin = Math.min(data.getToxinLevel(), AtmosphereConfig.getSettings().toxinDeathCap());
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
