package org.dudblockman.hostileatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier.Operation;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ValueSource;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ModifierCommand {

    private ModifierCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atmosphere")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("modifier")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clear(ctx.getSource())))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("key", IntegerArgumentType.integer())
                                        .executes(ctx -> remove(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "key")))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("key", IntegerArgumentType.integer())
                                        // target: "all" or a zone id like "hazy"
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .then(opNode("add",   Operation.ADD))
                                                .then(opNode("cap",   Operation.CLAMP_MAX))
                                                .then(opNode("floor", Operation.CLAMP_MIN)))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> opNode(String opLiteral, Operation op) {
        return Commands.literal(opLiteral)
                // constant <value> [tweenTicks]
                .then(Commands.literal("constant")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(ctx -> addModifier(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "key"),
                                        StringArgumentType.getString(ctx, "target"), op,
                                        new ValueSource.Constant(DoubleArgumentType.getDouble(ctx, "value"), 0, 0)))
                                .then(Commands.argument("tweenTicks", LongArgumentType.longArg(0))
                                        .executes(ctx -> {
                                            long now = ctx.getSource().getLevel().getGameTime();
                                            return addModifier(ctx.getSource(),
                                                    IntegerArgumentType.getInteger(ctx, "key"),
                                                    StringArgumentType.getString(ctx, "target"), op,
                                                    new ValueSource.Constant(
                                                            DoubleArgumentType.getDouble(ctx, "value"),
                                                            LongArgumentType.getLong(ctx, "tweenTicks"), now));
                                        }))))
                // sin <amplitude> <period> <phase> [tweenTicks]
                .then(Commands.literal("sin")
                        .then(Commands.argument("amplitude", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("period", LongArgumentType.longArg(1))
                                        .then(Commands.argument("phase", LongArgumentType.longArg())
                                                .executes(ctx -> addModifier(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "target"), op,
                                                        new ValueSource.SinWave(
                                                                DoubleArgumentType.getDouble(ctx, "amplitude"),
                                                                LongArgumentType.getLong(ctx, "period"),
                                                                (double) LongArgumentType.getLong(ctx, "phase"),
                                                                0, 0)))
                                                .then(Commands.argument("tweenTicks", LongArgumentType.longArg(0))
                                                        .executes(ctx -> {
                                                            long now = ctx.getSource().getLevel().getGameTime();
                                                            return addModifier(ctx.getSource(),
                                                                    IntegerArgumentType.getInteger(ctx, "key"),
                                                                    StringArgumentType.getString(ctx, "target"), op,
                                                                    new ValueSource.SinWave(
                                                                            DoubleArgumentType.getDouble(ctx, "amplitude"),
                                                                            LongArgumentType.getLong(ctx, "period"),
                                                                            (double) LongArgumentType.getLong(ctx, "phase"),
                                                                            LongArgumentType.getLong(ctx, "tweenTicks"), now));
                                                        }))))))
                // perlin <xzScale> <amplitude> <timeTicks> [tweenTicks]
                .then(Commands.literal("perlin")
                        .then(Commands.argument("xzScale", DoubleArgumentType.doubleArg(0.0001))
                                .then(Commands.argument("amplitude", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("timeTicks", LongArgumentType.longArg(1))
                                                .executes(ctx -> {
                                                    long seed = ThreadLocalRandom.current().nextLong();
                                                    return addModifier(ctx.getSource(),
                                                            IntegerArgumentType.getInteger(ctx, "key"),
                                                            StringArgumentType.getString(ctx, "target"), op,
                                                            new ValueSource.Perlin(
                                                                    DoubleArgumentType.getDouble(ctx, "xzScale"),
                                                                    DoubleArgumentType.getDouble(ctx, "amplitude"),
                                                                    LongArgumentType.getLong(ctx, "timeTicks"),
                                                                    0, 0, seed));
                                                })
                                                .then(Commands.argument("tweenTicks", LongArgumentType.longArg(0))
                                                        .executes(ctx -> {
                                                            long now  = ctx.getSource().getLevel().getGameTime();
                                                            long seed = ThreadLocalRandom.current().nextLong();
                                                            return addModifier(ctx.getSource(),
                                                                    IntegerArgumentType.getInteger(ctx, "key"),
                                                                    StringArgumentType.getString(ctx, "target"), op,
                                                                    new ValueSource.Perlin(
                                                                            DoubleArgumentType.getDouble(ctx, "xzScale"),
                                                                            DoubleArgumentType.getDouble(ctx, "amplitude"),
                                                                            LongArgumentType.getLong(ctx, "timeTicks"),
                                                                            LongArgumentType.getLong(ctx, "tweenTicks"),
                                                                            now, seed));
                                                        }))))));
    }

    // ------------------------------------------------------------------------------------------

    private static int list(CommandSourceStack src) {
        AtmosphereProgressionData data = AtmosphereProgressionData.get(src.getServer());
        long tick = src.getLevel().getGameTime();
        Map<Integer, AtmosphereModifier> mods = data.getModifiers();
        double level = data.getLevel(tick);
        StringBuilder sb = new StringBuilder(
                String.format("[HA] global=%.2f  modifiers(%d):", level, mods.size()));
        mods.forEach((key, mod) -> sb.append(String.format("\n  [%d] %-5s %-6s | %s | now=%.2f",
                key, mod.operation().getSerializedName(),
                mod.target().equals("all") ? "(all)" : "(" + mod.target() + ")",
                describe(mod.source()), mod.getCurrentValue(tick))));
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return mods.size();
    }

    private static int clear(CommandSourceStack src) {
        AtmosphereProgressionData data = AtmosphereProgressionData.get(src.getServer());
        int count = data.getModifiers().size();
        data.clearModifiers();
        src.sendSuccess(() -> Component.literal("[HA] Cleared " + count + " modifier(s)"), false);
        return count;
    }

    private static int remove(CommandSourceStack src, int key) {
        AtmosphereProgressionData.get(src.getServer()).removeModifier(key);
        src.sendSuccess(() -> Component.literal("[HA] Removed modifier [" + key + "]"), false);
        return 1;
    }

    private static int addModifier(CommandSourceStack src, int key, String target,
                                    Operation op, ValueSource source) {
        AtmosphereProgressionData.get(src.getServer()).setModifier(key, op, source, target);
        src.sendSuccess(() -> Component.literal(
                String.format("[HA] [%d] %s (%s) %s", key, op.getSerializedName(), target, describe(source))), false);
        return 1;
    }

    // ------------------------------------------------------------------------------------------

    private static String describe(ValueSource source) {
        if (source instanceof ValueSource.Constant c)
            return String.format("const(%.4g tween=%dt)", c.value(), c.tweenTicks());
        if (source instanceof ValueSource.SinWave s)
            return String.format("sin(amp=%.4g period=%.0f phase=%.0f tween=%dt)",
                    s.amplitude(), s.periodTicks(), s.phaseTicks(), s.tweenTicks());
        if (source instanceof ValueSource.Perlin p)
            return String.format("perlin(xz=%.4g amp=%.4g time=%.0f tween=%dt)",
                    p.xzScale(), p.amplitude(), p.timeTicks(), p.tweenTicks());
        return source.type();
    }
}
