package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.progression.AtmosphereModifier.Operation;
import org.dudblockman.hostileatmosphere.progression.AtmosphereProgressionData;
import org.dudblockman.hostileatmosphere.progression.ValueSource;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.TEMPLATE;
import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.assertEquals;

/**
 * Tests the runtime modifier pipeline stored in {@link AtmosphereProgressionData}:
 * adding, overwriting, removing, and scoped clearing of modifiers, and verifying
 * that {@link AtmosphereProgressionData#getLevelForZone} reflects each change.
 *
 * tick=0, x=0, z=0 throughout — constant sources are position- and time-independent.
 */
@GameTestHolder(Constants.MOD_ID)
public class ModifierProgressionTests {

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void reusedKeyReplacesPreviousModifier(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(10.0), "all");
        data.setModifier(1, Operation.ADD, constant(50.0), "all");
        assertEquals("after overwrite", 50.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void removedModifierNoLongerApplies(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(30.0), "all");
        data.removeModifier(1);
        assertEquals("after remove", 0.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void removeNonexistentKeyIsNoop(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(10.0), "all");
        data.removeModifier(999);
        assertEquals(10.0, data.getLevelForZone(0, 0, 0, "all"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clearForTargetRemovesOnlyThatTargetsModifiers(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(20.0), "all");
        data.setModifier(2, Operation.ADD, constant(15.0), "lethal");
        data.clearModifiersForTarget("lethal");
        assertEquals("lethal after clear", 20.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void clearForTargetReturnsRemovedCount(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(20.0), "all");
        data.setModifier(2, Operation.ADD, constant(15.0), "lethal");
        data.setModifier(3, Operation.ADD, constant(5.0), "lethal");
        assertEquals("removed count", 2, data.clearModifiersForTarget("lethal"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void allScopeAffectsEveryZone(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(30.0), "all");
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "toxic"), helper);
        assertEquals(30.0, data.getLevelForZone(0, 0, 0, "hazy"), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void zoneSpecificScopeDoesNotLeakToOtherZones(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(50.0), "lethal");
        assertEquals(50.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        assertEquals(0.0,  data.getLevelForZone(0, 0, 0, "toxic"),  helper);
        assertEquals(0.0,  data.getLevelForZone(0, 0, 0, "hazy"),   helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void noModifiersLeavesBaseCeilingUnchanged(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        assertEquals("effective ceiling", 64.0, data.getEffectiveCeiling(0, 0, 0, "lethal", 64.0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void zoneModifierStacksOnTopOfAll(GameTestHelper helper) {
        AtmosphereProgressionData data = new AtmosphereProgressionData();
        data.setModifier(1, Operation.ADD, constant(20.0), "all");
        data.setModifier(2, Operation.ADD, constant(15.0), "lethal");
        assertEquals("lethal (all + lethal)", 35.0, data.getLevelForZone(0, 0, 0, "lethal"), helper);
        helper.succeed();
    }

    private static ValueSource constant(double v) {
        return new ValueSource.Constant(0.0, v, 0L, 0L);
    }

}
