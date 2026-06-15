package org.dudblockman.hostileatmosphere.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.client.AtmosphereClientData;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.registry.ModAttachments;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class AtmosphereVanillaOverrideHandler {

    /**
     * Prevents vanilla (and other mods) from pushing air supply above the debt ceiling.
     * Fires on both sides; client reads from AtmosphereClientData.
     *
     * Uses consume-based clamping rather than refill-capping so that other mods
     * (e.g. Create's backtank) can still register a refill intent — necessary for
     * their overlay display logic — while the net air change stays at the ceiling.
     */
    @SubscribeEvent
    public static void onLivingBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        int debt;
        if (player.level().isClientSide()) {
            debt = AtmosphereClientData.getAirDebt(player.getUUID());
        } else {
            if (!(player instanceof ServerPlayer sp)) return;
            debt = sp.getData(ModAttachments.ATMOSPHERE_DATA.get()).getAirDebt();
        }

        if (debt > 0) {
            int ceiling   = Math.max(0, player.getMaxAirSupply() - debt);
            int projected = player.getAirSupply() + event.getRefillAirAmount() - event.getConsumeAirAmount();
            if (projected > ceiling) {
                event.setConsumeAirAmount(event.getConsumeAirAmount() + (projected - ceiling));
            }
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;

        int fatigueAmp;
        if (!player.level().isClientSide()) {
            if (!(player instanceof ServerPlayer sp)) return;
            PlayerAtmosphereData data = sp.getData(ModAttachments.ATMOSPHERE_DATA.get());
            AtmosphereSettings cfg = AtmosphereSettings.getSettings();
            if (cfg == null) return;
            fatigueAmp = Math.max(-1, AtmosphereEngine.getToxinAmplifier(data.getToxinLevel(), cfg) - 1);
        } else {
            fatigueAmp = AtmosphereClientData.getMiningFatigueAmp(player.getUUID());
        }

        if (fatigueAmp >= 0) {
            event.setNewSpeed(event.getNewSpeed() * (float) Math.pow(0.3, fatigueAmp + 1));
        }
    }
}
