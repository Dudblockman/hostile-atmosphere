package org.dudblockman.hostileatmosphere.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
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
import org.dudblockman.hostileatmosphere.progression.ZoneCacheManager;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;
import org.dudblockman.hostileatmosphere.registry.ModAttachments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereEventHandler {

    /**
     * Per-player session state tracked to avoid redundant packets and for retroactive drain accounting.
     * lastHazardKey / lastOffsetKey: MIN_VALUE = not yet sent (forces first-tick sync).
     * backtankDebtDrain: fractional backtank units owed from retroactive debt-recovery drain, pending consumption.
     */
    private static final Map<UUID, PlayerSessionState> sessionStates = new HashMap<>();

    private static PlayerSessionState session(UUID id) {
        return sessionStates.computeIfAbsent(id, k -> new PlayerSessionState());
    }

    private static final class PlayerSessionState {
        int lastHazardKey      = Integer.MIN_VALUE;
        int lastOffsetKey      = Integer.MIN_VALUE;
        int lastFloorOffsetKey = Integer.MIN_VALUE;
        boolean lastDivingState;
        float backtankDebtDrain;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;

        ServerLevel serverLevel = (ServerLevel) player.level();
        ResourceLocation dim = serverLevel.dimension().location();
        List<ZoneDefinition> zones = ZoneCacheManager.getZonesForDim(dim);
        List<String>         ids   = ZoneCacheManager.getIdsForDim(dim);
        long   gameTick = serverLevel.getGameTime();
        double px = player.getX(), pz = player.getZ();
        AtmosphereProgressionData progression = AtmosphereProgressionData.get(player.getServer());

        ZoneLookup.ZoneAndCeiling zoneResult = ZoneLookup.findZoneAndFloor(serverLevel, px, player.getEyeY(), pz);
        ZoneDefinition activeZone = zoneResult != null ? zoneResult.zone().def() : null;

        CeilingGridDebug.renderForPlayer(player, zones, ids, gameTick, px, pz, progression);

        // Creative and spectator skip hazard logic but still get zone sync so particles are visible.
        if (player.isCreative() || player.isSpectator()) {
            syncZoneSeverity(player, zoneResult, gameTick, px, pz);
            return;
        }

        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        int maxAir   = player.getMaxAirSupply();
        int oldDebt  = data.getAirDebt();
        int oldToxin = data.getToxinLevel();

        AtmosphereSettings cfg = AtmosphereSettings.getSettings();
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
                new SyncToxinPayload(newToxin, AtmosphereEngine.computeFatigueAmp(newToxin, cfg)));

        syncZoneSeverity(player, zoneResult, gameTick, px, pz);
    }

    private static void syncZoneSeverity(ServerPlayer player, ZoneLookup.ZoneAndCeiling zoneResult,
            long gameTick, double px, double pz) {
        float hazardIntensity    = 0.0f;
        float ceilingOffset      = 0.0f;
        float floorCeilingOffset = 0.0f;
        if (zoneResult != null) {
            int leastSevereTimeSecs = ZoneCacheManager.getLeastSevereSecsForDim(
                    player.level().dimension().location(), zoneResult.zone().def().hazardTimeSecs());
            hazardIntensity = (float) leastSevereTimeSecs / zoneResult.zone().def().hazardTimeSecs();
            double base = zoneResult.zone().def().evalCeiling(gameTick, px, pz);
            ceilingOffset = (float) (zoneResult.ceiling() - base);
            if (zoneResult.floor() > 0) {
                floorCeilingOffset = (float) (zoneResult.floor() - zoneResult.baseFloor());
            }
        }

        int hazardKey      = Math.round(hazardIntensity * 100);
        int offsetKey      = Math.round(ceilingOffset); // 1-block precision; sub-block oscillations are imperceptible
        int floorOffsetKey = Math.round(floorCeilingOffset);
        PlayerSessionState zoneSession = session(player.getUUID());
        if (zoneSession.lastHazardKey != hazardKey || zoneSession.lastOffsetKey != offsetKey
                || zoneSession.lastFloorOffsetKey != floorOffsetKey) {
            zoneSession.lastHazardKey      = hazardKey;
            zoneSession.lastOffsetKey      = offsetKey;
            zoneSession.lastFloorOffsetKey = floorOffsetKey;
            PacketDistributor.sendToPlayer(player,
                    new SyncZoneSeverityPayload(hazardIntensity, ceilingOffset, floorCeilingOffset));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        sessionStates.remove(id);
        CeilingGridDebug.onPlayerLoggedOut(id);
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
        AtmosphereSettings cfg = AtmosphereSettings.getSettings();
        int retainedToxin = (cfg != null) ? Math.min(data.getToxinLevel(), cfg.toxinDeathCap()) : 0;
        data.reset(retainedToxin);
        sessionStates.remove(player.getUUID());
        syncAll(player);
    }

    private static void syncAll(ServerPlayer player) {
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        AtmosphereSettings cfg = AtmosphereSettings.getSettings();
        int toxin = data.getToxinLevel();
        int fatigueAmp = (cfg != null) ? AtmosphereEngine.computeFatigueAmp(toxin, cfg) : -1;
        PacketDistributor.sendToPlayer(player, new SyncAirDebtPayload(data.getAirDebt()));
        PacketDistributor.sendToPlayer(player, new SyncToxinPayload(toxin, fatigueAmp));
        PacketDistributor.sendToPlayer(player, new SyncDivingActivePayload(false));
    }
}
