package org.dudblockman.hostileatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.dudblockman.hostileatmosphere.compat.ProtectionLevel;
import org.dudblockman.hostileatmosphere.config.AtmosphereSettings;
import org.dudblockman.hostileatmosphere.data.PlayerAtmosphereData;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DebugCommands {

    private DebugCommands() {}

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            Function<ServerPlayer, PlayerAtmosphereData> dataGetter,
            Supplier<AtmosphereSettings> configGetter,
            Consumer<ServerPlayer> removeEffect,
            Function<ServerPlayer, ProtectionLevel> protectionGetter,
            Function<ServerPlayer, ZoneDefinition> activeZoneGetter,
            Function<ServerPlayer, Double> effectiveCeilingGetter) {

        dispatcher.register(Commands.literal("atmosphere")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException(), dataGetter, configGetter, protectionGetter, activeZoneGetter, effectiveCeilingGetter))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), dataGetter, configGetter, protectionGetter, activeZoneGetter, effectiveCeilingGetter))))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource(), ctx.getSource().getPlayerOrException(), dataGetter, removeEffect))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), dataGetter, removeEffect))))
                .then(Commands.literal("setairdebt")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 300))
                                .executes(ctx -> setAirDebt(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "amount"), dataGetter))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setAirDebt(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"), dataGetter)))))
                .then(Commands.literal("settoxin")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> setToxin(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "amount"), dataGetter, removeEffect))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setToxin(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"), dataGetter, removeEffect)))))
                .then(Commands.literal("setgrace")
                        .then(Commands.argument("days", IntegerArgumentType.integer(0, 30))
                                .executes(ctx -> setGrace(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "days"), dataGetter))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> setGrace(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "days"), dataGetter)))))
                .then(Commands.literal("config")
                        .executes(ctx -> showConfig(ctx.getSource(), configGetter)))
        );
    }

    // ------------------------------------------------------------------------------------------

    private static int status(CommandSourceStack src, ServerPlayer player,
                               Function<ServerPlayer, PlayerAtmosphereData> dataGetter,
                               Supplier<AtmosphereSettings> configGetter,
                               Function<ServerPlayer, ProtectionLevel> protectionGetter,
                               Function<ServerPlayer, ZoneDefinition> activeZoneGetter,
                               Function<ServerPlayer, Double> effectiveCeilingGetter) {
        PlayerAtmosphereData data = dataGetter.apply(player);
        AtmosphereSettings cfg = configGetter.get();
        int maxAir = player.getMaxAirSupply();
        ZoneDefinition activeZone = activeZoneGetter.apply(player);
        ProtectionLevel protection = protectionGetter.apply(player);

        String zoneStr;
        if (activeZone == null) {
            zoneStr = "§aSAFE§r";
        } else {
            double effectiveCeiling = effectiveCeilingGetter.apply(player);
            zoneStr = String.format("§cHAZARD§r (y<=%.0f, %ds, tox%ds)",
                    effectiveCeiling, activeZone.hazardTimeSecs(), activeZone.toxinBuildupSecs());
        }

        int toxin = data.getToxinLevel();
        int amp   = AtmosphereEngine.getToxinAmplifier(toxin, cfg);
        String ampStr = (amp < 0) ? "none" : "L" + (amp + 1) + " (amp " + amp + ")";

        AtmosphereEngine.Rates rates = AtmosphereEngine.computeRates(player, data, cfg, protection, activeZone);
        String airRateStr = formatRate(rates.airDebtPerSec(), data.getAirDebt(), maxAir - data.getAirDebt());
        String toxRateStr = formatRate(rates.toxinPerSec(),   toxin, 1000 - toxin);

        src.sendSuccess(() -> Component.literal(String.format(
                "[HA] %s | eyeY=%.1f | %s | protection=%s\n" +
                "  airDebt=%d/%d  ceiling=%d  air=%d  suffTicks=%d  graceTicks=%d\n" +
                "  toxin=%d/1000  effect=%s\n" +
                "  airDebt: %s | toxin: %s",
                player.getName().getString(),
                player.getEyeY(),
                zoneStr,
                protection.name(),
                data.getAirDebt(), maxAir,
                maxAir - data.getAirDebt(),
                player.getAirSupply(),
                data.getSuffocationTicks(),
                data.getGracePeriodTicks(),
                toxin,
                ampStr,
                airRateStr,
                toxRateStr
        )), false);
        return 1;
    }

    private static int reset(CommandSourceStack src, ServerPlayer player,
                              Function<ServerPlayer, PlayerAtmosphereData> dataGetter,
                              Consumer<ServerPlayer> removeEffect) {
        PlayerAtmosphereData data = dataGetter.apply(player);
        data.setAirDebt(0);
        data.setDrainAccumulator(0f);
        data.setRecoveryAccumulator(0f);
        data.setSuffocationTicks(0);
        data.setGracePeriodTicks(0);
        data.setToxinLevel(0);
        data.setToxinAccumulator(0f);
        data.setToxinRecoveryAccumulator(0f);
        player.setAirSupply(player.getMaxAirSupply());
        removeEffect.accept(player);
        src.sendSuccess(() -> Component.literal("[HA] Reset " + player.getName().getString()), false);
        return 1;
    }

    private static int setAirDebt(CommandSourceStack src, ServerPlayer player, int amount,
                                   Function<ServerPlayer, PlayerAtmosphereData> dataGetter) {
        PlayerAtmosphereData data = dataGetter.apply(player);
        data.setAirDebt(Math.min(amount, player.getMaxAirSupply()));
        data.setSuffocationTicks(0);
        int set = data.getAirDebt();
        src.sendSuccess(() -> Component.literal(
                "[HA] airDebt=" + set + " for " + player.getName().getString()), false);
        return 1;
    }

    private static int setToxin(CommandSourceStack src, ServerPlayer player, int amount,
                                 Function<ServerPlayer, PlayerAtmosphereData> dataGetter,
                                 Consumer<ServerPlayer> removeEffect) {
        PlayerAtmosphereData data = dataGetter.apply(player);
        data.setToxinLevel(Math.min(amount, 1000));
        // Remove the effect so the engine re-applies with the correct amplifier on the next tick
        removeEffect.accept(player);
        int set = data.getToxinLevel();
        src.sendSuccess(() -> Component.literal(
                "[HA] toxin=" + set + " for " + player.getName().getString()), false);
        return 1;
    }

    private static int setGrace(CommandSourceStack src, ServerPlayer player, int days,
                                 Function<ServerPlayer, PlayerAtmosphereData> dataGetter) {
        PlayerAtmosphereData data = dataGetter.apply(player);
        int ticks = days * 24000;
        data.setGracePeriodTicks(ticks);
        src.sendSuccess(() -> Component.literal(
                "[HA] grace=" + days + "d (" + ticks + "t) for " + player.getName().getString()), false);
        return 1;
    }

    private static int showConfig(CommandSourceStack src, Supplier<AtmosphereSettings> configGetter) {
        AtmosphereSettings cfg = configGetter.get();
        src.sendSuccess(() -> Component.literal(String.format(
                "[HA] Config:\n" +
                "  recovery=%ds  grace=%dd\n" +
                "  ramp: t2=%ds t3=%ds | dmg/interval: %.1f/%.2fs  %.1f/%.2fs  %.1f/%.2fs\n" +
                "  toxin: recovery=%ds  deathCap=%d\n" +
                "  thresholds: I=%d  II=%d  III=%d  IV=%d\n" +
                "  (zones are data-pack driven — see data/hostileatmosphere/zones/)",
                cfg.safeZoneRecoverySecs(), cfg.gracePeriodDays(),
                cfg.rampTier2Secs(), cfg.rampTier3Secs(),
                cfg.rampDamageTier1(), cfg.rampIntervalTier1Secs(),
                cfg.rampDamageTier2(), cfg.rampIntervalTier2Secs(),
                cfg.rampDamageTier3(), cfg.rampIntervalTier3Secs(),
                cfg.toxinRecoverySecs(), cfg.toxinDeathCap(),
                cfg.toxinThreshold1(), cfg.toxinThreshold2(), cfg.toxinThreshold3(), cfg.toxinThreshold4()
        )), false);
        return 1;
    }

    /** Formats a signed per-second rate with an ETA to the relevant limit. */
    private static String formatRate(float perSec, int current, int remaining) {
        if (perSec == 0f) return "stable";
        boolean gaining = perSec > 0;
        int etaSecs = (int) (gaining ? remaining / perSec : current / -perSec);
        return String.format("%s%.2f/s (%s)", gaining ? "+" : "", perSec, formatTime(etaSecs));
    }

    private static String formatTime(int secs) {
        if (secs <= 0) return "now";
        if (secs >= 3600) return String.format("%dh%02dm", secs / 3600, (secs % 3600) / 60);
        if (secs >= 60)   return String.format("%dm%02ds", secs / 60, secs % 60);
        return secs + "s";
    }
}
