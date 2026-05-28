package org.dudblockman.hostileatmosphere.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.effect.AtmosphericToxicityEffect;

public class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Constants.MOD_ID);

    public static final DeferredHolder<MobEffect, AtmosphericToxicityEffect> ATMOSPHERIC_TOXICITY =
            MOB_EFFECTS.register("atmospheric_toxicity", AtmosphericToxicityEffect::new);
}
