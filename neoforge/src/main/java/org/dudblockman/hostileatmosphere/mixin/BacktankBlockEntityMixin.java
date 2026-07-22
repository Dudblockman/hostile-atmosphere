package org.dudblockman.hostileatmosphere.mixin;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.dudblockman.hostileatmosphere.compat.SableCompat;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops a placed backtank from charging in the hazard zone.
 * Sable sub-levels: block pos is local; SableCompat transforms to world-space before the zone check.
 */
@Pseudo
@Mixin(value = BacktankBlockEntity.class, remap = false)
public class BacktankBlockEntityMixin {

    @Shadow public int airLevel;
    @Shadow private int capacityEnchantLevel;

    @Inject(
        method = "tick",
        at = @At(
            value = "FIELD",
            target = "Lcom/simibubi/create/content/equipment/armor/BacktankBlockEntity;airLevelTimer:I",
            opcode = Opcodes.GETFIELD,
            ordinal = 0
        ),
        cancellable = true
    )
    private void hostileatmosphere$interruptInHazard(CallbackInfo ci) {
        BlockEntity be = (BlockEntity) (Object) this;
        Level level = be.getLevel();
        if (level == null) return;

        Vec3 worldPos = resolveWorldPos(be, 0.0, 0.0);

        if (ZoneLookup.findZoneAt(level, worldPos.x, worldPos.y, worldPos.z) != null) {
            boolean full = airLevel >= BacktankUtil.maxAir(capacityEnchantLevel);
            if (!full && !level.isClientSide() && level.getRandom().nextInt(50) == 0) {
                hostileatmosphere$emitBlockedParticle((ServerLevel) level, be);
            }
            ci.cancel();
        }
    }

    // Particle near the local +Y top of the block. On a tilted Sable sub-level the
    // local Y offset must be transformed through the pose before being emitted.
    private void hostileatmosphere$emitBlockedParticle(ServerLevel level, BlockEntity be) {
        Vec3 pos = resolveWorldPos(be, 0.8, 0.5);
        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 4, 0.2, 0.05, 0.2, 0.1);
    }

    /**
     * Resolves world-space position for {@code be}, transformed through the containing
     * Sable sub-level's pose when present. Falls back to the raw block pos (offset by
     * {@code fallbackXZOffset} in x/z, to center on the block when the caller needs that)
     * when Sable isn't loaded or {@code be} isn't inside a sub-level.
     */
    private Vec3 resolveWorldPos(BlockEntity be, double localYOffset, double fallbackXZOffset) {
        if (SableCompat.isLoaded()) {
            Vec3 pos = SableCompat.getWorldSpacePos(be, localYOffset);
            if (pos != null) return pos;
        }
        BlockPos pos = be.getBlockPos();
        return new Vec3(pos.getX() + fallbackXZOffset, pos.getY() + localYOffset, pos.getZ() + fallbackXZOffset);
    }
}
