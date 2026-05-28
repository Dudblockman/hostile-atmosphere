package org.dudblockman.hostileatmosphere.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import org.dudblockman.hostileatmosphere.Constants;

public class MiasmaDamageTypes {
    public static final ResourceKey<DamageType> MIASMA = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "miasma")
    );

    public static final ResourceKey<DamageType> MIASMA_INTENSE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "miasma_intense")
    );
}
