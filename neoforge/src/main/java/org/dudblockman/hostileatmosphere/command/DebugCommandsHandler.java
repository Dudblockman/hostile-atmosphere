package org.dudblockman.hostileatmosphere.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.data.ModAttachments;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEventHandler;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

/**
 * NeoForge event hook that wires the platform-specific data access into the
 * platform-agnostic {@link DebugCommands#register} method.
 */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DebugCommandsHandler {

    private DebugCommandsHandler() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModifierCommand.register(event.getDispatcher());
        DebugCommands.register(
                event.getDispatcher(),
                player -> player.getData(ModAttachments.ATMOSPHERE_DATA.get()),
                AtmosphereConfig::read,
                player -> player.removeEffect(ModEffects.ATMOSPHERIC_TOXICITY),
                CreateCompat::getProtection,
                player -> {
                    long tick = player.level().getGameTime();
                    double px = player.getX(), pz = player.getZ();
                    AtmosphereProgressionData prog = AtmosphereProgressionData.get(player.getServer());
                    return AtmosphereEngine.findZone(
                            AtmosphereEventHandler.getCachedZones(),
                            AtmosphereEventHandler.getCachedZoneIds(),
                            player.getEyeY(),
                            zoneId -> prog.getLevelForZone(tick, px, pz, zoneId));
                },
                player -> AtmosphereProgressionData.get(player.getServer())
                        .getLevel(player.level().getGameTime())
        );
    }
}
