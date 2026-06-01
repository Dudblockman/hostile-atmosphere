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
        assertEquals(0.0, rampMod(64.0, 100L, 0L).getCurrentValue(0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantRampAtEnd(GameTestHelper helper) {
        assertEquals(64.0, rampMod(64.0, 100L, 0L).getCurrentValue(100), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantRampAtMidpoint(GameTestHelper helper) {
        assertEquals(32.0, rampMod(64.0, 100L, 0L).getCurrentValue(50), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantRampPastEnd(GameTestHelper helper) {
        assertEquals(64.0, rampMod(64.0, 100L, 0L).getCurrentValue(200), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void constantInstant(GameTestHelper helper) {
        var m = new AtmosphereModifier(0, Operation.ADD, new ValueSource.Constant(0.0, 64.0, 0L, 0L), "all");
        assertEquals(64.0, m.getCurrentValue(0), helper);
        assertEquals(64.0, m.getCurrentValue(9999), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private static AtmosphereModifier mod(int key, Operation op, double value) {
        return new AtmosphereModifier(key, op, new ValueSource.Constant(0.0, value, 0L, 0L), "all");
    }

    private static AtmosphereModifier rampMod(double value, long tweenTicks, long startTick) {
        return new AtmosphereModifier(0, Operation.ADD,
                new ValueSource.Constant(0.0, value, tweenTicks, startTick), "all");
    }

}
