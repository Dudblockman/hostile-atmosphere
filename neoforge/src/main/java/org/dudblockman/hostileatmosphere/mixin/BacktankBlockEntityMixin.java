package org.dudblockman.hostileatmosphere.mixin;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents a placed backtank block entity from charging while in the hazard zone,
 * mirroring the existing waterlogged early-exit in BacktankBlockEntity.tick().
 *
 * Injects right before the airLevelTimer check — after super.tick(), speed == 0,
 * and waterlogged guards have all already run.
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
        if (be.getBlockPos().getY() <= AtmosphereConfig.DANGER_Y_THRESHOLD.get()) {
            ci.cancel();
        }
    }
}
