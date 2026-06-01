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
import org.dudblockman.hostileatmosphere.events.AtmosphereEventHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents a placed backtank block entity from charging while in the hazard zone.
 * Injects right before the airLevelTimer decrement — after super.tick(), speed == 0,
 * and waterlogged guards have all already run. Runs on both the server tick (cancels
 * the actual fill) and the client tick (cancels the charging particle spawn).
 *
 * Sable sub-levels: the block entity's block pos is local to the sub-level's plot chunk.
 * When Sable is installed the companion API transforms those local coords to world-space
 * before the zone check so the result is correct regardless of where the sub-level drifts.
 *
 * Zone check uses {@link AtmosphereEventHandler#findZoneAt(Level, double, double, double)},
 * which dispatches to full Perlin data server-side and base registry ceilings client-side.
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

        // Resolve world-space position — differs from block pos on Sable sub-levels.
        Vec3 worldPos = SableCompat.isLoaded() ? SableCompat.getWorldSpacePos(be) : null;
        double wx, wy, wz;
        if (worldPos != null) {
            wx = worldPos.x; wy = worldPos.y; wz = worldPos.z;
        } else {
            BlockPos pos = be.getBlockPos();
            wx = pos.getX(); wy = pos.getY(); wz = pos.getZ();
        }

        if (AtmosphereEventHandler.findZoneAt(level, wx, wy, wz) != null) {
            boolean full = airLevel >= BacktankUtil.maxAir(capacityEnchantLevel);
            if (!full && !level.isClientSide() && level.getRandom().nextInt(50) == 0) {
                // Particle near the local +Y top of the block. On a tilted Sable sub-level the
                // local Y offset must be transformed through the pose before being emitted.
                double px, py, pz;
                if (worldPos != null) {
                    Vec3 pp = SableCompat.getWorldSpacePos(be, 0.8);
                    px = pp.x; py = pp.y; pz = pp.z;
                } else {
                    px = wx + 0.5; py = wy + 0.8; pz = wz + 0.5;
                }
                ((ServerLevel) level).sendParticles(ParticleTypes.CRIT, px, py, pz, 4, 0.2, 0.05, 0.2, 0.1);
            }
            ci.cancel();
        }
    }
}
