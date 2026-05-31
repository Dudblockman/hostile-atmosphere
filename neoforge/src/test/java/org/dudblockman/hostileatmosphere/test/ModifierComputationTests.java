package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier.Operation;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;

import java.util.List;

@GameTestHolder(Constants.MOD_ID)
public class ModifierComputationTests {

    // NeoForge prepends "{namespace}:{className}." so this resolves to
    // hostileatmosphere:modifiercomputationtests.empty_platform
    private static final String TEMPLATE = "empty_platform";
    private static final double DELTA = 0.001;

    // ------------------------------------------------------------------------------------------
    // Level computation
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void emptyModifiersIsZero(GameTestHelper helper) {
        double level = AtmosphereProgressionData.computeLevel(List.of(), 0);
        assertEquals(0.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void singleAddModifier(GameTestHelper helper) {
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, 64.0)), 0);
        assertEquals(64.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void multipleAddModifiersSum(GameTestHelper helper) {
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, 40.0), mod("b", Operation.ADD, 24.0)), 0);
        assertEquals(64.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void negativeAddModifier(GameTestHelper helper) {
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, -20.0)), 0);
        assertEquals(-20.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void clampMaxCaps(GameTestHelper helper) {
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, 100.0), mod("b", Operation.CLAMP_MAX, 60.0)), 0);
        assertEquals(60.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void clampMinFloors(GameTestHelper helper) {
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, -20.0), mod("b", Operation.CLAMP_MIN, 0.0)), 0);
        assertEquals(0.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void clampMinAndMaxBothApply(GameTestHelper helper) {
        // ADD 0 → sum=0; CLAMP_MAX 50 → still 0; CLAMP_MIN 10 → 10
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, 0.0),
                        mod("b", Operation.CLAMP_MIN, 10.0),
                        mod("c", Operation.CLAMP_MAX, 50.0)), 0);
        assertEquals(10.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void clampMaxBelowClampMin(GameTestHelper helper) {
        // ADD 0 → 0; CLAMP_MAX 20 → min(0,20)=0; CLAMP_MIN 30 → max(0,30)=30. MIN wins.
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, 0.0),
                        mod("b", Operation.CLAMP_MIN, 30.0),
                        mod("c", Operation.CLAMP_MAX, 20.0)), 0);
        assertEquals(30.0, level, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void addBeforeClamps(GameTestHelper helper) {
        // ADD 80 → 80; CLAMP_MAX 60 → 60; CLAMP_MIN 70 → 70. MIN is final guard.
        double level = AtmosphereProgressionData.computeLevel(
                List.of(mod("a", Operation.ADD, 80.0),
                        mod("b", Operation.CLAMP_MAX, 60.0),
                        mod("c", Operation.CLAMP_MIN, 70.0)), 0);
        assertEquals(70.0, level, helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Tween interpolation (AtmosphereModifier.getCurrentValue)
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void tweenAtStart(GameTestHelper helper) {
        var mod = tween("a", 0.0, 64.0, 0L, 100L);
        assertEquals(0.0, mod.getCurrentValue(0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void tweenAtEnd(GameTestHelper helper) {
        var mod = tween("a", 0.0, 64.0, 0L, 100L);
        assertEquals(64.0, mod.getCurrentValue(100), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void tweenAtMidpoint(GameTestHelper helper) {
        var mod = tween("a", 0.0, 64.0, 0L, 100L);
        assertEquals(32.0, mod.getCurrentValue(50), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void tweenPastEnd(GameTestHelper helper) {
        var mod = tween("a", 0.0, 64.0, 0L, 100L);
        assertEquals(64.0, mod.getCurrentValue(200), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void instantTween(GameTestHelper helper) {
        var mod = tween("a", 0.0, 64.0, 0L, 0L); // durationTicks = 0
        assertEquals(64.0, mod.getCurrentValue(0), helper);
        assertEquals(64.0, mod.getCurrentValue(9999), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void tweenInterruptSnapshotsCurrent(GameTestHelper helper) {
        // Tween 0→64 over 100 ticks; at tick 50 the value is 32.
        var original = tween("a", 0.0, 64.0, 0L, 100L);
        assertEquals(32.0, original.getCurrentValue(50), helper);

        // Interrupt at tick 50 with a new tween targeting 32.
        // The new tween must start from current value (32), not from original fromValue (0).
        var interrupted = original.withNewTween(32.0, 100, 50);
        assertEquals(32.0, interrupted.fromValue(), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private static AtmosphereModifier mod(String name, Operation op, double value) {
        return new AtmosphereModifier(rl(name), op, value, value, 0L, 0L);
    }

    private static AtmosphereModifier tween(String name, double from, double to,
                                             long startTick, long durationTicks) {
        return new AtmosphereModifier(rl(name), Operation.ADD, from, to, startTick, durationTicks);
    }

    private static ResourceLocation rl(String name) {
        return ResourceLocation.fromNamespaceAndPath("test", name);
    }

    private static void assertEquals(double expected, double actual, GameTestHelper helper) {
        if (Math.abs(expected - actual) > DELTA) {
            helper.fail(String.format("Expected %.4f but was %.4f", expected, actual));
        }
    }
}
