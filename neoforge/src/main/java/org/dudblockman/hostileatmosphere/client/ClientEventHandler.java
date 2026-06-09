package org.dudblockman.hostileatmosphere.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerHeartTypeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.client.AtmosphereClientData;
import org.dudblockman.hostileatmosphere.client.AtmosphereParticles;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

/**
 * Client-only GAME-bus event handlers ({@code Dist.CLIENT}).
 *
 * <table>
 *   <caption>Atmospheric Toxicity heart-bar visuals</caption>
 *   <tr><th>Amp</th><th>Level</th><th>Visual</th><th>Damage</th></tr>
 *   <tr><td>0</td><td>I</td>  <td>Poisoned yellow-green</td>                              <td>—</td></tr>
 *   <tr><td>1</td><td>II</td> <td>Poisoned yellow-green + wiggle</td>                     <td>—</td></tr>
 *   <tr><td>2</td><td>III</td><td>Poisoned yellow-green + wiggle + Poison damage</td>     <td>1 HP / 25 t</td></tr>
 *   <tr><td>3</td><td>IV</td> <td>Withered dark grey</td>                                 <td>1 HP / 20 t</td></tr>
 * </table>
 */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        var entity = event.getEntity();
        if (!entity.level().isClientSide()) return;
        // Creative and spectator skip hazard effects but still see particles — it's the environment.
        if (!entity.isCreative() && !entity.isSpectator()) {
            // Visual backtank air
            if (AtmosphereClientData.isDivingActive(entity.getUUID())) {
                CreateCompat.updateVisualAir(entity);
            } else {
                CreateCompat.clearVisualAir(entity);
            }
        }

        Player localPlayer = Minecraft.getInstance().player;
        boolean wiggle = false;
        if (localPlayer != null && localPlayer.getUUID().equals(entity.getUUID())) {
            if (!localPlayer.isCreative() && !localPlayer.isSpectator()) {
                MobEffectInstance inst = localPlayer.getEffect(ModEffects.ATMOSPHERIC_TOXICITY);
                int amp = (inst != null) ? inst.getAmplifier() : -1;
                wiggle = (amp == 1 || amp == 2)
                        && localPlayer.getHealth() + localPlayer.getAbsorptionAmount() > 4.0F;
            }

            float intensity = AtmosphereClientData.getHazardIntensity(localPlayer.getUUID());
            boolean near    = AtmosphereClientData.isApproachingHazard(localPlayer.getUUID());
            int ceilingY    = AtmosphereClientData.getZoneCeilingY(localPlayer.getUUID());
            int floorY      = AtmosphereClientData.getZoneFloorY(localPlayer.getUUID());
            AtmosphereParticles.spawn(localPlayer, intensity, near, ceilingY, floorY);
            AtmosphereClientData.setForceHeartWiggle(wiggle);
        }
    }

    @SubscribeEvent
    public static void onPlayerHeartType(PlayerHeartTypeEvent event) {
        if (event.getType() != Gui.HeartType.NORMAL) return; // preserve vanilla Wither/Absorbing/etc.

        Player player = event.getEntity();
        MobEffectInstance instance = player.getEffect(ModEffects.ATMOSPHERIC_TOXICITY);
        if (instance == null) return;

        int amp = instance.getAmplifier();
        if (amp >= 3) {
            event.setType(Gui.HeartType.WITHERED);  // Level IV — Wither-style damage
        } else {
            event.setType(Gui.HeartType.POISIONED); // Levels I–III — poisoned yellow-green
        }
    }
}
