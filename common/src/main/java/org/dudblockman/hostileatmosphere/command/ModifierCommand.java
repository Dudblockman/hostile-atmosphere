package org.dudblockman.hostileatmosphere.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier.Operation;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.PredicateSource;
import org.dudblockman.hostileatmosphere.progression.ValueSource;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

public final class ModifierCommand {

    private ModifierCommand() {}

    /**
     * Command structure:
     *
     * <pre>
     *   /atmosphere modifier list                         — list ALL modifiers across all targets
     *   /atmosphere modifier &lt;target&gt; list               — list modifiers for one target
     *   /atmosphere modifier &lt;target&gt; clear              — clear modifiers for one target
     *   /atmosphere modifier &lt;target&gt; remove &lt;key&gt;       — remove modifier at &lt;key&gt;
     *   /atmosphere modifier &lt;target&gt; add &lt;key&gt; add|cap|floor constant|sin|perlin ...
     * </pre>
     *
     * {@code target} is "all" (applies to every zone in this dimension) or a zone id like "lethal".
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                SuggestionProvider<CommandSourceStack> targetSuggestions,
                                @Nullable BiFunction<CommandSourceStack, String, ZoneDefinition> zoneLookup) {
        dispatcher.register(Commands.literal("atmosphere")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("modifier")
                        .then(Commands.literal("list")
                                .executes(ctx -> listAll(ctx.getSource())))
                        // Reset: clear all runtime modifiers and restore the zero-offset base
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx.getSource())))
                        // Target-scoped subcommands
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests(targetSuggestions)
                                .then(Commands.literal("list")
                                        .executes(ctx -> list(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "target"),
                                                zoneLookup)))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> clear(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "target"))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("key", IntegerArgumentType.integer())
                                                .executes(ctx -> remove(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "key")))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("key", IntegerArgumentType.integer())
                                                .then(opNode("offset", Operation.ADD))
                                                .then(opNode("cap",   Operation.CLAMP_MAX))
                                                .then(opNode("floor", Operation.CLAMP_MIN)))))));
    }

    /** Builds the operation subtree. Reads "target" and "key" from ancestor arguments in context. */
    private static ArgumentBuilder<CommandSourceStack, ?> opNode(String opLiteral, Operation op) {
        return Commands.literal(opLiteral)
                .then(Commands.literal("constant")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                .executes(ctx -> execConstant(ctx, op, 0))
                                .then(Commands.argument("tweenTicks", LongArgumentType.longArg(0))
                                        .executes(ctx -> execConstant(ctx, op,
                                                LongArgumentType.getLong(ctx, "tweenTicks"))))))
                .then(Commands.literal("sin")
                        .then(Commands.argument("amplitude", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("period", LongArgumentType.longArg(1))
                                        .then(Commands.argument("phase", LongArgumentType.longArg())
                                                .executes(ctx -> execSin(ctx, op, 0))
                                                .then(Commands.argument("tweenTicks", LongArgumentType.longArg(0))
                                                        .executes(ctx -> execSin(ctx, op,
                                                                LongArgumentType.getLong(ctx, "tweenTicks"))))))))
                .then(Commands.literal("perlin")
                        .then(Commands.argument("xzScale", DoubleArgumentType.doubleArg(0.0001))
                                .then(Commands.argument("amplitude", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("timeTicks", LongArgumentType.longArg(1))
                                                .executes(ctx -> execPerlin(ctx, op, 0))
                                                .then(Commands.argument("tweenTicks", LongArgumentType.longArg(0))
                                                        .executes(ctx -> execPerlin(ctx, op,
                                                                LongArgumentType.getLong(ctx, "tweenTicks"))))))));
    }

    private static int execConstant(CommandContext<CommandSourceStack> ctx, Operation op, long tweenTicks) {
        long now = ctx.getSource().getLevel().getGameTime();
        int key = IntegerArgumentType.getInteger(ctx, "key");
        double toValue = DoubleArgumentType.getDouble(ctx, "value");
        double fromValue;
        if (tweenTicks > 0) {
            AtmosphereModifier prev = AtmosphereProgressionData.get(ctx.getSource().getServer()).getModifiers().get(key);
            fromValue = prev != null ? prev.source().get(now) : 0.0;
        } else {
            fromValue = 0.0;
        }
        return addModifier(ctx.getSource(), key, StringArgumentType.getString(ctx, "target"), op,
                new ValueSource.Constant(fromValue, toValue, tweenTicks, tweenTicks > 0 ? now : 0));
    }

    private static int execSin(CommandContext<CommandSourceStack> ctx, Operation op, long tweenTicks) {
        long now = ctx.getSource().getLevel().getGameTime();
        int key = IntegerArgumentType.getInteger(ctx, "key");
        double newAmp    = DoubleArgumentType.getDouble(ctx, "amplitude");
        double newPeriod = LongArgumentType.getLong(ctx, "period");
        double newPhase  = (double) LongArgumentType.getLong(ctx, "phase");
        double fromAmp, fromPeriod, fromPhase;
        if (tweenTicks > 0) {
            AtmosphereModifier prev = AtmosphereProgressionData.get(ctx.getSource().getServer()).getModifiers().get(key);
            if (prev != null && prev.source() instanceof ValueSource.SinWave s) {
                fromAmp    = s.amplitude();
                fromPeriod = s.periodTicks();
                fromPhase  = s.phaseTicks();
            } else {
                fromAmp = 0.0; fromPeriod = 0.0; fromPhase = 0.0;
            }
        } else {
            fromAmp = 0.0; fromPeriod = 0.0; fromPhase = 0.0;
        }
        return addModifier(ctx.getSource(), key, StringArgumentType.getString(ctx, "target"), op,
                new ValueSource.SinWave(fromAmp, newAmp, fromPeriod, newPeriod, fromPhase, newPhase,
                        tweenTicks, tweenTicks > 0 ? now : 0));
    }

    private static int execPerlin(CommandContext<CommandSourceStack> ctx, Operation op, long tweenTicks) {
        long now  = ctx.getSource().getLevel().getGameTime();
        long seed = ThreadLocalRandom.current().nextLong();
        int  key  = IntegerArgumentType.getInteger(ctx, "key");
        double fromAmp;
        if (tweenTicks > 0) {
            AtmosphereModifier prev = AtmosphereProgressionData.get(ctx.getSource().getServer()).getModifiers().get(key);
            fromAmp = (prev != null && prev.source() instanceof ValueSource.Perlin p) ? p.amplitude() : 0.0;
        } else {
            fromAmp = 0.0;
        }
        return addModifier(ctx.getSource(), key, StringArgumentType.getString(ctx, "target"), op,
                new ValueSource.Perlin(DoubleArgumentType.getDouble(ctx, "xzScale"),
                        fromAmp, DoubleArgumentType.getDouble(ctx, "amplitude"),
                        LongArgumentType.getLong(ctx, "timeTicks"),
                        tweenTicks, tweenTicks > 0 ? now : 0, seed));
    }

    private static int listAll(CommandSourceStack src) {
        AtmosphereProgressionData data = AtmosphereProgressionData.get(src.getServer());
        long tick = src.getLevel().getGameTime();
        Map<Integer, AtmosphereModifier> mods = data.getModifiers();
        double level = data.getLevelForZone(tick, 0, 0, "all");
        StringBuilder sb = new StringBuilder(
                String.format("[HA] global=%.2f  modifiers(%d):", level, mods.size()));
        mods.forEach((key, mod) -> sb.append(String.format("\n  [%d] %-5s (%s) | %s | now=%.2f",
                key, mod.operation().getSerializedName(), mod.target(),
                describe(mod.source()), mod.source().get(tick))));
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return mods.size();
    }

    private static int list(CommandSourceStack src, String target,
                             @Nullable BiFunction<CommandSourceStack, String, ZoneDefinition> zoneLookup) {
        AtmosphereProgressionData data = AtmosphereProgressionData.get(src.getServer());
        long tick = src.getLevel().getGameTime();
        Map<Integer, AtmosphereModifier> mods = data.getModifiers();
        StringBuilder sb = new StringBuilder(String.format("[HA] modifiers for (%s):", target));

        ZoneDefinition zoneDef = zoneLookup != null ? zoneLookup.apply(src, target) : null;
        if (zoneDef != null) {
            for (ZoneDefinition.CeilingLayer layer : zoneDef.ceiling()) {
                sb.append(String.format("\n  [dp] %-6s | %s | now=%.2f",
                        layer.operation().getSerializedName(),
                        describe(layer.source()),
                        layer.source().get(tick)));
            }
        }

        int count = 0;
        for (Map.Entry<Integer, AtmosphereModifier> entry : mods.entrySet()) {
            AtmosphereModifier mod = entry.getValue();
            String t = mod.target();
            if (!t.equals("all") && !t.equals(target)) continue;
            sb.append(String.format("\n  [%d] %-6s (%s) | %s | now=%.2f",
                    entry.getKey(), mod.operation().getSerializedName(), t,
                    describe(mod.source()), mod.source().get(tick)));
            count++;
        }

        if (zoneDef == null && count == 0) sb.append("\n  (none)");
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return count;
    }

    private static int reset(CommandSourceStack src) {
        AtmosphereProgressionData data = AtmosphereProgressionData.get(src.getServer());
        int removed = data.getModifiers().size();
        data.clearModifiers();
        src.sendSuccess(() -> Component.literal(
                String.format("[HA] Reset %d modifier(s). Zones restored to data-pack defaults.", removed)), false);
        return removed;
    }

    private static int clear(CommandSourceStack src, String target) {
        int removed = AtmosphereProgressionData.get(src.getServer()).clearModifiersForTarget(target);
        src.sendSuccess(() -> Component.literal(
                String.format("[HA] Cleared %d modifier(s) for (%s)", removed, target)), false);
        return removed;
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
                String.format("[HA] [%d] (%s) %s %s", key, target, op.getSerializedName(), describe(source))), false);
        return 1;
    }

    private static String describe(ValueSource source) {
        if (source instanceof ValueSource.Constant c)
            return c.tweenTicks() > 0
                    ? String.format("const(%.4g←%.4g tween=%dt)", c.value(), c.fromValue(), c.tweenTicks())
                    : String.format("const(%.4g)", c.value());
        if (source instanceof ValueSource.SinWave s) {
            if (s.tweenTicks() > 0) {
                boolean hasPeriodTween = s.fromPeriodTicks() > 0 && s.fromPeriodTicks() != s.periodTicks();
                return hasPeriodTween
                        ? String.format("sin(amp=%.4g←%.4g period=%.0f←%.0f phase=%.0f←%.0f tween=%dt)",
                                s.amplitude(), s.fromAmplitude(),
                                s.periodTicks(), s.fromPeriodTicks(),
                                s.phaseTicks(), s.fromPhaseTicks(),
                                s.tweenTicks())
                        : String.format("sin(amp=%.4g←%.4g period=%.0f phase=%.0f tween=%dt)",
                                s.amplitude(), s.fromAmplitude(),
                                s.periodTicks(), s.phaseTicks(), s.tweenTicks());
            }
            return String.format("sin(amp=%.4g period=%.0f phase=%.0f)",
                    s.amplitude(), s.periodTicks(), s.phaseTicks());
        }
        if (source instanceof ValueSource.Perlin p) {
            return p.tweenTicks() > 0
                    ? String.format("perlin(xz=%.4g amp=%.4g←%.4g time=%.0f tween=%dt)",
                            p.xzScale(), p.amplitude(), p.fromAmplitude(), p.timeTicks(), p.tweenTicks())
                    : String.format("perlin(xz=%.4g amp=%.4g time=%.0f)",
                            p.xzScale(), p.amplitude(), p.timeTicks());
        }
        if (source instanceof PredicateSource ps) {
            double from = ps.fromMultiplier(), to = ps.toMultiplier();
            String mul = (from == to)
                    ? String.format("%.2f", to)
                    : String.format("%.2f→%.2f", from, to);
            return ps.tweenTicks() > 0
                    ? String.format("predicate(%s mul=%s tween=%dt eval=%dt)",
                            ps.predicateId(), mul, ps.tweenTicks(), ps.evaluationInterval())
                    : String.format("predicate(%s mul=%s eval=%dt)",
                            ps.predicateId(), mul, ps.evaluationInterval());
        }
        return source.type();
    }
}
