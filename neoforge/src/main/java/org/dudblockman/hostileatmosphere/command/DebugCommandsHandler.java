package org.dudblockman.hostileatmosphere.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.events.AtmosphereEventHandler;
import org.dudblockman.hostileatmosphere.events.ZoneLookup;
import org.dudblockman.hostileatmosphere.registry.ModAttachments;
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
        // /atmosphere particles <radius>  — 0 disables, 1-32 enables the ceiling-grid debug visualisation.
        event.getDispatcher().register(Commands.literal("atmosphere")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("particles")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                                .executes(ctx -> AtmosphereEventHandler.toggleParticleGrid(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"))))));

        ModifierCommand.register(event.getDispatcher(),
                (ctx, builder) -> {
                    builder.suggest("all");
                    ZoneLookup.getCachedZoneIds().values().forEach(ids -> ids.forEach(builder::suggest));
                    return builder.buildFuture();
                },
                (src, zoneId) -> ZoneLookup.findZoneByIdForDim(
                        src.getLevel().dimension().location(), zoneId));
        DebugCommands.register(
                event.getDispatcher(),
                player -> player.getData(ModAttachments.ATMOSPHERE_DATA.get()),
                AtmosphereConfig::getSettings,
                player -> player.removeEffect(ModEffects.ATMOSPHERIC_TOXICITY),
                CreateCompat::getProtection,
                player -> ZoneLookup.findZoneAt(
                        (ServerLevel) player.level(), player.getX(), player.getEyeY(), player.getZ()),
                player -> {
                    var sl = (ServerLevel) player.level();
                    ZoneDefinition zone = ZoneLookup.findZoneAt(
                            sl, player.getX(), player.getEyeY(), player.getZ());
                    return zone != null
                            ? ZoneLookup.getEffectiveCeiling(sl, zone, player.getX(), player.getZ())
                            : 0.0;
                }
        );
    }
}
