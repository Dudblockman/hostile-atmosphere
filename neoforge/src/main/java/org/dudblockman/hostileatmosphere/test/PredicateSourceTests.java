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
import java.util.concurrent.atomic.AtomicBoolean;
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

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void tweenHoldsIfPipelineValueAfterCompletion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 1L);
        for (long t = 1; t <= 40; t++) src.serverTick(level, t);
        assertEquals("at tween end", 10.0, src.get(21, 0, 0), helper);
        assertEquals("after tween end", 10.0, src.get(40, 0, 0), helper);
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

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void instantTweenJumpsImmediately(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 0L, 1L);
        src.serverTick(level, 1);
        assertEquals(10.0, src.get(1, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void falseFlipTweensBackToElsePipeline(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var isActive = new AtomicBoolean(true);
        var src = makeWithElse(l -> isActive.get(), pipeline(10.0), pipeline(5.0), 20L, 1L);

        src.serverTick(level, 1);
        assertEquals("before false flip", 10.0, src.get(21, 0, 0), helper);

        isActive.set(false);
        src.serverTick(level, 27);
        assertEquals("false flip midpoint", 7.5, src.get(37, 0, 0), helper);
        assertEquals("false flip complete", 5.0, src.get(47, 0, 0), helper);

        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void evaluationIntervalThrottlesPredicate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), List.of(), 20L, 5L);

        src.serverTick(level, 1);
        assertEquals(0.0, src.get(1, 0, 0), helper);

        src.serverTick(level, 2);
        src.serverTick(level, 3);
        src.serverTick(level, 4);
        assertEquals(0.0, src.get(4, 0, 0), helper);

        src.serverTick(level, 5);
        assertEquals(0.0, src.get(5, 0, 0), helper);
        assertEquals(0.5, src.get(6, 0, 0), helper);

        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void elsePipelineAppliesBeforeFirstEvaluation(GameTestHelper helper) {
        var src = makeWithElse(l -> true, pipeline(10.0), pipeline(5.0), 20L, 1L);
        assertEquals("before first evaluation", 5.0, src.get(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void midTweenBlendsIfAndElsePipelines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var src = makeWithElse(l -> true, pipeline(10.0), pipeline(5.0), 20L, 1L);
        for (long t = 1; t <= 10; t++) src.serverTick(level, t);
        assertEquals("mid-tween blend", 7.5, src.get(11, 0, 0), helper);
        helper.succeed();
    }
}
