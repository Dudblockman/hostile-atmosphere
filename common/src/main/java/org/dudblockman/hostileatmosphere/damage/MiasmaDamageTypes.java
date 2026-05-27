package org.dudblockman.hostileatmosphere.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import org.dudblockman.hostileatmosphere.Constants;

public class MiasmaDamageTypes {

    /** Tiers 1 & 2 — bypasses armor and enchantments. */
    public static final ResourceKey<DamageType> MIASMA = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "miasma")
    );

    /** Tier 3 — additionally bypasses Resistance and absorption. */
    public static final ResourceKey<DamageType> MIASMA_INTENSE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "miasma_intense")
    );
}
