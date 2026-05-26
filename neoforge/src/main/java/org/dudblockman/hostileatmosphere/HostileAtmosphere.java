package org.dudblockman.hostileatmosphere;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class HostileAtmosphere {

    public HostileAtmosphere(IEventBus eventBus) {
        CommonClass.init();
    }
}
