package org.dudblockman.hostileatmosphere.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.dudblockman.hostileatmosphere.Constants;

public class ModAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, Constants.MOD_ID);

    public static final DeferredHolder<Attribute, RangedAttribute> AIR_DRAIN_RATE =
            ATTRIBUTES.register("air_drain_rate",
                    () -> (RangedAttribute) new RangedAttribute(
                            "attribute.hostileatmosphere.air_drain_rate", 1.0, 0.0, 4.0)
                            .setSyncable(true));

    public static final DeferredHolder<Attribute, RangedAttribute> TOXIN_RATE =
            ATTRIBUTES.register("toxin_rate",
                    () -> (RangedAttribute) new RangedAttribute(
                            "attribute.hostileatmosphere.toxin_rate", 1.0, 0.0, 4.0)
                            .setSyncable(true));
}
