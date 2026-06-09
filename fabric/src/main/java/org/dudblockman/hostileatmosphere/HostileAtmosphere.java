package org.dudblockman.hostileatmosphere;

import net.fabricmc.api.ModInitializer;

public class HostileAtmosphere implements ModInitializer {

    @Override
    public void onInitialize() {
        Constants.LOG.info("{} initializing", Constants.MOD_NAME);
    }
}
