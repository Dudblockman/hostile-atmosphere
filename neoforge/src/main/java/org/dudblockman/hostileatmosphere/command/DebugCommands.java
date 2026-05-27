package org.dudblockman.hostileatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.config.AtmosphereConfig;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.data.ModAttachments;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class DebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("atmosphere")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("setairdebt")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 300))
                                .executes(ctx -> setAirDebt(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "amount")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setAirDebt(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("setgrace")
                        .then(Commands.argument("days", IntegerArgumentType.integer(0, 30))
                                .executes(ctx -> setGrace(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "days")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setGrace(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "days"))))))
                .then(Commands.literal("config")
                        .executes(ctx -> showConfig(ctx.getSource())))
        );
    }

    private static int status(CommandSourceStack src, ServerPlayer player) {
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        AtmosphereSettings cfg = AtmosphereConfig.read();
        int maxAir = player.getMaxAirSupply();
        boolean inHazard = Mth.floor(player.getY()) <= cfg.dangerYThreshold();

        src.sendSuccess(() -> Component.literal(String.format(
                "[HA] %s | Y=%d | %s\n  airDebt=%d/%d  ceiling=%d  air=%d\n  suffTicks=%d  graceTicks=%d",
                player.getName().getString(),
                Mth.floor(player.getY()),
                inHazard ? "§cHAZARD§r" : "§aSAFE§r",
                data.getAirDebt(), maxAir,
                maxAir - data.getAirDebt(),
                player.getAirSupply(),
                data.getSuffocationTicks(),
                data.getGracePeriodTicks()
        )), false);
        return 1;
    }

    private static int reset(CommandSourceStack src, ServerPlayer player) {
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        data.setAirDebt(0);
        data.setSuffocationTicks(0);
        data.setGracePeriodTicks(0);
        player.setAirSupply(player.getMaxAirSupply());
        src.sendSuccess(() -> Component.literal("[HA] Reset " + player.getName().getString()), false);
        return 1;
    }

    private static int setAirDebt(CommandSourceStack src, ServerPlayer player, int amount) {
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        data.setAirDebt(Math.min(amount, player.getMaxAirSupply()));
        data.setSuffocationTicks(0);
        int set = data.getAirDebt();
        src.sendSuccess(() -> Component.literal(
                "[HA] airDebt=" + set + " for " + player.getName().getString()), false);
        return 1;
    }

    private static int setGrace(CommandSourceStack src, ServerPlayer player, int days) {
        PlayerAtmosphereData data = player.getData(ModAttachments.ATMOSPHERE_DATA.get());
        int ticks = days * 24000;
        data.setGracePeriodTicks(ticks);
        src.sendSuccess(() -> Component.literal(
                "[HA] grace=" + days + "d (" + ticks + "t) for " + player.getName().getString()), false);
        return 1;
    }

    private static int showConfig(CommandSourceStack src) {
        AtmosphereSettings cfg = AtmosphereConfig.read();
        src.sendSuccess(() -> Component.literal(String.format(
                "[HA] Config:\n  dangerY=%d  hazardTime=%ds  recovery=%ds  grace=%dd\n" +
                "  ramp tier thresholds: t2=%ds  t3=%ds\n" +
                "  dmg / interval: %.1f/%.2fs  %.1f/%.2fs  %.1f/%.2fs",
                cfg.dangerYThreshold(),
                cfg.hazardTimeSecs(), cfg.safeZoneRecoverySecs(), cfg.gracePeriodDays(),
                cfg.rampTier2Secs(), cfg.rampTier3Secs(),
                cfg.rampDamageTier1(), cfg.rampIntervalTier1Secs(),
                cfg.rampDamageTier2(), cfg.rampIntervalTier2Secs(),
                cfg.rampDamageTier3(), cfg.rampIntervalTier3Secs()
        )), false);
        return 1;
    }
}
