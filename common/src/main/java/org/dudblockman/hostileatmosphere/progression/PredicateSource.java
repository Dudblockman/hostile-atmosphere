package org.dudblockman.hostileatmosphere.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A predicate-gated ceiling pipeline. When the named datapack predicate flips, the internal
 * multiplier tweens between 0.0 and 1.0 at constant speed.
 * Output: {@code multiplier(tick) * ifPipeline + (1 - multiplier(tick)) * elsePipeline}.
 *
 * <p>The multiplier is computed deterministically from stored tween state
 * {@code (transitionTick, fromMultiplier, toMultiplier)} — identical in structure to how
 * {@link ValueSource.Constant} works. {@link #serverTick} only mutates state on a predicate
 * flip; between flips {@link #get} is a pure function of tick.
 *
 * <p>When a flip occurs mid-tween, {@code transitionTick} is set retroactively so the
 * remaining tween duration is proportional to the remaining distance — constant speed is
 * preserved regardless of where the multiplier was when the predicate changed.
 */
public final class PredicateSource implements ValueSource {

    private static final ResourceLocation TESTING_OVERRIDE_ID =
            ResourceLocation.fromNamespaceAndPath("test", "override");

    // Config — final, decoded from datapack / codec.
    private final ResourceLocation predicateId;
    private final List<ZoneDefinition.CeilingLayer> ifSources;
    private final List<ZoneDefinition.CeilingLayer> elseSources;
    private final long tweenTicks;
    private final long evaluationInterval;
    private final Predicate<ServerLevel> predicateOverride;

    // Tween state — persisted via codec; mutated only when predicate flips.
    // get(tick) is a pure function of these three fields + tick.
    private long transitionTick = 0;
    private double fromMultiplier = 0.0;
    private double toMultiplier = 0.0;
    private boolean lastResult = false;

    // Canonical — all field assignments live here.
    private PredicateSource(ResourceLocation predicateId,
                            Predicate<ServerLevel> predicateOverride,
                            List<ZoneDefinition.CeilingLayer> ifSources,
                            List<ZoneDefinition.CeilingLayer> elseSources,
                            long tweenTicks,
                            long evaluationInterval) {
        if (evaluationInterval <= 0)
            throw new IllegalArgumentException("evaluationInterval must be >= 1, got " + evaluationInterval);
        this.predicateId = predicateId;
        this.predicateOverride = predicateOverride;
        this.ifSources = List.copyOf(ifSources);
        this.elseSources = List.copyOf(elseSources);
        this.tweenTicks = tweenTicks;
        this.evaluationInterval = evaluationInterval;
    }

    // Public — registry / command path (no predicateOverride).
    public PredicateSource(ResourceLocation predicateId,
                           List<ZoneDefinition.CeilingLayer> ifSources,
                           List<ZoneDefinition.CeilingLayer> elseSources,
                           long tweenTicks,
                           long evaluationInterval) {
        this(predicateId, null, ifSources, elseSources, tweenTicks, evaluationInterval);
    }

    /**
     * Creates a {@link PredicateSource} backed by a plain Java predicate rather than a
     * registry-registered loot condition. Used in game tests where the predicate registry
     * is not populated.
     */
    public static PredicateSource withPredicate(Predicate<ServerLevel> pred,
                                                List<ZoneDefinition.CeilingLayer> ifSources,
                                                List<ZoneDefinition.CeilingLayer> elseSources,
                                                long tweenTicks, long evaluationInterval) {
        return new PredicateSource(TESTING_OVERRIDE_ID, pred, ifSources, elseSources, tweenTicks, evaluationInterval);
    }

    // Accessors for codec and describe().
    public ResourceLocation predicateId()   { return predicateId; }
    public List<ZoneDefinition.CeilingLayer> ifSources()   { return ifSources; }
    public List<ZoneDefinition.CeilingLayer> elseSources() { return elseSources; }
    public long tweenTicks()        { return tweenTicks; }
    public long evaluationInterval() { return evaluationInterval; }
    /** The multiplier at the start of the current tween (0.0 or 1.0 after a full flip). */
    public double fromMultiplier() { return fromMultiplier; }
    /** The multiplier at the end of the current tween (0.0 = else, 1.0 = if). */
    public double toMultiplier() { return toMultiplier; }

    @Override public String type() { return "predicate"; }

    @Override
    public double get(long tick) { return get(tick, 0.0, 0.0); }

    @Override
    public double get(long tick, double x, double z) {
        double m = currentMultiplier(tick);
        return m * ZoneDefinition.evalPipeline(ifSources, tick, x, z)
                + (1.0 - m) * ZoneDefinition.evalPipeline(elseSources, tick, x, z);
    }

    /**
     * Evaluates the predicate on every {@code evaluationInterval} ticks. When the result
     * differs from the last known result, sets the tween state using a retroactive
     * {@code transitionTick} so constant-speed tweening is preserved regardless of where
     * the multiplier was when the flip occurred.
     *
     * @return {@code true} if tween state changed (signals SavedData to mark dirty)
     */
    @Override
    public boolean serverTick(ServerLevel level, long tick) {
        if (tick % evaluationInterval != 0) return false;
        boolean result = testPredicate(level);
        if (result == lastResult) return false;

        double current = currentMultiplier(tick);
        if (tweenTicks <= 0) {
            transitionTick = tick;
            fromMultiplier = 0.0;
            toMultiplier = result ? 1.0 : 0.0;
        } else if (result) {
            // Flipping to true (0 → 1). Retroactive start: pretend we started the full
            // 0→1 tween 'current * tweenTicks' ticks ago so the remaining distance
            // (1 - current) is covered in (1 - current) * tweenTicks ticks.
            fromMultiplier = 0.0;
            toMultiplier = 1.0;
            transitionTick = tick - (long) (current * tweenTicks);
        } else {
            // Flipping to false (1 → 0). Retroactive start: pretend we started the full
            // 1→0 tween '(1 - current) * tweenTicks' ticks ago.
            fromMultiplier = 1.0;
            toMultiplier = 0.0;
            transitionTick = tick - (long) ((1.0 - current) * tweenTicks);
        }
        lastResult = result;
        return true;
    }

    private double currentMultiplier(long tick) {
        if (tweenTicks <= 0) return toMultiplier;
        return Mth.lerp(Mth.clamp((double) (tick - transitionTick) / tweenTicks, 0.0, 1.0), fromMultiplier, toMultiplier);
    }

    private boolean testPredicate(ServerLevel level) {
        if (predicateOverride != null) return predicateOverride.test(level);
        var condition = level.getServer().reloadableRegistries().get()
                .registryOrThrow(LootDataType.PREDICATE.registryKey())
                .get(predicateId);
        if (condition == null) return false;
        // EMPTY context: only parameter-free predicates work (weather_check, random_chance,
        // inverted, all_of, any_of). Predicates requiring entity/position/block context
        // (location_check, entity_properties, match_tool, etc.) will silently return false.
        LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
        LootContext ctx = new LootContext.Builder(params).create(Optional.empty());
        return condition.test(ctx);
    }

    // Used by the codec to reconstruct a PredicateSource with its saved tween state.
    private static PredicateSource restore(ResourceLocation predicateId,
                                           List<ZoneDefinition.CeilingLayer> ifSources,
                                           List<ZoneDefinition.CeilingLayer> elseSources,
                                           long tweenTicks, long evaluationInterval,
                                           long transitionTick, double fromMultiplier,
                                           double toMultiplier, boolean lastResult) {
        PredicateSource ps = new PredicateSource(predicateId, null, ifSources, elseSources, tweenTicks, evaluationInterval);
        ps.transitionTick = transitionTick;
        ps.fromMultiplier = fromMultiplier;
        ps.toMultiplier = toMultiplier;
        ps.lastResult = lastResult;
        return ps;
    }

    // ZoneDefinition.CeilingLayer.CODEC must be referenced lazily here: PredicateSource.<clinit>
    // runs inside ValueSource.<clinit> (via BY_TYPE), which itself was triggered by
    // ZoneDefinition$CeilingLayer.<clinit> — so CeilingLayer.CODEC is still null at this point.
    // Codec.lazyInitialized defers the supplier call until first encode/decode use.
    @SuppressWarnings("null")
    static final MapCodec<PredicateSource> CODEC = RecordCodecBuilder.<PredicateSource>mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("predicate").forGetter(PredicateSource::predicateId),
            Codec.lazyInitialized(() -> ZoneDefinition.CeilingLayer.CODEC).listOf().fieldOf("if").forGetter(PredicateSource::ifSources),
            Codec.lazyInitialized(() -> ZoneDefinition.CeilingLayer.CODEC).listOf().optionalFieldOf("else", List.of()).forGetter(PredicateSource::elseSources),
            Codec.LONG.optionalFieldOf("tweenTicks", 0L).forGetter(PredicateSource::tweenTicks),
            Codec.LONG.optionalFieldOf("evaluationInterval", 1L).forGetter(PredicateSource::evaluationInterval),
            Codec.LONG.optionalFieldOf("transitionTick", 0L).forGetter(ps -> ps.transitionTick),
            Codec.DOUBLE.optionalFieldOf("fromMultiplier", 0.0).forGetter(ps -> ps.fromMultiplier),
            Codec.DOUBLE.optionalFieldOf("toMultiplier", 0.0).forGetter(ps -> ps.toMultiplier),
            Codec.BOOL.optionalFieldOf("lastResult", false).forGetter(ps -> ps.lastResult)
    ).apply(i, PredicateSource::restore));
}
