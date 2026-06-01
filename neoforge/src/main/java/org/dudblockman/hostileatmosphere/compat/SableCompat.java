package org.dudblockman.hostileatmosphere.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class SableCompat {

    private SableCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("sable");
    }

    /**
     * Returns the world-space position of a block entity that lives on a Sable sub-level,
     * or {@code null} if the block entity is not inside any sub-level.
     * Only call this when {@link #isLoaded()} is true.
     */
    public static Vec3 getWorldSpacePos(BlockEntity be) {
        return getWorldSpacePos(be, 0.0);
    }

    /**
     * Like {@link #getWorldSpacePos(BlockEntity)} but offsets the local Y coordinate by
     * {@code localYOffset} before transforming, so the result correctly accounts for the
     * sub-level's orientation (e.g. a tilted vessel turns local +Y into a different world axis).
     */
    public static Vec3 getWorldSpacePos(BlockEntity be, double localYOffset) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(be);
        if (subLevel == null) return null;
        BlockPos pos = be.getBlockPos();
        return subLevel.logicalPose().transformPosition(
                new Vec3(pos.getX() + 0.5, pos.getY() + localYOffset, pos.getZ() + 0.5));
    }

}
