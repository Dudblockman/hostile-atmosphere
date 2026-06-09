package org.dudblockman.hostileatmosphere.client;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class AtmosphereParticles {

    private AtmosphereParticles() {}

    /**
     * Spawns cosmetic atmosphere particles. Always runs regardless of protection.
     *
     * <p>Each zone has a flat base rate so zones read as distinct density layers from a distance.
     * A denser band of particles is added within 4 blocks of each zone boundary, creating a
     * visible "line" in the atmosphere at every transition point.
     */
    public static void spawn(Player player, float hazardIntensity, boolean approaching,
            int zoneCeilingY, int zoneFloorY) {
        float rate = computeRate(player, hazardIntensity, approaching, zoneCeilingY, zoneFloorY);
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
    private static float computeRate(Player player, float hazardIntensity, boolean approaching,
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
}
