package org.dudblockman.hostileatmosphere;

import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.data.ModAttachments;
import org.dudblockman.hostileatmosphere.registry.ModAttributes;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

@Mod(Constants.MOD_ID)
public class HostileAtmosphere {

    public HostileAtmosphere(IEventBus modEventBus, ModContainer modContainer) {
        CommonClass.init();
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(HostileAtmosphere::onAttributeModification);
        modContainer.registerConfig(ModConfig.Type.SERVER, AtmosphereConfig.SPEC);
    }

    private static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, ModAttributes.AIR_DRAIN_RATE);
        event.add(EntityType.PLAYER, ModAttributes.TOXIN_RATE);
    }
}
