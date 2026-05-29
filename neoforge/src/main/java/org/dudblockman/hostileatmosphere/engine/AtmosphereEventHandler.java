package org.dudblockman.hostileatmosphere.engine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.compat.ProtectionLevel;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.data.AtmosphereClientData;
import org.dudblockman.hostileatmosphere.data.ModAttachments;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.network.SyncAirDebtPayload;
import org.dudblockman.hostileatmosphere.network.SyncDivingActivePayload;
import org.dudblockman.hostileatmosphere.network.SyncToxinPayload;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereEventHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        int oldDebt  = data.getAirDebt();
        int oldToxin = data.getToxinLevel();

        var cfg = AtmosphereConfig.getSettings();
        var protection = CreateCompat.getProtection(player);
        AtmosphereEngine.tick(player, data, cfg, protection);

        boolean divingActive = (protection == ProtectionLevel.SEALED || protection == ProtectionLevel.RESPIRATOR)
                && Mth.floor(player.getEyeY()) <= cfg.dangerYThreshold();

        if (divingActive != data.isDivingActive()) {
            data.setDivingActive(divingActive);
            PacketDistributor.sendToPlayer(player, new SyncDivingActivePayload(divingActive));
        }

        // Only drain backtank for atmospheric filtering when eye is in air.
        // When submerged in a drowning fluid, Create already consumes the backtank
        // for breathing — draining here too would cause double consumption.
        if (divingActive && player.getEyeInFluidType().isAir() && player.tickCount % 20 == 0) {
            CreateCompat.drainBacktank(player);
        }

        int newDebt  = data.getAirDebt();
        int newToxin = data.getToxinLevel();

        if (newDebt != oldDebt) {
            PacketDistributor.sendToPlayer(player, new SyncAirDebtPayload(newDebt));
        }
        if (newToxin != oldToxin) {
            PacketDistributor.sendToPlayer(player, new SyncToxinPayload(newToxin));
        }
    }

    /** Clamp vanilla air recovery to the debt ceiling on both server and client. */
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

    /** Sync full atmosphere state to client on login. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        syncAll(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());

        // Reset air state
        data.setAirDebt(0);
        data.setDrainAccumulator(0f);
        data.setRecoveryAccumulator(0f);
        data.setSuffocationTicks(0);

        // Retain a fraction of toxin; clear accumulators
        float retainFactor = AtmosphereConfig.getSettings().toxinRetainOnDeath();
        int retainedToxin = Math.round(data.getToxinLevel() * retainFactor);
        data.setToxinLevel(retainedToxin);
        data.setToxinAccumulator(0f);
        data.setToxinRecoveryAccumulator(0f);

        // Sync fresh state to client (engine will re-apply effect on next tick if toxin > threshold)
        syncAll(player);
    }

    // ------------------------------------------------------------------------------------------

    private static void syncAll(ServerPlayer player) {
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        PacketDistributor.sendToPlayer(player, new SyncAirDebtPayload(data.getAirDebt()));
        PacketDistributor.sendToPlayer(player, new SyncToxinPayload(data.getToxinLevel()));
    }
}
