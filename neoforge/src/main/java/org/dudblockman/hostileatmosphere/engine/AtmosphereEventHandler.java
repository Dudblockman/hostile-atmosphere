package org.dudblockman.hostileatmosphere.engine;

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
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereEventHandler {

    private record ZonePair(String id, ZoneDefinition def) {}

    /** Sorted ascending by yCeiling (lowest = most severe first). Rebuilt on datapack reload. */
    private static volatile List<ZoneDefinition> cachedZoneDefs = List.of();
    /** Zone IDs parallel to {@link #cachedZoneDefs} — e.g. "hazy", "toxic", "lethal". */
    private static volatile List<String> cachedZoneIds = List.of();

    public static List<ZoneDefinition> getCachedZones() { return cachedZoneDefs; }
    public static List<String> getCachedZoneIds()       { return cachedZoneIds; }

    /** Convenience lookup for non-player callers (e.g. block entity mixins). */
    public static ZoneDefinition findZoneAt(ServerLevel level, double x, double y, double z) {
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(level.getServer());
        long gameTick = level.getGameTime();
        return AtmosphereEngine.findZone(cachedZoneDefs, cachedZoneIds, y,
                zoneId -> prog.getLevelForZone(gameTick, x, z, zoneId));
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
        List<ZonePair> pairs = new ArrayList<>();
        registry.entrySet().forEach(e ->
                pairs.add(new ZonePair(e.getKey().location().getPath(), e.getValue())));
        pairs.sort(Comparator.comparingInt(p -> p.def().yCeiling()));
        cachedZoneDefs = pairs.stream().map(ZonePair::def).toList();
        cachedZoneIds  = pairs.stream().map(ZonePair::id).toList();

        AtmosphereProgressionData.get(server).ensureKey0();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        int oldDebt  = data.getAirDebt();
        int oldToxin = data.getToxinLevel();

        var cfg        = AtmosphereConfig.getSettings();
        var protection = CreateCompat.getProtection(player);

        long   gameTick  = player.level().getGameTime();
        double px = player.getX(), pz = player.getZ();
        AtmosphereProgressionData progression = AtmosphereProgressionData.get(player.getServer());

        ZoneDefinition activeZone = AtmosphereEngine.findZone(
                cachedZoneDefs, cachedZoneIds, player.getEyeY(),
                zoneId -> progression.getLevelForZone(gameTick, px, pz, zoneId));

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
