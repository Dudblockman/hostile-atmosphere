package org.dudblockman.hostileatmosphere.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.EntityHazardSettings;
import org.dudblockman.hostileatmosphere.engine.EntityHazardEngine;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;

import java.util.EnumSet;

/**
 * Makes mobs seek higher ground when inside a hazard zone that would damage them.
 * Priority 2: yields to FloatGoal (0) and PanicGoal (1), overrides idle wandering.
 * Releases the MOVE flag immediately if no reachable escape path exists so the mob isn't frozen.
 */
public class FleeHazardGoal extends Goal {

    private static final int[] SCAN_RADII = {4, 8, 12};

    private final Mob mob;
    private int canUseCooldown = 0;
    private int continueCooldown = 0;
    private int navCooldown = 0;
    private boolean hasPath = false;

    public FleeHazardGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (canUseCooldown > 0) { canUseCooldown--; return false; }
        canUseCooldown = 20;
        if (!(mob.level() instanceof ServerLevel sl)) return false;
        EntityHazardSettings cfg = AtmosphereConfig.getEntityHazardSettings();
        if (cfg == null || !EntityHazardEngine.isDamageEnabled(mob.getType(), cfg)) return false;
        return ZoneLookup.findZoneAt(sl, mob.getX(), mob.getEyeY(), mob.getZ()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!hasPath) return false;
        if (continueCooldown > 0) { continueCooldown--; return true; }
        continueCooldown = 20;
        if (!(mob.level() instanceof ServerLevel sl)) return false;
        return ZoneLookup.findZoneAt(sl, mob.getX(), mob.getEyeY(), mob.getZ()) != null;
    }

    @Override
    public void start() {
        navCooldown = 0;
        navigate();
    }

    @Override
    public void tick() {
        if (navCooldown > 0) { navCooldown--; return; }
        navCooldown = 20;
        navigate();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        canUseCooldown = 60;
        hasPath = false;
    }

    private void navigate() {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        ZoneLookup.Located located = ZoneLookup.findLocatedZone(sl, mob.getX(), mob.getEyeY(), mob.getZ());
        if (located == null) { hasPath = false; return; }
        long tick = sl.getGameTime();
        double px = mob.getX(), pz = mob.getZ();
        double baseCeiling = located.def().evalCeiling(tick, px, pz);
        AtmosphereProgressionData prog = AtmosphereProgressionData.get(sl.getServer());
        double ceiling = prog.getEffectiveCeiling(tick, px, pz, located.id(), baseCeiling);
        int ceilInt = (int) Math.ceil(ceiling);

        int mx = mob.blockPosition().getX();
        int mz = mob.blockPosition().getZ();

        for (int r : SCAN_RADII) {
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4);
                int wx = mx + (int) Math.round(Math.cos(angle) * r);
                int wz = mz + (int) Math.round(Math.sin(angle) * r);
                int surfY = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                if (surfY > ceilInt) {
                    hasPath = mob.getNavigation().moveTo(wx + 0.5, surfY, wz + 0.5, 1.0);
                    if (hasPath) return;
                }
            }
        }
        hasPath = false;
    }
}
