package org.dudblockman.hostileatmosphere.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks walkable nodes inside hazard zones as {@link PathType#DAMAGE_OTHER} (malus 8.0)
 * for mobs that would take damage there. This makes the pathfinder treat the zone as a
 * soft hazard: routes around it when alternatives exist, but traverses it if necessary.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {

    @Inject(
        method = "getPathTypeOfMob(Lnet/minecraft/world/level/pathfinder/PathfindingContext;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/PathType;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void hostileatmosphere$hazardPathType(PathfindingContext context, int x, int y, int z, Mob mob,
            CallbackInfoReturnable<PathType> cir) {
        if (mob == null) return;
        PathType current = cir.getReturnValue();
        if (current != PathType.WALKABLE && current != PathType.OPEN) return;
        if (!AtmosphereConfig.isDamageEnabled(mob.getType())) return;
        if (!(context.level() instanceof ServerLevel sl)) return;
        if (ZoneLookup.findZoneAt(sl, x + 0.5, y + mob.getBbHeight(), z + 0.5) != null) {
            cir.setReturnValue(PathType.DAMAGE_OTHER);
        }
    }
}
