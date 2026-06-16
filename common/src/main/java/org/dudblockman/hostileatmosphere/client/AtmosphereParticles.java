package org.dudblockman.hostileatmosphere.client;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
import org.dudblockman.hostileatmosphere.registry.ModRegistries;

import java.util.Comparator;
import java.util.List;

public final class AtmosphereParticles {

    private static final int    BOUNDARY_RANGE  = 8;
    private static final float  SAFE_HINT_RATE  = 0.5f;
    private static final double UNBOUNDED_DEPTH = 20.0;

    private static ResourceLocation clientDimCached;
    private static List<ZoneDefinition> clientZonesCached = List.of();

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
        Level level = player.level();
        RandomSource rng = player.getRandom();
        double px = player.getX(), pz = player.getZ();
        double eyeY = player.getEyeY();
        long tick = level.getGameTime();
        float ceilingOffset = AtmosphereClientData.getCeilingOffset(player.getUUID());

        List<ZoneDefinition> dimZones = zonesForDim(level);
        if (dimZones.isEmpty()) return;

        ZoneDefinition activeZone = null, floorZone = null;
        for (int i = 0; i < dimZones.size(); i++) {
            if (eyeY <= dimZones.get(i).evalCeiling(tick, px, pz) + ceilingOffset) {
                activeZone = dimZones.get(i);
                floorZone  = i > 0 ? dimZones.get(i - 1) : null;
                break;
            }
        }

        if (hazardIntensity > 0.0f && activeZone != null) {
            spawnVolumeRejection(level, rng, px, pz, hazardIntensity, activeZone, floorZone, tick, ceilingOffset);
            int borderCount = stochasticCount(rng, (float) Math.sqrt(hazardIntensity) * 2.0f);
            spawnBandAtCeiling(level, rng, px, pz, borderCount, activeZone, tick, -0.5, null, ceilingOffset);
            if (floorZone != null) {
                spawnBandAtCeiling(level, rng, px, pz, borderCount, floorZone, tick, 0.5, activeZone, 0.0f);
            }
        }

        if (activeZone == null) {
            for (ZoneDefinition zone : dimZones) {
                double gap = eyeY - maxCeilingNear(zone, tick, px, pz) - ceilingOffset;
                if (gap > 0 && gap <= BOUNDARY_RANGE) {
                    float rate  = (float) ((BOUNDARY_RANGE - gap) / BOUNDARY_RANGE) * SAFE_HINT_RATE;
                    int   count = stochasticCount(rng, rate);
                    spawnBandAtCeiling(level, rng, px, pz, count, zone, tick, -0.5, null, ceilingOffset);
                    break;
                }
            }
        }
    }

    private static List<ZoneDefinition> zonesForDim(Level level) {
        ResourceLocation dim = level.dimension().location();
        if (!dim.equals(clientDimCached)) {
            clientDimCached = dim;
            clientZonesCached = level.registryAccess().registry(ModRegistries.ZONES)
                    .map(reg -> reg.stream()
                            .filter(z -> z.dimension().equals(dim))
                            .sorted(Comparator.comparingDouble(z -> z.evalCeiling(0, 0, 0)))
                            .toList())
                    .orElse(List.of());
        }
        return clientZonesCached;
    }

    private static void spawnVolumeRejection(Level level, RandomSource rng, double px, double pz,
            float hazardIntensity, ZoneDefinition activeZone, ZoneDefinition floorZone, long tick,
            float ceilingOffset) {
        float maxRate   = hazardIntensity * 6.0f;
        int   candidates = stochasticCount(rng, maxRate);

        for (int i = 0; i < candidates; i++) {
            double[] off = sampleAnnular(rng);
            if (off == null) continue;
            double bx = px + off[0], bz = pz + off[1];

            double ceiling = activeZone.evalCeiling(tick, bx, bz) + ceilingOffset;
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
     * Scatters {@code count} particles near the ceiling plane of {@code zone}, evaluated per-point
     * so the band follows noise contours. {@code spread} controls the offset: negative places
     * particles below the ceiling, positive places them above it. {@code offset} is added to the
     * zone ceiling (e.g. a server-side progression modifier).
     *
     * <p>{@code capZone}: when non-null, any scatter position where {@code zone}'s ceiling meets or
     * exceeds {@code capZone}'s ceiling is skipped — prevents floor-border particles from escaping
     * into the clear zone when Perlin noise causes zone ceilings to cross.
     */
    private static void spawnBandAtCeiling(Level level, RandomSource rng, double px, double pz,
            int count, ZoneDefinition zone, long tick, double spread, ZoneDefinition capZone,
            float offset) {
        for (int i = 0; i < count; i++) {
            double[] off = sampleAnnular(rng);
            if (off == null) continue;
            double bx    = px + off[0], bz = pz + off[1];
            double ceilY = zone.evalCeiling(tick, bx, bz) + offset;
            if (capZone != null && ceilY >= capZone.evalCeiling(tick, bx, bz)) continue;
            level.addParticle(ParticleTypes.MYCELIUM, bx, ceilY + rng.nextDouble() * spread, bz, 0.0, 0.0, 0.0);
        }
    }

    /** Samples a random XZ offset in the annulus r∈[2,12] centred on the player, or null if rejected. */
    private static double[] sampleAnnular(RandomSource rng) {
        double offX  = rng.nextDouble() * 24.0 - 12.0;
        double offZ  = rng.nextDouble() * 24.0 - 12.0;
        double dist2 = offX * offX + offZ * offZ;
        return (dist2 >= 4.0 && dist2 <= 144.0) ? new double[]{offX, offZ} : null;
    }

    private static double maxCeilingNear(ZoneDefinition zone, long tick, double px, double pz) {
        double c = zone.evalCeiling(tick, px, pz);
        c = Math.max(c, zone.evalCeiling(tick, px + 5, pz));
        c = Math.max(c, zone.evalCeiling(tick, px - 5, pz));
        c = Math.max(c, zone.evalCeiling(tick, px, pz + 5));
        c = Math.max(c, zone.evalCeiling(tick, px, pz - 5));
        return c;
    }

    private static int stochasticCount(RandomSource rng, float rate) {
        int count = (int) rate;
        if (rng.nextFloat() < (rate - count)) count++;
        return count;
    }
}
