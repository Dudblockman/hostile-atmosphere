package org.dudblockman.hostileatmosphere.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.dudblockman.hostileatmosphere.Constants;
import org.dudblockman.hostileatmosphere.engine.AtmosphereEngine;
import org.dudblockman.hostileatmosphere.progression.ZoneDefinition;

import java.util.List;

/**
 * Tests {@link AtmosphereEngine#findZone} — given a sorted zone list and a player eye Y,
 * verifies the correct zone (or null for safe) is returned.
 * All tests are pure; no world interaction needed.
 */
@GameTestHolder(Constants.MOD_ID)
public class ZoneLookupTests {

    // NeoForge prepends "{namespace}:{className}." so this resolves to
    // hostileatmosphere:zonelookuptests.empty_platform
    private static final String TEMPLATE = "empty_platform";

    /** Default three-zone list used across most cases. Sorted ascending by yCeiling. */
    private static final List<ZoneDefinition> ZONES = List.of(
            new ZoneDefinition(32, 60, 600),   // lethal
            new ZoneDefinition(64, 180, 1200), // toxic
            new ZoneDefinition(96, 480, 2400)  // hazy
    );

    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void belowLethalCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 10.0, 0.0);
        assertNotNull("Expected lethal zone", zone, helper);
        assertEquals("lethal yCeiling", 32, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atLethalCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 32.0, 0.0);
        assertNotNull("Expected lethal zone at boundary", zone, helper);
        assertEquals("lethal yCeiling", 32, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void justAboveLethalCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 33.0, 0.0);
        assertNotNull("Expected toxic zone", zone, helper);
        assertEquals("toxic yCeiling", 64, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void midToxicBand(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 50.0, 0.0);
        assertNotNull("Expected toxic zone", zone, helper);
        assertEquals("toxic yCeiling", 64, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atToxicCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 64.0, 0.0);
        assertNotNull("Expected toxic zone at boundary", zone, helper);
        assertEquals("toxic yCeiling", 64, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void midHazyBand(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 80.0, 0.0);
        assertNotNull("Expected hazy zone", zone, helper);
        assertEquals("hazy yCeiling", 96, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void atHazyCeiling(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 96.0, 0.0);
        assertNotNull("Expected hazy zone at boundary", zone, helper);
        assertEquals("hazy yCeiling", 96, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void aboveAllCeilings(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, 200.0, 0.0);
        assertNull("Expected safe zone above all ceilings", zone, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void progressionRaisesCeiling(GameTestHelper helper) {
        // atmosphereLevel = +32; effective lethal ceiling = 32 + 32 = 64; eyeY 50 ≤ 64 → lethal
        var zone = AtmosphereEngine.findZone(ZONES, 50.0, 32.0);
        assertNotNull("Expected lethal zone with raised ceiling", zone, helper);
        assertEquals("lethal yCeiling", 32, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void progressionPushesPlayerToSafe(GameTestHelper helper) {
        // atmosphereLevel = -60; effective hazy ceiling = 96 - 60 = 36; eyeY 90 > 36 → safe
        var zone = AtmosphereEngine.findZone(ZONES, 90.0, -60.0);
        assertNull("Expected safe zone with lowered ceilings", zone, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void emptyZoneListAlwaysSafe(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(List.of(), 10.0, 0.0);
        assertNull("Expected safe with no zones", zone, helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void singleZoneOnly(GameTestHelper helper) {
        var single = List.of(new ZoneDefinition(64, 180, 1200));
        var zone = AtmosphereEngine.findZone(single, 50.0, 0.0);
        assertNotNull("Expected zone hit", zone, helper);
        assertEquals("yCeiling", 64, zone.yCeiling(), helper);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1)
    public static void negativeYInHazard(GameTestHelper helper) {
        var zone = AtmosphereEngine.findZone(ZONES, -30.0, 0.0);
        assertNotNull("Expected lethal zone below 0", zone, helper);
        assertEquals("lethal yCeiling", 32, zone.yCeiling(), helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------

    private static void assertNotNull(String msg, Object obj, GameTestHelper helper) {
        if (obj == null) helper.fail(msg + ": expected non-null but was null");
    }

    private static void assertNull(String msg, Object obj, GameTestHelper helper) {
        if (obj != null) helper.fail(msg + ": expected null but was " + obj);
    }

    private static void assertEquals(String label, int expected, int actual, GameTestHelper helper) {
        if (expected != actual) helper.fail(label + ": expected " + expected + " but was " + actual);
    }
}
