package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier.Operation;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ValueSource;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.assertEquals;

/**
 * Tests the runtime modifier pipeline stored in {@link AtmosphereProgressionData}:
 * adding, overwriting, removing, and scoped clearing of modifiers, and verifying
 * that {@link AtmosphereProgressionData#getLevelForZone} reflects each change.
 *
 * Each test calls {@link #freshData} to clear the shared server-level data before
 * exercising it, keeping tests independent.
 *
 * tick=0, x=0, z=0 throughout — constant sources are position- and time-independent.
 */
@GameTestHolder(Constants.MOD_ID)
public class ModifierProgressionTests {

    private static final String TEMPLATE = "empty_platform";

    /** Returns a fresh, isolated AtmosphereProgressionData instance for test isolation. */
    private static AtmosphereProgressionData freshData() {
        return new AtmosphereProgressionData();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void addModifierChangesLevel(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(30.0), "all");
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void addTwoModifiersSum(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(20.0), "all");
        data.setModifier(2, Operation.ADD, constant(10.0), "all");
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void overwriteSameKeyUsesLatest(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(10.0), "all");
        data.setModifier(1, Operation.ADD, constant(50.0), "all");
        assertEquals(50.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void overwriteCanChangeOperation(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        // Start: ADD 80 → level 80. Then replace with CLAMP_MAX 80 + add modifier at key 0.
        data.setModifier(0, Operation.ADD, constant(80.0), "all");
        data.setModifier(1, Operation.CLAMP_MAX, constant(40.0), "all");
        assertEquals(40.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        // Overwrite key 1 with a floor instead — max(80, 60) = 80
        data.setModifier(1, Operation.CLAMP_MIN, constant(60.0), "all");
        assertEquals(80.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void removeModifierReverts(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(30.0), "all");
        data.removeModifier(1);
        assertEquals(0.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void removeNonexistentKeyIsNoop(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(10.0), "all");
        data.removeModifier(999);
        assertEquals(10.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clearForTargetRemovesOnlyThatScope(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(20.0), "all");
        data.setModifier(2, Operation.ADD, constant(15.0), "lethal");
        int removed = data.clearModifiersForTarget("lethal");
        assertEquals("removed count", 1, removed, helper);
        // "all"-scoped modifier still in place
        assertEquals(20.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        // "lethal" zone now only sees the remaining "all" modifier
        assertEquals(20.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clearForTargetWithNoMatchIsNoop(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(25.0), "all");
        int removed = data.clearModifiersForTarget("lethal");
        assertEquals("removed count", 0, removed, helper);
        assertEquals(25.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void allScopeAffectsEveryZone(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(30.0), "all");
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "toxic"), helper);
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "hazy"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void zoneSpecificScopeDoesNotLeakToOtherZones(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(50.0), "lethal");
        assertEquals(50.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        assertEquals(0.0,  data.getLevelForZone(0, 0, 0, "toxic"),  helper);
        assertEquals(0.0,  data.getLevelForZone(0, 0, 0, "hazy"),   helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void noRuntimeModifiersRetainsDatapackBase(GameTestHelper helper) {
        // No runtime modifiers: getLevelForZone with baseCeiling=0 returns 0 unchanged.
        // In the real engine, the zone stays active at its datapack-defined ceiling (baseCeiling).
        AtmosphereProgressionData data = freshData();
        assertEquals(0.0, data.getLevelForZone(0, 0, 0, "all"),    helper);
        assertEquals(0.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void zoneModifierStacksOnTopOfAll(GameTestHelper helper) {
        AtmosphereProgressionData data = freshData();
        data.setModifier(1, Operation.ADD, constant(20.0), "all");
        data.setModifier(2, Operation.ADD, constant(15.0), "lethal");
        // lethal zone: all(20) + lethal(15) = 35
        assertEquals(35.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        // toxic zone: all(20) only = 20
        assertEquals(20.0, data.getLevelForZone(0, 0, 0, "toxic"),  helper);
        helper.succeed();
    }

    private static ValueSource constant(double v) {
        return new ValueSource.Constant(0.0, v, 0L, 0L);
    }

}
