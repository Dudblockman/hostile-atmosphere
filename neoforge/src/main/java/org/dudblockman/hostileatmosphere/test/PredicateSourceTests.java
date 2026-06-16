package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier;
import org.dudblockman.hostileatmosphere.progression.PredicateSource;
import org.dudblockman.hostileatmosphere.progression.ValueSource;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import java.util.List;
import java.util.function.Predicate;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.assertEquals;

/**
 * Tests for {@link PredicateSource}.
 *
 * <p>The multiplier is computed deterministically from stored tween state
 * {@code (transitionTick, fromMultiplier, toMultiplier)}: {@code get(tick)} is a pure function,
 * and state only changes when the predicate flips in {@link PredicateSource#serverTick}. The flip
 * sets {@code transitionTick} retroactively to preserve constant tween speed.
 *
 * <p>For a flip to true at {@code transitionTick=T} with tweenTicks=20:
 * {@code get(T) = 0.0} (tween just started), {@code get(T+10) = 5.0} (half), {@code get(T+20) = 10.0}.
 *
 * <p>NeoForge's game test server does not populate the loot-predicate
 * {@code reloadableRegistries()} from the mod's built-in data pack, so tests use
 * {@link PredicateSource#withPredicate} to supply a plain Java predicate
 * instead of a registry-registered loot condition. The registry-lookup path is
 * exercised implicitly by {@code missingPredicateStaysAtZero}.
 */
@GameTestHolder(Constants.MOD_ID)
public class PredicateSourceTests {

    private static final String TEMPLATE = "empty_platform";

    /** Testing-path constructor with explicit else pipeline. */
    private static PredicateSource makeWithElse(Predicate<ServerLevel> pred,
                                                List<ZoneDefinition.CeilingLayer> ifSources,
                                                List<ZoneDefinition.CeilingLayer> elseSources,
                                                long tweenTicks, long evalInterval) {
        return PredicateSource.withPredicate(pred, ifSources, elseSources, tweenTicks, evalInterval);
    }

    private static List<ZoneDefinition.CeilingLayer> pipeline(double value) {
        return List.of(new ZoneDefinition.CeilingLayer(
                AtmosphereModifier.Operation.ADD,
                new ValueSource.Constant(0.0, value, 0L, 0L)));
    }

    // get() is deterministic: before any serverTick the multiplier state is (from=0, to=0), output=0.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void predicateSourceStartsAtZero(GameTestHelper helper) {
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 1L);
        assertEquals(0.0, src.get(0, 0, 0), helper);
        helper.succeed();
    }

    // On first flip (false→true) at tick T, transitionTick=T, so get(T)=0 (tween just started).
    // get is pure: get(T+1) = 1/20 of the way → output 0.5 without another serverTick call.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void predicateSourceFlipsAtFirstEval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 1L);
        src.serverTick(level, 1); // flip: transitionTick=1, from=0, to=1
        assertEquals(0.0, src.get(1, 0, 0), helper);  // at transition tick, m=0
        assertEquals(0.5, src.get(2, 0, 0), helper);  // 1 tick in: m=1/20=0.05, output=0.5
        helper.succeed();
    }

    // Flip at tick 1 → complete at tick 21 (transitionTick + tweenTicks).
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void predicateSourceReachesOneAfterFullTween(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 1L);
        for (long t = 1; t <= 21; t++) src.serverTick(level, t); // flip at 1, complete at 21
        assertEquals(10.0, src.get(21, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void predicateSourceDoesNotExceedOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 1L);
        for (long t = 1; t <= 40; t++) src.serverTick(level, t);
        assertEquals(10.0, src.get(40, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void alwaysFalseStaysAtZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> false, pipeline(10.0), List.of(), 20L, 1L);
        for (long t = 1; t <= 30; t++) src.serverTick(level, t);
        assertEquals(0.0, src.get(30, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void missingPredicateStaysAtZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = new PredicateSource(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "nonexistent"), pipeline(10.0), List.of(), 20L, 1L);
        for (long t = 1; t <= 30; t++) src.serverTick(level, t);
        assertEquals(0.0, src.get(30, 0, 0), helper);
        helper.succeed();
    }

    // Flip at tick 1, transitionTick=1. Midpoint (m=0.5) is at transitionTick+tweenTicks/2 = 1+10 = 11.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void predicateSourceIsAtMidpointHalfway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 1L);
        for (long t = 1; t <= 10; t++) src.serverTick(level, t);
        assertEquals(5.0, src.get(11, 0, 0), helper); // get(11): t=(11-1)/20=0.5, output=5.0
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void instantTweenJumpsImmediately(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 0L, 1L);
        src.serverTick(level, 1);
        assertEquals(10.0, src.get(1, 0, 0), helper);
        helper.succeed();
    }

    // Rain starts at tick 6: flip at 6, transitionTick=6, complete at 26.
    // Rain stops at tick 27: flip at 27 from m=1.0, transitionTick=27, complete at 47.
    // Midpoint of stop-tween at 27+10=37 → m=0.5 → output=5.0.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void weatherPredicateUpdatesDynamically(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(ServerLevel::isRaining, pipeline(10.0), List.of(), 20L, 1L);

        level.setRainLevel(0.0f);
        for (long t = 1; t <= 5; t++) src.serverTick(level, t);
        assertEquals(0.0, src.get(5, 0, 0), helper);

        level.setRainLevel(1.0f);
        for (long t = 6; t <= 26; t++) src.serverTick(level, t); // flip at 6, complete at 26
        assertEquals(10.0, src.get(26, 0, 0), helper);

        level.setRainLevel(0.0f);
        for (long t = 27; t <= 36; t++) src.serverTick(level, t); // flip at 27 from m=1.0, transitionTick=27
        assertEquals(5.0, src.get(37, 0, 0), helper); // midpoint at 27+10=37

        for (long t = 37; t <= 46; t++) src.serverTick(level, t);
        assertEquals(0.0, src.get(47, 0, 0), helper); // complete at 27+20=47

        helper.succeed();
    }

    // evalInterval=5: flip first evaluated at tick 5.
    // At tick 5 (transition tick), m=0. get(6) deterministically gives 1/20-way progress → output=0.5.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void evaluationIntervalThrottlesPredicate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 5L);

        src.serverTick(level, 1);
        assertEquals(0.0, src.get(1, 0, 0), helper); // 1 % 5 != 0, no evaluation yet

        src.serverTick(level, 2);
        src.serverTick(level, 3);
        src.serverTick(level, 4);
        assertEquals(0.0, src.get(4, 0, 0), helper); // still no evaluation

        src.serverTick(level, 5); // flip: transitionTick=5, from=0, to=1
        assertEquals(0.0, src.get(5, 0, 0), helper); // at transition tick, m=0
        assertEquals(0.5, src.get(6, 0, 0), helper); // 1 tick in: m=1/20=0.05, output=0.5

        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void elseSourceDominatesBeforeFirstTick(GameTestHelper helper) {
        var src = makeWithElse(l -> true, pipeline(10.0), pipeline(5.0), 20L, 1L);
        assertEquals(5.0, src.get(0, 0, 0), helper);
        helper.succeed();
    }

    // Flip at tick 1, transitionTick=1. At get(11): t=(11-1)/20=0.5, m=0.5, output=0.5*10+0.5*5=7.5.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void blendIsCorrectMidTween(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), pipeline(5.0), 20L, 1L);
        for (long t = 1; t <= 10; t++) src.serverTick(level, t);
        assertEquals(7.5, src.get(11, 0, 0), helper); // t=(11-1)/20=0.5 → 0.5*10+0.5*5=7.5
        helper.succeed();
    }

    // Before the first serverTick, multiplier=0 so the predicate source contributes elseSources (0).
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void ceilingStartsAtBaseBeforeFirstTick(GameTestHelper helper) {
        var predSrc = PredicateSource.withPredicate(l -> true, pipeline(-10.0), List.of(), 0L, 1L);
        var zone = zone(50.0, predSrc);
        // predSrc multiplier=0 → get()=0 → evalCeiling = 50+0 = 50
        assertEquals("ceiling before tick", 50.0, zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    // Instant flip (tweenTicks=0): after serverTick flips to true, multiplier jumps to 1 immediately.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void ceilingAppliesIfPipelineAfterInstantFlip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var predSrc = PredicateSource.withPredicate(l -> true, pipeline(-10.0), List.of(), 0L, 1L);
        var zone = zone(50.0, predSrc);
        predSrc.serverTick(level, 1); // flip to true, tweenTicks=0 → multiplier=1 immediately
        // predSrc.get(1) = 1*(-10) + 0*0 = -10 → ceiling = 50 + (-10) = 40
        assertEquals("ceiling after flip", 40.0, zone.evalCeiling(1, 0, 0), helper);
        helper.succeed();
    }

    // Tween: flip at tick 1, tweenTicks=20. At tick 11 (halfway): m=0.5 → ceiling = 50 + 0.5*(-20) = 40.
    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void ceilingTweensBetweenBaseAndIfAtMidpoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var predSrc = PredicateSource.withPredicate(l -> true, pipeline(-20.0), List.of(), 20L, 1L);
        var zone = zone(50.0, predSrc);
        predSrc.serverTick(level, 1); // flip: transitionTick=1, from=0, to=1
        // get(11): t=(11-1)/20=0.5, m=0.5 → predSrc.get(11)=0.5*(-20)+0.5*0=-10 → ceiling=50+(-10)=40
        assertEquals("ceiling at tween midpoint", 40.0, zone.evalCeiling(11, 0, 0), helper);
        helper.succeed();
    }

    // Helpers

    private static ZoneDefinition zone(double baseCeiling, PredicateSource predSrc) {
        return new ZoneDefinition(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                List.of(
                        new ZoneDefinition.CeilingLayer(AtmosphereModifier.Operation.ADD,
                                new ValueSource.Constant(0.0, baseCeiling, 0L, 0L)),
                        new ZoneDefinition.CeilingLayer(AtmosphereModifier.Operation.ADD, predSrc)
                ), 60, 600);
    }
}
