package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.engine.MiasmaDamageTypes;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier.Operation;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ValueSource;

import java.util.List;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.TEMPLATE;
import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.assertEquals;
import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.assertTrue;

@GameTestHolder(Constants.MOD_ID)
public class ModifierComputationTests {

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void addModifiersSumTheirOffsets(GameTestHelper helper) {
        assertEquals("summed offset", 24.0, computeLevel(
                List.of(mod(0, Operation.ADD, 40.0), mod(1, Operation.ADD, -16.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void capLimitsCeilingWithoutRaisingIt(GameTestHelper helper) {
        assertEquals("above cap", 60.0, computeLevel(
                List.of(mod(0, Operation.ADD, 100.0), mod(1, Operation.CLAMP_MAX, 60.0)), 0), helper);
        assertEquals("below cap", 40.0, computeLevel(
                List.of(mod(0, Operation.ADD, 40.0), mod(1, Operation.CLAMP_MAX, 60.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void floorRaisesCeilingWithoutLoweringIt(GameTestHelper helper) {
        assertEquals("below floor", 0.0, computeLevel(
                List.of(mod(0, Operation.ADD, -20.0), mod(1, Operation.CLAMP_MIN, 0.0)), 0), helper);
        assertEquals("above floor", 30.0, computeLevel(
                List.of(mod(0, Operation.ADD, 30.0), mod(1, Operation.CLAMP_MIN, 0.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void modifiersApplyInAscendingKeyOrder(GameTestHelper helper) {
        assertEquals("key order", 20.0, computeLevel(
                List.of(mod(2, Operation.CLAMP_MAX, 20.0), mod(1, Operation.CLAMP_MIN, 30.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantHoldsTargetAfterTweenEnds(GameTestHelper helper) {
        var source = new ValueSource.Constant(0.0, 64.0, 100L, 100L);
        assertEquals("tween end", 64.0, source.get(200), helper);
        assertEquals("after tween", 64.0, source.get(400), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantTweensLinearlyFromItsStartTick(GameTestHelper helper) {
        var source = new ValueSource.Constant(0.0, 64.0, 100L, 100L);
        assertEquals("at start tick", 0.0, source.get(100), helper);
        assertEquals("midpoint", 32.0, source.get(150), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void settledConstantHoldsTargetAtEveryTick(GameTestHelper helper) {
        var settled = new ValueSource.Constant(0.0, 64.0, 100L, 0L).settle(100);
        assertEquals("settled value at tick 0", 64.0, settled.get(0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantKeepsTweeningWhenSettledEarly(GameTestHelper helper) {
        var result = new ValueSource.Constant(0.0, 64.0, 100L, 0L).settle(50);
        assertEquals("mid-tween after early settle", 32.0, result.get(50), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void sinWaveSwingsByAmplitudeOverPeriod(GameTestHelper helper) {
        var src = new ValueSource.SinWave(0.0, 10.0, 0.0, 20.0, 0.0, 0.0, 0L, 0L);
        assertEquals("quarter period", 10.0, src.get(5), helper);
        assertEquals("three-quarter period", -10.0, src.get(15), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void sinWavePhaseDelaysWave(GameTestHelper helper) {
        var src = new ValueSource.SinWave(0.0, 10.0, 0.0, 20.0, 0.0, 5.0, 0L, 0L);
        assertEquals("at phase offset", 0.0, src.get(5), helper);
        assertEquals("quarter period after phase offset", 10.0, src.get(10), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perlinOutputIsFixedForSeed(GameTestHelper helper) {
        var src = new ValueSource.Perlin(0.01, 0.0, 5.0, 100.0, 0L, 0L, 42L);
        assertEquals("seed 42 sample", -0.6193, src.get(37L, 123.4, 56.7), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perlinOutputScalesWithAmplitude(GameTestHelper helper) {
        double base = new ValueSource.Perlin(0.01, 0.0, 5.0, 100.0, 0L, 0L, 42L).get(37L, 123.4, 56.7);
        double doubled = new ValueSource.Perlin(0.01, 0.0, 10.0, 100.0, 0L, 0L, 42L).get(37L, 123.4, 56.7);
        assertTrue("sample nonzero", Math.abs(base) > 0.01, helper);
        assertEquals("double amplitude", 2 * base, doubled, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perlinDifferentSeedsProduceDifferentOutput(GameTestHelper helper) {
        var s1 = new ValueSource.Perlin(0.01, 0.0, 5.0, 100.0, 0L, 0L, 42L);
        var s2 = new ValueSource.Perlin(0.01, 0.0, 5.0, 100.0, 0L, 0L, 99L);
        boolean anyDifferent = false;
        for (int i = 1; i <= 10; i++) {
            if (Math.abs(s1.get(i * 7L, i * 3.7, i * 5.1) - s2.get(i * 7L, i * 3.7, i * 5.1)) > 0.001) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue("Seeds 42 and 99 produced identical output at all 10 sample positions", anyDifferent, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void damageIntervalConvertsSecondsAtTwentyTicksPerSecond(GameTestHelper helper) {
        assertIntervalTicks(4.0f, 80, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void intervalTicksZeroClamped(GameTestHelper helper) {
        assertIntervalTicks(0.0f, 1, helper);
        helper.succeed();
    }

    private static void assertIntervalTicks(float secs, int expectedTicks, GameTestHelper helper) {
        assertEquals("toIntervalTicks(" + secs + "s)", expectedTicks, MiasmaDamageTypes.toIntervalTicks(secs), helper);
    }

    private static AtmosphereModifier mod(int key, Operation op, double value) {
        return new AtmosphereModifier(key, op, new ValueSource.Constant(0.0, value, 0L, 0L), "all");
    }

    private static double computeLevel(List<AtmosphereModifier> mods, long tick) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        for (AtmosphereModifier m : mods) data.setModifier(m.key(), m.operation(), m.source(), m.target());
        return data.getLevelForZone(tick, 0, 0, "all");
    }

}
