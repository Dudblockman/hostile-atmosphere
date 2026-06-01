package org.dudblockman.hostileatmosphere.mixin;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.dudblockman.hostileatmosphere.compat.SableCompat;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEventHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
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
            ci.cancel();
        }
    }
}
