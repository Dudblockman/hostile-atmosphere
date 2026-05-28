package org.dudblockman.hostileatmosphere;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.data.ModAttachments;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

@Mod(Constants.MOD_ID)
public class HostileAtmosphere {

    public HostileAtmosphere(IEventBus modEventBus, ModContainer modContainer) {
        CommonClass.init();
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, AtmosphereConfig.SPEC);
    }
}
