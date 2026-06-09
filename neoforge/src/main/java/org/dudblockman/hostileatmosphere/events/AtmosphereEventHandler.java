package org.dudblockman.hostileatmosphere.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.command.CeilingGridDebug;
import org.dudblockman.hostileatmosphere.engine.ProtectionLevel;
import org.dudblockman.hostileatmosphere.network.SyncAirDebtPayload;
import org.dudblockman.hostileatmosphere.network.SyncDivingActivePayload;
import org.dudblockman.hostileatmosphere.network.SyncToxinPayload;
import org.dudblockman.hostileatmosphere.network.SyncZoneSeverityPayload;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;
import org.dudblockman.hostileatmosphere.registry.ModAttachments;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereEventHandler {

    /**
     * Per-player session state tracked to avoid redundant packets and for retroactive drain accounting.
     * lastZoneState: null = not yet sent; int[0] = severity*2+(approaching?1:0), int[1]=ceilingY, int[2]=floorY.
     * Ceiling and floor are included so Perlin-noise-driven boundary shifts trigger a resend.
     * backtankDebtDrain: fractional backtank units owed from retroactive debt-recovery drain, pending consumption.
     */
    private static final Map<UUID, PlayerSessionState> sessionStates = new ConcurrentHashMap<>();

    private static PlayerSessionState session(UUID id) {
        return sessionStates.computeIfAbsent(id, k -> new PlayerSessionState());
    }

    private static final class PlayerSessionState {
        int[] lastZoneState;
        boolean lastDivingState;
        float backtankDebtDrain;
    }

    private static int computeFatigueAmp(int toxinLevel, AtmosphereSettings cfg) {
        return Math.max(-1, AtmosphereEngine.getToxinAmplifier(toxinLevel, cfg) - 1);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long tick = overworld.getGameTime();
        // PredicateSource evaluates predicates against the overworld regardless of zone dimension
        // because serverTick() needs zone-to-dimension mapping that isn't plumbed through yet.
        AtmosphereProgressionData.get(server).serverTick(overworld, tick);

        ZoneLookup.tickAllZoneSources(server, tick);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ZoneLookup.rebuildZoneCache(event.getServer());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ZoneLookup.rebuildZoneCache(event.getPlayerList().getServer());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;

        ResourceLocation dim = player.level().dimension().location();
        List<ZoneDefinition> zones = ZoneLookup.getZonesForDim(dim);
        List<String>         ids   = ZoneLookup.getIdsForDim(dim);

        long   gameTick  = player.level().getGameTime();
        double px = player.getX(), pz = player.getZ();
        AtmosphereProgressionData progression = AtmosphereProgressionData.get(player.getServer());

        ZoneDefinition activeZone = AtmosphereEngine.findZone(
                zones, ids, gameTick, px, player.getEyeY(), pz,
                (zoneId, base) -> progression.getEffectiveCeiling(gameTick, px, pz, zoneId, base));

        CeilingGridDebug.renderForPlayer(player, zones, ids, gameTick, px, pz, progression);

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

        PlayerSessionState state = session(player.getUUID());
        if (divingActive != state.lastDivingState) {
            state.lastDivingState = divingActive;
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
                float acc = state.backtankDebtDrain + drain;
                int whole = (int) acc;
                state.backtankDebtDrain = acc - whole;
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
                int leastSevereTimeSecs = ZoneLookup.getLeastSevereSecsForDim(
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
        int stateKey = Math.round(hazardIntensity * 100) * 2 + (approaching ? 1 : 0);
        PlayerSessionState zoneSession = session(player.getUUID());
        int[] last = zoneSession.lastZoneState;
        boolean changed = last == null
                || last[0] != stateKey
                || Math.abs(last[1] - zoneCeilingY) > 1
                || Math.abs(last[2] - zoneFloorY)   > 1;
        if (changed) {
            zoneSession.lastZoneState = new int[]{stateKey, zoneCeilingY, zoneFloorY};
            PacketDistributor.sendToPlayer(player, new SyncZoneSeverityPayload(hazardIntensity, approaching, zoneCeilingY, zoneFloorY));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        sessionStates.remove(id);
        CeilingGridDebug.onPlayerLoggedOut(id);
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
        sessionStates.remove(player.getUUID()); // reset session state on join to force re-sync
        syncAll(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        AtmosphereSettings cfg = AtmosphereConfig.getSettings();
        int retainedToxin = (cfg != null) ? Math.min(data.getToxinLevel(), cfg.toxinDeathCap()) : 0;
        data.reset(retainedToxin);
        sessionStates.remove(player.getUUID());
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
        PacketDistributor.sendToPlayer(player, new SyncDivingActivePayload(false));
    }
}
