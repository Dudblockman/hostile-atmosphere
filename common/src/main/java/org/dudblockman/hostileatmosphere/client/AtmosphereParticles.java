package org.dudblockman.hostileatmosphere.client;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;

import java.util.Comparator;
import java.util.List;

public final class AtmosphereParticles {

    private static final int    BOUNDARY_RANGE  = 8;
    private static final float  BAND_WIDTH      = 4.0f;
    private static final float  SAFE_HINT_RATE  = 0.5f;
    private static final double UNBOUNDED_DEPTH = 20.0;

    private AtmosphereParticles() {}

    /**
     * In-zone: rejection-sampled volume. Candidates are drawn at the maximum possible rate;
     * each is accepted proportionally to its local rate, which is the base zone intensity plus
     * proximity contributions from the nearest ceiling and floor. Both ceiling and floor are
     * evaluated at each candidate's X/Z position via the data-pack pipeline, so boundary bands
     * follow Perlin noise and sine-wave contours rather than a flat plane.
     *
     * <p>Safe approach: a hint band just below the nearest ceiling, also contour-following.
     */
    public static void spawn(Player player, float hazardIntensity) {
        Level level      = player.level();
        RandomSource rng = player.getRandom();
        double px        = player.getX();
        double pz        = player.getZ();
        double eyeY      = player.getEyeY();
        long   tick      = level.getGameTime();

        List<ZoneDefinition> dimZones = zonesForDim(level, px, pz, tick);
        if (dimZones.isEmpty()) return;

        ZoneDefinition activeZone = null;
        ZoneDefinition floorZone  = null;
        for (int i = 0; i < dimZones.size(); i++) {
            if (eyeY <= dimZones.get(i).evalCeiling(tick, px, pz)) {
                activeZone = dimZones.get(i);
                floorZone  = i > 0 ? dimZones.get(i - 1) : null;
                break;
            }
        }

        if (hazardIntensity > 0.0f && activeZone != null) {
            spawnVolumeRejection(level, rng, px, pz, hazardIntensity, activeZone, floorZone, tick);
        } else if (hazardIntensity == 0.0f) {
            for (ZoneDefinition zone : dimZones) {
                double gap = eyeY - zone.evalCeiling(tick, px, pz);
                if (gap > 0 && gap <= BOUNDARY_RANGE) {
                    spawnApproachBand(level, rng, px, pz, eyeY, zone, tick);
                    break;
                }
            }
        }
    }

    /**
     * Returns zones for the player's dimension sorted ascending by ceiling at (px, pz),
     * matching the server-side ordering used for zone selection.
     */
    private static List<ZoneDefinition> zonesForDim(Level level, double px, double pz, long tick) {
        var dim = level.dimension().location();
        return level.registryAccess().registry(ModRegistries.ZONES)
                .map(reg -> reg.stream()
                        .filter(z -> z.dimension().equals(dim))
                        .sorted(Comparator.comparingDouble(z -> z.evalCeiling(tick, px, pz)))
                        .toList())
                .orElse(List.of());
    }

    private static void spawnVolumeRejection(Level level, RandomSource rng, double px, double pz,
            float hazardIntensity, ZoneDefinition activeZone, ZoneDefinition floorZone, long tick) {
        float maxRate  = hazardIntensity * 4.0f;
        int candidates = stochasticCount(rng, maxRate);

        for (int i = 0; i < candidates; i++) {
            double r     = 2.0 + Math.sqrt(rng.nextDouble()) * 10.0;
            double theta = rng.nextDouble() * 2.0 * Math.PI;
            double bx    = px + r * Math.cos(theta);
            double bz    = pz + r * Math.sin(theta);

            double ceiling = activeZone.evalCeiling(tick, bx, bz);
            double floor   = floorZone != null ? floorZone.evalCeiling(tick, bx, bz) : Double.NEGATIVE_INFINITY;

            double bottom = Double.isInfinite(floor) ? ceiling - UNBOUNDED_DEPTH * 2 : floor;
            double height = ceiling - bottom;
            if (height <= 0) continue;

            double y         = bottom + rng.nextDouble() * height;
            float  localRate = localVolumeRate(y, hazardIntensity, ceiling, floor);
            if (rng.nextFloat() < localRate / maxRate) {
                level.addParticle(ParticleTypes.MYCELIUM, bx, y, bz, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Base rate plus linear proximity boosts within {@code BOUNDARY_RANGE} blocks of each known edge. */
    private static float localVolumeRate(double y, float hazardIntensity, double ceiling, double floor) {
        float rate = hazardIntensity * 2.0f;
        double distToCeiling = ceiling - y;
        if (distToCeiling >= 0 && distToCeiling < BOUNDARY_RANGE)
            rate += (float) (1.0 - distToCeiling / BOUNDARY_RANGE) * hazardIntensity * 2.0f;
        if (!Double.isInfinite(floor)) {
            double distToFloor = y - floor;
            if (distToFloor >= 0 && distToFloor < BOUNDARY_RANGE)
                rate += (float) (1.0 - distToFloor / BOUNDARY_RANGE) * hazardIntensity * 2.0f;
        }
        return rate;
    }

    /**
     * Hint band shown when safe and approaching a zone ceiling. Each particle is placed at the
     * actual ceiling height for its X/Z so the band follows the zone's noise contour.
     */
    private static void spawnApproachBand(Level level, RandomSource rng, double px, double pz,
            double eyeY, ZoneDefinition zone, long tick) {
        double gap  = eyeY - zone.evalCeiling(tick, px, pz);
        float  rate = (float) ((BOUNDARY_RANGE - gap) / BOUNDARY_RANGE) * SAFE_HINT_RATE;
        int   count = stochasticCount(rng, rate);
        for (int i = 0; i < count; i++) {
            double r     = 3.0 + rng.nextDouble() * 8.0;
            double theta = rng.nextDouble() * 2.0 * Math.PI;
            double bx    = px + r * Math.cos(theta);
            double bz    = pz + r * Math.sin(theta);
            level.addParticle(ParticleTypes.MYCELIUM,
                    bx,
                    zone.evalCeiling(tick, bx, bz) - rng.nextDouble() * BAND_WIDTH,
                    bz,
                    0.0, 0.0, 0.0);
        }
    }

    private static int stochasticCount(RandomSource rng, float rate) {
        int count = (int) rate;
        if (rng.nextFloat() < (rate - count)) count++;
        return count;
    }
}
