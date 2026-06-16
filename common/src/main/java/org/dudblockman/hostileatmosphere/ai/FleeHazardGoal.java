package org.dudblockman.hostileatmosphere.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;

import java.util.EnumSet;
import java.util.OptionalDouble;
import java.util.function.ToIntFunction;

/**
 * Makes mobs seek higher ground when inside a hazard zone that would damage them.
 * Priority 2: yields to FloatGoal (0) and PanicGoal (1), overrides idle wandering.
 * Releases the MOVE flag on the next goal-selector evaluation when no reachable escape path exists.
 */
public class FleeHazardGoal extends Goal {

    private static final int[] SCAN_RADII = {4, 8, 12, 16, 20};

    private final Mob mob;
    private final ToIntFunction<LivingEntity> airDebtFn;
    private int canUseCooldown    = 0;
    private int navCooldown       = 0;
    private int zoneCheckCooldown = 0;
    private boolean hasPath       = false;

    public FleeHazardGoal(Mob mob, ToIntFunction<LivingEntity> airDebtFn) {
        this.mob = mob;
        this.airDebtFn = airDebtFn;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (canUseCooldown > 0) { canUseCooldown--; return false; }
        ServerLevel sl = serverLevel();
        if (sl == null) return false;
        AtmosphereSettings.EntityHazardSettings cfg = AtmosphereSettings.getEntityHazardSettings();
        if (cfg == null || !cfg.isDamageEnabled(mob.getType())) return false;
        if (ZoneLookup.findZoneAt(sl, mob.getX(), mob.getEyeY(), mob.getZ()) == null) {
            canUseCooldown = 20;
            return false;
        }
        canUseCooldown = 20;
        return airDebtFn.applyAsInt(mob) > mob.getMaxAirSupply() * 0.33f;
    }

    @Override
    public boolean canContinueToUse() {
        if (!hasPath) return false;
        if (mob.getNavigation().isDone()) {
            ServerLevel sl = serverLevel();
            if (sl == null) return false;
            if (ZoneLookup.findZoneAt(sl, mob.getX(), mob.getEyeY(), mob.getZ()) == null) return false;
            navigate();
            navCooldown = 10;
            return hasPath;
        }
        if (zoneCheckCooldown > 0) { zoneCheckCooldown--; return true; }
        zoneCheckCooldown = 20;
        ServerLevel sl = serverLevel();
        if (sl == null) return false;
        return ZoneLookup.findZoneAt(sl, mob.getX(), mob.getEyeY(), mob.getZ()) != null;
    }

    @Override
    public void start() {
        navCooldown = 0;
        zoneCheckCooldown = 0;
        navigate();
    }

    @Override
    public void tick() {
        if (navCooldown > 0) { navCooldown--; return; }
        navCooldown = 10;
        navigate();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        canUseCooldown = 60;
        hasPath = false;
    }

    private ServerLevel serverLevel() {
        return mob.level() instanceof ServerLevel sl ? sl : null;
    }

    private void navigate() {
        ServerLevel sl = serverLevel();
        if (sl == null) return;
        OptionalDouble effectiveCeiling = ZoneLookup.getEffectiveCeilingAt(sl, mob.getX(), mob.getEyeY(), mob.getZ());
        if (effectiveCeiling.isEmpty()) { hasPath = false; return; }
        int ceilInt = (int) Math.ceil(effectiveCeiling.getAsDouble());

        int mx = mob.blockPosition().getX();
        int mz = mob.blockPosition().getZ();

        for (int r : SCAN_RADII) {
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4);
                int wx = mx + (int) Math.round(Math.cos(angle) * r);
                int wz = mz + (int) Math.round(Math.sin(angle) * r);
                int surfY = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                if (surfY > ceilInt) {
                    hasPath = mob.getNavigation().moveTo(wx + 0.5, surfY, wz + 0.5, 1.5);
                    if (hasPath) return;
                }
            }
        }
        hasPath = false;
    }
}
