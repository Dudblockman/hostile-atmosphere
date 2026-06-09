package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier.Operation;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ValueSource;

import java.util.List;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.assertEquals;
import org.dudblockman.hostileatmosphere.engine.MiasmaDamageTypes;

@GameTestHolder(Constants.MOD_ID)
public class ModifierComputationTests {

    private static final String TEMPLATE = "empty_platform";

    // ------------------------------------------------------------------------------------------
    // Pipeline level computation
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void emptyModifiersIsZero(GameTestHelper helper) {
        assertEquals(0.0, AtmosphereProgressionData.computeLevel(List.of(), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void singleAddModifier(GameTestHelper helper) {
        assertEquals(64.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, 64.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void multipleAddModifiersSum(GameTestHelper helper) {
        assertEquals(64.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, 40.0), mod(1, Operation.ADD, 24.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void negativeAddModifier(GameTestHelper helper) {
        assertEquals(-20.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, -20.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clampMaxCaps(GameTestHelper helper) {
        // ADD 100 → 100, CLAMP_MAX 60 → min(100,60) = 60
        assertEquals(60.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, 100.0), mod(1, Operation.CLAMP_MAX, 60.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clampMinFloors(GameTestHelper helper) {
        // ADD -20 → -20, CLAMP_MIN 0 → max(-20,0) = 0
        assertEquals(0.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, -20.0), mod(1, Operation.CLAMP_MIN, 0.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clampMinThenMax(GameTestHelper helper) {
        // ADD 0 → 0; CLAMP_MIN 10 → 10; CLAMP_MAX 50 → min(10,50) = 10
        assertEquals(10.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, 0.0),
                        mod(1, Operation.CLAMP_MIN, 10.0),
                        mod(2, Operation.CLAMP_MAX, 50.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clampOrderMatters(GameTestHelper helper) {
        // ADD 0 → 0; CLAMP_MIN 30 → 30; CLAMP_MAX 20 → min(30,20) = 20. Last wins.
        assertEquals(20.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, 0.0),
                        mod(1, Operation.CLAMP_MIN, 30.0),
                        mod(2, Operation.CLAMP_MAX, 20.0)), 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void addBeforeClamps(GameTestHelper helper) {
        // ADD 80 → 80; CLAMP_MAX 60 → 60; CLAMP_MIN 70 → max(60,70) = 70
        assertEquals(70.0, AtmosphereProgressionData.computeLevel(
                List.of(mod(0, Operation.ADD, 80.0),
                        mod(1, Operation.CLAMP_MAX, 60.0),
                        mod(2, Operation.CLAMP_MIN, 70.0)), 0), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Constant ramp-in (tweenTicks > 0 scales from 0 to value)
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantRampAtStart(GameTestHelper helper) {
        assertEquals(0.0, rampMod(64.0, 100L, 0L).source().get(0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantRampAtEnd(GameTestHelper helper) {
        assertEquals(64.0, rampMod(64.0, 100L, 0L).source().get(100), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantRampAtMidpoint(GameTestHelper helper) {
        assertEquals(32.0, rampMod(64.0, 100L, 0L).source().get(50), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantRampPastEnd(GameTestHelper helper) {
        assertEquals(64.0, rampMod(64.0, 100L, 0L).source().get(200), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantInstant(GameTestHelper helper) {
        var m = new AtmosphereModifier(0, Operation.ADD, new ValueSource.Constant(0.0, 64.0, 0L, 0L), "all");
        assertEquals(64.0, m.source().get(0), helper);
        assertEquals(64.0, m.source().get(9999), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Constant.settle()
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantSettlesAfterTweenComplete(GameTestHelper helper) {
        var m = rampMod(64.0, 100L, 0L);
        var settled = m.source().settle(100);
        assertEquals("settled tweenTicks", 0, (int) ((ValueSource.Constant) settled).tweenTicks(), helper);
        assertEquals("settled value", 64.0, settled.get(0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantDoesNotSettleBeforeTweenComplete(GameTestHelper helper) {
        var m = rampMod(64.0, 100L, 0L);
        var notYet = m.source().settle(50);
        // settle() returns this before tween completes — still has tweenTicks=100
        assertEquals("unsettled tweenTicks", 100, (int) ((ValueSource.Constant) notYet).tweenTicks(), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // SinWave value source
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void sinWaveAtZero(GameTestHelper helper) {
        // amplitude=10, period=20, phase=0 → get(0) = 10*sin(0) = 0
        var src = new ValueSource.SinWave(0.0, 10.0, 0.0, 20.0, 0.0, 0.0, 0L, 0L);
        assertEquals(0.0, src.get(0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void sinWaveAtQuarterPeriod(GameTestHelper helper) {
        // get(5) = 10*sin(2π*5/20) = 10*sin(π/2) = 10.0
        var src = new ValueSource.SinWave(0.0, 10.0, 0.0, 20.0, 0.0, 0.0, 0L, 0L);
        assertEquals(10.0, src.get(5), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void sinWaveAtHalfPeriod(GameTestHelper helper) {
        // get(10) = 10*sin(π) ≈ 0 (within floating-point tolerance)
        var src = new ValueSource.SinWave(0.0, 10.0, 0.0, 20.0, 0.0, 0.0, 0L, 0L);
        assertEquals(0.0, src.get(10), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void sinWaveAtThreeQuarterPeriod(GameTestHelper helper) {
        // get(15) = 10*sin(3π/2) = -10.0
        var src = new ValueSource.SinWave(0.0, 10.0, 0.0, 20.0, 0.0, 0.0, 0L, 0L);
        assertEquals(-10.0, src.get(15), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void sinWavePhaseShiftsMidpoint(GameTestHelper helper) {
        // phase=5: zero crosses at tick=5; peak at tick=10 (quarter period after zero)
        // get(5) = 10*sin(2π*(5-5)/20) = 0; get(10) = 10*sin(2π*5/20) = 10
        var src = new ValueSource.SinWave(0.0, 10.0, 0.0, 20.0, 0.0, 5.0, 0L, 0L);
        assertEquals("at phase offset", 0.0, src.get(5), helper);
        assertEquals("quarter period after phase offset", 10.0, src.get(10), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Perlin noise value source
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perlinSameSeedSameOutput(GameTestHelper helper) {
        // Two independent instances with same seed must produce identical output.
        var a = new ValueSource.Perlin(0.01, 0.0, 5.0, 100.0, 0L, 0L, 42L);
        var b = new ValueSource.Perlin(0.01, 0.0, 5.0, 100.0, 0L, 0L, 42L);
        assertEquals(a.get(37L, 123.4, 56.7), b.get(37L, 123.4, 56.7), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perlinOutputWithinAmplitudeBounds(GameTestHelper helper) {
        var src = new ValueSource.Perlin(0.01, 0.0, 5.0, 100.0, 0L, 0L, 42L);
        for (int i = 0; i < 20; i++) {
            double v = src.get(i * 13L, i * 7.3, i * 11.9);
            if (v < -5.001 || v > 5.001) {
                String msg = "Perlin output " + v + " outside amplitude [-5,5] at i=" + i;
                helper.fail(msg);
                throw new AssertionError(msg);
            }
        }
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
        if (!anyDifferent) {
            String msg = "Seeds 42 and 99 produced identical output at all 10 sample positions";
            helper.fail(msg);
            throw new AssertionError(msg);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Interval ticks (MiasmaDamageTypes.toIntervalTicks)
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void intervalTicksDefault(GameTestHelper helper) {
        assertIntervalTicks(4.0f, 80, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void intervalTicksHalfSecond(GameTestHelper helper) {
        assertIntervalTicks(0.5f, 10, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void intervalTicksOneTick(GameTestHelper helper) {
        assertIntervalTicks(0.05f, 1, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void intervalTicksZeroClamped(GameTestHelper helper) {
        assertIntervalTicks(0.0f, 1, helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private static void assertIntervalTicks(float secs, int expectedTicks, GameTestHelper helper) {
        assertEquals("toIntervalTicks(" + secs + "s)", expectedTicks, MiasmaDamageTypes.toIntervalTicks(secs), helper);
    }

    private static AtmosphereModifier mod(int key, Operation op, double value) {
        return new AtmosphereModifier(key, op, new ValueSource.Constant(0.0, value, 0L, 0L), "all");
    }

    private static AtmosphereModifier rampMod(double value, long tweenTicks, long startTick) {
        return new AtmosphereModifier(0, Operation.ADD,
                new ValueSource.Constant(0.0, value, tweenTicks, startTick), "all");
    }

}
