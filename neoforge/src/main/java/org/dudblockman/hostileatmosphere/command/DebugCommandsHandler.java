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
import org.dudblockman.hostileatmosphere.command.CeilingGridDebug;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ZoneCacheManager;
import org.dudblockman.hostileatmosphere.progression.ZoneLookup;
import org.dudblockman.hostileatmosphere.registry.ModAttachments;
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
                                .executes(ctx -> CeilingGridDebug.toggleGrid(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"))))));

        ModifierCommand.register(event.getDispatcher(),
                (ctx, builder) -> {
                    builder.suggest("all");
                    ZoneCacheManager.getCachedZoneIds().values().forEach(ids -> ids.forEach(builder::suggest));
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
                    ZoneLookup.Located loc = ZoneLookup.findLocatedZone(
                            sl, player.getX(), player.getEyeY(), player.getZ());
                    if (loc == null) return 0.0;
                    long tick = sl.getGameTime();
                    double base = loc.def().evalCeiling(tick, player.getX(), player.getZ());
                    return AtmosphereProgressionData.get(sl.getServer())
                            .getEffectiveCeiling(tick, player.getX(), player.getZ(), loc.id(), base);
                }
        );
    }
}
