package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import java.util.List;

import static org.dudblockman.hostileatmosphere.test.GameTestAssertions.*;

/**
 * Tests {@link AtmosphereEngine#findZone} — given a sorted zone list and a player eye Y,
 * verifies the correct zone (or null for safe) is returned.
 * All tests are pure; no world interaction needed.
 * tick=0, x=0, z=0 is used throughout — zones are ofConstant so position doesn't affect the ceiling.
 *
 * Default zone ceilings (matching the shipped data files):
 *   lethal: y ≤ -16   toxic: y ≤ 30   hazy: y ≤ 66
 */
@GameTestHolder(Constants.MOD_ID)
public class ZoneLookupTests {

    private static final String TEMPLATE = "empty_platform";

    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    /** Default three-zone list used across most cases. Sorted ascending by evalCeiling. */
    private static final List<ZoneDefinition> ZONES = List.of(
            ZoneDefinition.ofConstant(OVERWORLD, -16, 60, 600),  // lethal
            ZoneDefinition.ofConstant(OVERWORLD,  30, 180, 1200), // toxic
            ZoneDefinition.ofConstant(OVERWORLD,  66, 480, 2400)  // hazy
    );

    /** Zone IDs parallel to {@link #ZONES} — used for per-zone offset tests. */
    private static final List<String> ZONE_IDS = List.of("lethal", "toxic", "hazy");

    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void belowLethalCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, -20.0, 0.0, 0.0);
        assertNotNull("Expected lethal zone", zone, helper);
        assertEquals("lethal ceiling", -16, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atLethalCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, -16.0, 0.0, 0.0);
        assertNotNull("Expected lethal zone at boundary", zone, helper);
        assertEquals("lethal ceiling", -16, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void justAboveLethalCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, -15.0, 0.0, 0.0);
        assertNotNull("Expected toxic zone", zone, helper);
        assertEquals("toxic ceiling", 30, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void midToxicBand(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, 10.0, 0.0, 0.0);
        assertNotNull("Expected toxic zone", zone, helper);
        assertEquals("toxic ceiling", 30, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atToxicCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, 30.0, 0.0, 0.0);
        assertNotNull("Expected toxic zone at boundary", zone, helper);
        assertEquals("toxic ceiling", 30, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void midHazyBand(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, 50.0, 0.0, 0.0);
        assertNotNull("Expected hazy zone", zone, helper);
        assertEquals("hazy ceiling", 66, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void atHazyCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, 66.0, 0.0, 0.0);
        assertNotNull("Expected hazy zone at boundary", zone, helper);
        assertEquals("hazy ceiling", 66, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void aboveAllCeilings(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, 200.0, 0.0, 0.0);
        assertNull("Expected safe zone above all ceilings", zone, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void progressionRaisesCeiling(GameTestHelper helper) {
        // atmosphereLevel = +32; effective lethal ceiling = -16 + 32 = 16; eyeY 10 ≤ 16 → lethal
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, 10.0, 0.0, 32.0);
        assertNotNull("Expected lethal zone with raised ceiling", zone, helper);
        assertEquals("lethal ceiling", -16, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void progressionPushesPlayerToSafe(GameTestHelper helper) {
        // atmosphereLevel = -60; effective hazy ceiling = 66 - 60 = 6; eyeY 50 > 6 → safe
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, 50.0, 0.0, -60.0);
        assertNull("Expected safe zone with lowered ceilings", zone, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void emptyZoneListAlwaysSafe(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(List.of(), 0L, 0.0, 10.0, 0.0, 0.0);
        assertNull("Expected safe with no zones", zone, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void singleZoneOnly(GameTestHelper helper) {
        var single = List.of(ZoneDefinition.ofConstant(OVERWORLD, 30, 180, 1200));
        var zone = AtmosphereEngine.findZone(single, 0L, 0.0, 20.0, 0.0, 0.0);
        assertNotNull("Expected zone hit", zone, helper);
        assertEquals("ceiling", 30, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void negativeYInHazard(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 0L, 0.0, -30.0, 0.0, 0.0);
        assertNotNull("Expected lethal zone below 0", zone, helper);
        assertEquals("lethal ceiling", -16, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // Per-zone offset overload: AtmosphereEngine.findZone(zones, zoneIds, tick, x, eyeY, z, levelForZone)
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perZoneOnlyLethalRaised(GameTestHelper helper) {
        // lethal ceiling raised by 32 → effective -16 + 32 = 16; eyeY 10 ≤ 16 → lethal
        var zone = AtmosphereEngine.findZone(ZONES, ZONE_IDS, 0L, 0.0, 10.0, 0.0,
                (id, base) -> "lethal".equals(id) ? base + 32.0 : base);
        assertNotNull("Expected lethal zone with raised ceiling", zone, helper);
        assertEquals("lethal ceiling", -16, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perZoneLethalSuppressedExposesToxic(GameTestHelper helper) {
        // eyeY -20 is normally in lethal (≤ -16); lethal offset -30 → effective -46;
        // eyeY -20 > -46 so lethal is skipped → hits toxic (-20 ≤ 30)
        var zone = AtmosphereEngine.findZone(ZONES, ZONE_IDS, 0L, 0.0, -20.0, 0.0,
                (id, base) -> "lethal".equals(id) ? base - 30.0 : base);
        assertNotNull("Expected toxic zone when lethal suppressed", zone, helper);
        assertEquals("toxic ceiling", 30, (int) zone.evalCeiling(0, 0, 0), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = Constants.MOD_ID, timeoutTicks = 1)
    public static void perZoneAllSuppressedYieldsSafe(GameTestHelper helper) {
        // all ceilings pushed down by 200; eyeY 10 is above all effective ceilings → safe
        var zone = AtmosphereEngine.findZone(ZONES, ZONE_IDS, 0L, 0.0, 10.0, 0.0, (id, base) -> base - 200.0);
        assertNull("Expected safe zone when all ceilings suppressed", zone, helper);
        helper.succeed();
    }

}
