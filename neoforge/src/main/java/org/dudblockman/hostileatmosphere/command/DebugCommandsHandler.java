package org.dudblockman.hostileatmosphere.command;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.data.ModAttachments;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEventHandler;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;
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
                AtmosphereConfig::getSettings,
                player -> player.removeEffect(ModEffects.ATMOSPHERIC_TOXICITY),
                CreateCompat::getProtection,
                player -> AtmosphereEventHandler.findZoneAt(
                        (ServerLevel) player.level(), player.getX(), player.getEyeY(), player.getZ()),
                player -> {
                    var sl = (ServerLevel) player.level();
                    ZoneDefinition zone = AtmosphereEventHandler.findZoneAt(
                            sl, player.getX(), player.getEyeY(), player.getZ());
                    return zone != null
                            ? AtmosphereEventHandler.getEffectiveCeiling(sl, zone, player.getX(), player.getZ())
                            : 0.0;
                }
        );
    }
}
