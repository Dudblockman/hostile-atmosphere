package org.dudblockman.hostileatmosphere.engine;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.dudblockman.hostileatmosphere.Constants;

public final class MiasmaDamageTypes {

    public static final ResourceKey<DamageType> MIASMA =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "miasma"));

    public static final ResourceKey<DamageType> MIASMA_INTENSE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "miasma_intense"));

    private MiasmaDamageTypes() {}

    /** @param intervalSecs seconds between damage ticks; clamped to a minimum of 1 game tick. */
    public static int toIntervalTicks(float intervalSecs) {
        return Math.max(1, Math.round(intervalSecs * 20));
    }

    public static DamageSource miasma(LivingEntity entity) {
        return damageSource(entity, MIASMA);
    }

    public static DamageSource miasmaIntense(LivingEntity entity) {
        return damageSource(entity, MIASMA_INTENSE);
    }

    private static DamageSource damageSource(LivingEntity entity, ResourceKey<DamageType> key) {
        return new DamageSource(
                entity.level().registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(key));
    }
}
