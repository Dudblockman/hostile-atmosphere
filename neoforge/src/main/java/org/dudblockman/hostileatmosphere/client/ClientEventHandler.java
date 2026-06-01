package org.dudblockman.hostileatmosphere.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerHeartTypeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.data.AtmosphereClientData;
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
            spawnAtmosphericParticles(localPlayer, intensity, near, ceilingY, floorY);
        }
        AtmosphereClientData.setForceHeartWiggle(wiggle);
    }

    /**
     * Spawns cosmetic atmosphere particles. Always runs regardless of protection.
     *
     * <p>Each zone has a flat base rate so zones read as distinct density layers from a distance.
     * A denser band of particles is added within 4 blocks of each zone boundary, creating a
     * visible "line" in the atmosphere at every transition point.
     */
    private static void spawnAtmosphericParticles(Player player, float hazardIntensity, boolean approaching,
            int zoneCeilingY, int zoneFloorY) {
        float rate = computeParticleRate(player, hazardIntensity, approaching, zoneCeilingY, zoneFloorY);
        if (rate <= 0.0f) return;

        Level level = player.level();
        RandomSource rng = player.getRandom();
        double px = player.getX();
        double py = player.getY() + player.getEyeHeight() * 0.5;
        double pz = player.getZ();

        int count = (int) rate;
        if (rng.nextFloat() < (rate - count)) count++;

        if (approaching && zoneCeilingY != Integer.MAX_VALUE) {
            // Approaching: motes anchored just below the zone ceiling.
            for (int i = 0; i < count; i++) {
                double r     = 3.0 + rng.nextDouble() * 8.0;
                double theta = rng.nextDouble() * 2.0 * Math.PI;
                level.addParticle(ParticleTypes.MYCELIUM,
                        px + r * Math.cos(theta),
                        zoneCeilingY - rng.nextDouble() * 4.0,
                        pz + r * Math.sin(theta),
                        0.0, 0.0, 0.0);
            }
        } else {
            // In-zone: sphere around the player, clamped below the zone ceiling.
            for (int i = 0; i < count; i++) {
                double r     = 6.0 + rng.nextDouble() * 6.0;
                double phi   = Math.acos(1.0 - 2.0 * rng.nextDouble());
                double theta = rng.nextDouble() * 2.0 * Math.PI;
                double spawnY = py + r * Math.cos(phi);
                if (spawnY > zoneCeilingY) continue;
                level.addParticle(ParticleTypes.MYCELIUM,
                        px + r * Math.sin(phi) * Math.cos(theta),
                        spawnY,
                        pz + r * Math.sin(phi) * Math.sin(theta),
                        0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Particle rate derived entirely from the zone's actual hazard data, not a hardcoded
     * per-zone lookup. {@code hazardIntensity = leastSevereTimeSecs / thisZoneTimeSecs},
     * so the mildest zone is always 1.0 and any data-pack zone gets proportional density.
     *
     * <ul>
     *   <li>Base rate: {@code hazardIntensity × 2} — mildest zone ≈ 2/tick</li>
     *   <li>Boundary band (±4 blocks of zone edge): additional {@code hazardIntensity × 2}</li>
     *   <li>Approaching (above ceiling): fixed ~0.5/tick hint regardless of zone</li>
     * </ul>
     */
    private static float computeParticleRate(Player player, float hazardIntensity, boolean approaching,
            int zoneCeilingY, int zoneFloorY) {
        if (hazardIntensity <= 0.0f) {
            return (approaching && zoneCeilingY != Integer.MAX_VALUE) ? 0.5f : 0.0f;
        }

        float base = hazardIntensity * 2.0f;

        // Boundary bands: proportionally denser within 4 blocks of either zone edge.
        double eyeY = player.getEyeY();
        if (zoneCeilingY != Integer.MAX_VALUE && eyeY >= zoneCeilingY - 4) base += hazardIntensity * 2.0f;
        if (zoneFloorY   != Integer.MAX_VALUE && eyeY <= zoneFloorY   + 4) base += hazardIntensity * 2.0f;

        return base;
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
