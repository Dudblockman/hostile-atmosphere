package org.dudblockman.hostileatmosphere.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.compat.CreateCompat;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.progression.ZoneCacheManager;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;
import org.dudblockman.hostileatmosphere.registry.ModAttachments;
import org.dudblockman.hostileatmosphere.registry.ModEffects;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DebugCommandsHandler {

    private DebugCommandsHandler() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("atmosphere")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("particles")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                                .executes(ctx -> CeilingGridDebug.toggleGrid(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"))))));

        ModifierCommand.register(event.getDispatcher(),
                (ctx, builder) -> {
                    builder.suggest("all");
                    ZoneCacheManager.getAllZoneIdsByDim().forEach(ids -> ids.forEach(builder::suggest));
                    return builder.buildFuture();
                },
                (src, zoneId) -> ZoneLookup.findZoneByIdForDim(
                        src.getLevel().dimension().location(), zoneId));

        var platform = new DebugCommands.Platform(
                player -> player.getData(ModAttachments.ATMOSPHERE_DATA.get()),
                AtmosphereConfig::getSettings,
                player -> player.removeEffect(ModEffects.ATMOSPHERIC_TOXICITY),
                CreateCompat::getProtection
        );
        DebugCommands.register(event.getDispatcher(), platform);
    }
}
